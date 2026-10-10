package com.kirinonakar.symvacas.ui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.json.JSONArray
import com.kirinonakar.symvacas.math.Parser

class AdvancedStatisticsTest {
    @Test fun modelWorkflowsShareMeasurementDataAndValidateAssignmentsAndPaths() {
        val cases=JSONArray(File("../tests/fixtures/statistics_model_workflow.json").readText())
        for(i in 0 until cases.length()) {
            val item=cases.getJSONObject(i);val workflow=item.getJSONObject("workflow");val settings=item.optJSONObject("settings") ?: JSONObject()
            val factors=workflow.getJSONArray("factors")
            val assignment=settings.optString("factors",List(factors.length()){factors.getInt(it).toString()}.joinToString(","))
            val result=runCatching {statisticsModelWorkflowPlan(workflow,assignment,settings.optString("paths"))}
            if(item.has("error"))assertEquals(item.getString("name"),item.getString("error"),result.exceptionOrNull()?.message)
            else {
                val plan=result.getOrThrow()
                assertEquals(item.getString("name"),Parser(item.getString("expected")).parse(),Parser(plan.expression).parse())
                val labels=workflow.getJSONObject("termLabels")
                assertEquals(labels.keys().asSequence().associateWith {labels.getString(it)},plan.termLabels)
            }
        }
    }
    @Test fun measurementExamplesIgnoreCurrentDataSelectionsAndKeepTheirOwnDrafts() {
        val schema=JSONArray(File("src/main/assets/advanced_statistics.json").readText())
        val sem=List(schema.length()){schema.getJSONObject(it)}.first {it.getString("id")=="sem"}
        val stale=JSONObject().put("sem",JSONObject().put("columns","0,1,2,3,4,5,6").put("factors","1,1")).toString()
        val current=advancedStatisticsFormSettings("sem","current",stale,"{}")
        assertEquals("1,1",current.getString("factors"))
        assertTrue(runCatching {guidedStatisticsCommand(sem,advancedStatisticsExampleRows(sem),current)}.isFailure)
        val example=advancedStatisticsFormSettings("sem","example",stale,"{}")
        val expression=guidedStatisticsCommand(sem,advancedStatisticsExampleRows(sem,example),example)
        assertEquals(Parser(sem.getString("example")).parse(),Parser(expression).parse())
        val saved=JSONObject().put("sem",JSONObject().put("groupMode","multi").put("invariance","strict")).toString()
        val restored=advancedStatisticsFormSettings("sem","example",stale,saved)
        assertEquals("strict",restored.getString("invariance"))
        assertEquals("1,1",advancedStatisticsFormSettings("sem","current",stale,saved).getString("factors"))
        val grouped=Parser(guidedStatisticsCommand(sem,advancedStatisticsExampleRows(sem,restored),restored)).parse()
        assertEquals(6,grouped.args[0].args[0].args.size)
        assertEquals(96,grouped.args[5].args.size)
    }

    @Test fun measurementExampleEstimationAndGroupChoicesKeepFactorIdsAligned() {
        val schema=JSONArray(File("src/main/assets/advanced_statistics.json").readText())
        for(id in listOf("cfa","sem")) {
            val definition=List(schema.length()){schema.getJSONObject(it)}.first {it.getString("id")==id}
            for(estimator in listOf("ml","wlsmv"))for(group in listOf("single","multi"))for(invariance in listOf("configural","metric","scalar","strict")) {
                val settings=JSONObject().put("estimator",estimator).put("groupMode",group).put("invariance",invariance)
                val rows=advancedStatisticsExampleRows(definition,settings)
                val tree=Parser(guidedStatisticsCommand(definition,rows,settings)).parse()
                assertEquals(6,tree.args[0].args[0].args.size)
                assertEquals(6,tree.args[1].args.size)
                assertEquals(if(group=="multi")7 else 6,rows.first().size)
                if(estimator=="wlsmv")assertTrue(rows.all {row->row.drop(if(group=="multi")1 else 0).all {it.toInt() in 1..4}})
            }
        }
    }

