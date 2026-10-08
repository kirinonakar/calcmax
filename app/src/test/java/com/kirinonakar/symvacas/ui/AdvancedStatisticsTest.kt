package com.kirinonakar.symvacas.ui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.json.JSONArray
import com.kirinonakar.symvacas.math.Parser
import com.kirinonakar.symvacas.math.requiresExplicitEvaluation

class AdvancedStatisticsTest {
    @Test fun structuredReportsRouteToTheirAnalysisMenuIncludingSavedLegacyReports() {
        val report=JSONObject().put("analysis","gee").put("title","GEE")
        val result=JSONObject().put("statisticsReport",report)
        assertSame(report,statisticsReportFor(result,"gee([[1,2,3]])",setOf("gee")))
        assertSame(report,statisticsReportFor(result,"",setOf("gee")))
        assertSame(report,statisticsReportFor(result,"previousCalculation([1])",setOf("gee")))
        assertNull(statisticsReportFor(result,"gee([[1,2,3]])",setOf("stats","ttest")))
        report.remove("analysis")
        assertSame(report,statisticsReportFor(result,"stats([1,2,3])",setOf("stats")))
        assertNull(statisticsReportFor(JSONObject().put("exact","2"),"1+1",setOf("stats")))
    }
    @Test fun bayesianFormsRejectMissingValuesAndOverlappingCountRoles() {
        val definitions=JSONArray(File("src/main/assets/advanced_statistics.json").readText())
        fun definition(id:String)=(0 until definitions.length()).map {definitions.getJSONObject(it)}.first {it.getString("id")==id}
        val rows=listOf(listOf("7","10"),listOf("2","5"))
        for(id in listOf("bayesproportion","bayesmean","bayesrate")) {
            assertTrue(requiresExplicitEvaluation(Parser("$id([1])").parse()))
            assertTrue(runCatching {guidedStatisticsCommand(definition(id),rows,JSONObject().put("alpha",""))}.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching {guidedStatisticsCommand(definition(id),listOf(listOf("1"),listOf("")))}.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(runCatching {guidedStatisticsCommand(definition("bayesproportion"),rows,JSONObject().put("layout","counts").put("trials","0"))}.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {guidedStatisticsCommand(definition("bayesrate"),rows,JSONObject().put("layout","exposure").put("exposure","0"))}.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun termLabelsFollowSelectedPredictorsAndInteractions() {
        run { // termLabelsFollowSelectedPredictorsAndInteractions
            val definitions=JSONArray(File("src/main/assets/advanced_statistics.json").readText())
            fun definition(id:String)=(0 until definitions.length()).map {definitions.getJSONObject(it)}.first {it.getString("id")==id}
            val rows=listOf(listOf("1","2","3","4","5"));val labels=listOf("id (x)","time (y)","treatment (z)","entry (x4)","outcome (x5)")
            assertEquals(mapOf("x1" to "treatment (z)","x2" to "time (y)","x1:x2" to "treatment (z):time (y)","x1^2" to "treatment (z)^2"),advancedStatisticsTermLabels(definition("gee"),rows,JSONObject().put("subject","0").put("response","4").put("predictors","2,1").put("interactions","z,y;z,z"),labels))
            assertEquals(mapOf("x1" to "time (y)","x2" to "treatment (z)","x3" to "entry (x4)"),advancedStatisticsTermLabels(definition("mixedmodel"),rows,JSONObject().put("subject","0").put("response","4"),labels))
            assertEquals(mapOf("x1" to "id (x)","x2" to "treatment (z)"),advancedStatisticsTermLabels(definition("cox"),rows,JSONObject().put("time","1").put("event","4").put("truncation","entry").put("entry","3"),labels))
            assertEquals(emptyMap<String,String>(),advancedStatisticsTermLabels(definition("gee"),rows,JSONObject(),emptyList()))
        }
        run { // geeInteractionsAcceptLettersNamesAndShownLabels
            val definitions=JSONArray(File("src/main/assets/advanced_statistics.json").readText())
            val definition=(0 until definitions.length()).map {definitions.getJSONObject(it)}.first {it.getString("id")=="gee"}
            val labels=listOf("id (x)","time (y)","treatment (z)","age (x4)","sex (x5)","y (x6)")
            val rows=listOf(listOf("1","0","0","47","0","1"),listOf("1","1","0","47","0","1"),listOf("2","0","1","60","1","0"),listOf("2","1","1","60","1","0"))
            val base=JSONObject().put("subject","0").put("response","5").put("predictors","1,2,3,4").put("family","binomial")
            val expected="gee([[1,0,0,47,0,1],[1,1,0,47,0,1],[2,0,1,60,1,0],[2,1,1,60,1,0]],binomial,independence,[[1,2]])"
            for(interactions in listOf("y,z","time, treatment","time (y), treatment (z)"))
                assertEquals(interactions,expected,guidedStatisticsCommand(definition,rows,JSONObject(base.toString()).put("interactions",interactions),labels))
            val shifted="gee([[1,0,0,47,0,1],[1,1,0,47,0,1],[2,0,1,60,1,0],[2,1,1,60,1,0]],binomial,independence,[[3,4]])"
            for(interactions in listOf("x4,x5"))
                assertEquals(interactions,shifted,guidedStatisticsCommand(definition,rows,JSONObject(base.toString()).put("interactions",interactions),labels))
            val subset="gee([[1,47,0,1],[1,47,0,1],[2,60,1,0],[2,60,1,0]],binomial,independence,[[1,2]])"
            assertEquals("x4,x5",subset,guidedStatisticsCommand(definition,rows,JSONObject(base.toString()).put("predictors","3,4").put("interactions","x4,x5"),labels))
            for(interactions in listOf("1,2","id (x),time (y)"))
                assertTrue(interactions,runCatching {guidedStatisticsCommand(definition,rows,JSONObject(base.toString()).put("interactions",interactions),labels)}.exceptionOrNull() is IllegalArgumentException)
        }
    }
    private fun definition(id:String,input:String,suffix:String="")=JSONObject().put("id",id).put("input",input).put("suffix",suffix)

    @Test(expected=IllegalArgumentException::class) fun rejectsIncompleteModelRows() {
        advancedStatisticsCommand(definition("cox","table"),listOf(listOf("1","","0"),listOf("2","1","1")))
    }
    @Test fun analysisRowsPreserveMissingCellsHeadersAndSelectedColumns() {
        run { // preservesMissingFirstRowAndIgnoresHeadersAndTrailingLineBreaks
            assertEquals(listOf(listOf("1","0","2"),listOf("2","1","3")),advancedStatisticsRows("time,event,x\n1,0,2\n2,1,3\n"))
            assertEquals(listOf(listOf("NA","NA"),listOf("1","2")),advancedStatisticsRows("NA,NA\n1,2"))
            assertEquals(listOf(listOf("1","2"),listOf("",""),listOf("3","4")),advancedStatisticsRows("1,2\n\n3,4"))
        }
        run { // usesOnlyTheSelectedDataColumns
            assertEquals(listOf(listOf("1","2"),listOf("3","4")),advancedStatisticsRows("1,2,9\n3,4,8",2))
            assertEquals(listOf(listOf("1","2"),listOf("4","5")),advancedStatisticsRows("x,y,z\n1,2,3\n4,5,6",2))
            val wide=(1..25).joinToString(",")
            assertEquals(listOf(listOf("1","2"),listOf("1","2")),advancedStatisticsRows("$wide\n$wide",2))
        }
    }

    @Test(expected=IllegalArgumentException::class) fun keepsTheColumnCapWithinTheSelectedRange() {
        val wide=(1..25).joinToString(",")
        advancedStatisticsRows("$wide\n$wide",25)
    }

    @Test fun formPlansMatchSharedColumnAndOptionCases() {
        val definitions=JSONArray(File("src/main/assets/advanced_statistics.json").readText())
        val cases=JSONArray(File("../tests/fixtures/statistics_forms.json").readText())
        for(i in 0 until cases.length()) {
            val item=cases.getJSONObject(i)
            val definition=(0 until definitions.length()).map {definitions.getJSONObject(it)}.first {it.getString("id")==item.getString("id")}
            val raw=item.getJSONArray("rows");val rows=List(raw.length()){r->raw.getJSONArray(r).let {row->List(row.length()){row.getString(it)}}}
            assertEquals(item.getString("expected"),guidedStatisticsCommand(definition,rows,item.getJSONObject("settings")))
        }
        for(i in 0 until definitions.length()) {
            val definition=definitions.getJSONObject(i)
            if(!definition.has("controls"))continue
            val raw=definition.getJSONArray("exampleRows");val rows=List(raw.length()){r->raw.getJSONArray(r).let {row->List(row.length()){row.getString(it)}}}
            assertEquals(Parser(definition.getString("example")).parse(),Parser(guidedStatisticsCommand(definition,rows)).parse())
        }
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsDuplicateSurvivalRoles() {
        val definitions=JSONArray(File("src/main/assets/advanced_statistics.json").readText())
        val definition=(0 until definitions.length()).map {definitions.getJSONObject(it)}.first {it.getString("id")=="kaplanmeier"}
        guidedStatisticsCommand(definition,listOf(listOf("1","1"),listOf("2","0")),JSONObject().put("time","0").put("event","0"))
    }
    @Test fun survivalPlanPreservesLabelsAndIgnoresUnusedCells() {
        val rows=listOf(listOf("1","yes","A","30",""),listOf("2","no","B","40",""))
        val plan=survivalAnalysisPlan(rows,JSONObject().put("eventValue","yes").put("cox","1").put("predictors","3"),listOf("time","status","arm","age","unused"))
        assertEquals("survivalanalysis([[1,1,1,30],[2,0,2,40]],1,efron,-1,1)",plan.command)
        assertEquals(listOf("A","B"),plan.groups)
        assertEquals(listOf("age"),plan.predictors)
        assertEquals(listOf(0.0 to 1.0,1.0 to 1.0,1.0 to .75,2.0 to .75,2.0 to .375),survivalStepPoints(JSONArray("[[1,4,1,1,.75,.4,.9],[2,2,1,0,.375,.1,.7]]"),4))
    }
    @Test(expected=IllegalArgumentException::class) fun survivalPlanRejectsGroupAsAdditionalPredictor() {
        survivalAnalysisPlan(listOf(listOf("1","1","A")),JSONObject().put("cox","1").put("predictors","2"))
    }

    @Test fun ancovaAndGlmRetainLabelsAndRejectInvalidRolesAndLinks() {
        val schema=JSONArray(File("src/main/assets/advanced_statistics.json").readText())
        val definitions=List(schema.length()){schema.getJSONObject(it)}
        val ancova=definitions.first {it.getString("id")=="ancova"};val glm=definitions.first {it.getString("id")=="glm"}
        val rows=listOf(listOf("Control","1","3"),listOf("Treatment","2","5"))
        val labels=listOf("arm","baseline","response")
        assertEquals(mapOf("Group" to "arm","group:1" to "Control","group:2" to "Treatment","x1" to "baseline"),advancedStatisticsTermLabels(ancova,rows,JSONObject(),labels))
        assertEquals(mapOf("x1" to "baseline"),advancedStatisticsTermLabels(glm,rows,JSONObject().put("predictors","1"),labels))
        for((definition,options) in listOf(ancova to JSONObject().put("response","0"),ancova to JSONObject().put("predictors","0,1"),glm to JSONObject().put("predictors","1").put("family","poisson").put("link","logit"))) {
            assertTrue(runCatching {guidedStatisticsCommand(definition,rows,options)}.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