    @Test fun weightedKappaRequiresOrderedSharedCategories() {
        val schema=JSONArray(File("src/main/assets/advanced_statistics.json").readText())
        val definition=List(schema.length()){schema.getJSONObject(it)}.first {it.getString("id")=="cohenkappa"}
        val rows=listOf(listOf("low","mid"),listOf("high","high"),listOf("mid","low"))
        val settings=JSONObject().put("layout","pairs").put("weights","quadratic")
        assertTrue(runCatching {guidedStatisticsCommand(definition,rows,settings)}.isFailure)
        settings.put("categories","low,mid,high")
        assertEquals("cohenkappa([[0,1,0],[1,0,0],[0,0,1]],quadratic)",guidedStatisticsCommand(definition,rows,settings))
        val labels=advancedStatisticsTermLabels(definition,rows,settings,listOf("Reviewer A","Reviewer B"))
        assertEquals("mid",labels["table:column:2"]);assertEquals("Reviewer A",labels["table:row"])
        settings.put("categories","low,high")
        assertTrue(runCatching {guidedStatisticsCommand(definition,rows,settings)}.isFailure)
    }
    @Test fun middlePlotGroupingKeepsHeaderLabelsAndValues() {
        val rows=listOf(listOf("1","Control","2"),listOf("3","Drug","4"),listOf("5","Control","6"))
        val labels=listOf("height (x)","treatment (y)","weight (z)")
        val panels=statisticsPlotPanels(rows,"xyz","column:1",labels)
        assertEquals(listOf("height (x)","weight (z)"),panels.map {it.label})
        assertEquals(listOf("Control" to listOf(1.0,5.0),"Drug" to listOf(3.0)),panels[0].series)
        assertEquals(listOf("height (x)","weight (z)"),statisticsHeatMapData(rows,"xyz","column:1",columnNames=labels).columns)
        assertEquals("welchanova([1,4],[3,6])",statisticsTestCommand("ANOVA",listOf(listOf("s1","1","2","3"),listOf("s2","4","5","6")),"columns:4","x","Two-sided","0","2","95",groupColumns="1,3"))
    }
    @Test fun imputationApplicationPreservesHeadersAndObservedPrecision() {
        val source="\"Height, cm\",Weight,ID\n1,,s1\n2,4.000,s2\nNA,6,s3"
        val filled=JSONArray("[[\"1\",\"5\"],[\"2\",\"4\"],[\"1.5\",\"6\"]]")
        assertEquals("\"Height, cm\",Weight,ID\n1,5,s1\n2,4.000,s2\n1.5,6,s3",statisticsImputationCSV(source,filled,2))
        assertTrue(runCatching {statisticsImputationCSV(source,JSONArray("[[\"1\",\"5\"]]"),2)}.isFailure)
        assertEquals("ttest2(0,[3,6],[4,9],student)",statisticsTestCommand("t test",listOf(listOf("1","2","3","4"),listOf("2","5","6","9")),"columns:4","x-y","Two-sided","0","2","95",firstGroup="z",secondGroup="x4",independentMethod="student"))
    }
    private fun definition(id:String,input:String,suffix:String="")=JSONObject().put("id",id).put("input",input).put("suffix",suffix)

    @Test(expected=IllegalArgumentException::class) fun rejectsIncompleteModelRows() {
        advancedStatisticsCommand(definition("cox","table"),listOf(listOf("1","","0"),listOf("2","1","1")))
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
    @Test fun survivalPlanPreservesLabelsAndIgnoresUnusedCells() {
        val rows=listOf(listOf("1","yes","A","30",""),listOf("2","no","B","40",""))
        val plan=survivalAnalysisPlan(rows,JSONObject().put("eventValue","yes").put("cox","1").put("predictors","3"),listOf("time","status","arm","age","unused"))
        assertEquals("survivalanalysis([[1,1,1,30],[2,0,2,40]],1,efron,-1,1)",plan.command)
        assertEquals(listOf("A","B"),plan.groups)
        assertEquals(listOf("age"),plan.predictors)
        assertEquals(listOf(0.0 to 1.0,1.0 to 1.0,1.0 to .75,2.0 to .75,2.0 to .375),survivalStepPoints(JSONArray("[[1,4,1,1,.75,.4,.9],[2,2,1,0,.375,.1,.7]]"),4))
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
