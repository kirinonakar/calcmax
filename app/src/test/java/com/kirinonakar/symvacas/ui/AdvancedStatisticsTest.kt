package com.kirinonakar.symvacas.ui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.json.JSONArray
import com.kirinonakar.symvacas.math.Parser
import com.kirinonakar.symvacas.math.requiresExplicitEvaluation

class AdvancedStatisticsTest {
    private fun definition(id:String,input:String,suffix:String="")=JSONObject().put("id",id).put("input",input).put("suffix",suffix)
    @Test fun preservesMissingCellsAndSubjectRows() {
        assertEquals("impute([[1,NA],[NA,2]],mean)",advancedStatisticsCommand(definition("impute","table",",mean"),listOf(listOf("1",""),listOf("","2"))))
        assertEquals("gee([[1,0,2],[1,1,4]],gaussian)",advancedStatisticsCommand(definition("gee","table",",gaussian"),listOf(listOf("1","0","2"),listOf("1","1","4"))))
    }
    @Test fun separatesSurvivalGroupsWithoutLosingCensoring() {
        val rows=listOf(listOf("1","1","A"),listOf("2","0","B"),listOf("3","0","A"),listOf("4","1","B"))
        assertEquals("logrank([[1,1],[3,0]],[[2,0],[4,1]])",advancedStatisticsCommand(definition("logrank","survivalgroups"),rows))
    }
    @Test fun preservesIndependentSampleLengths() {
        assertEquals("cohend([1,3],[2],independent)",advancedStatisticsCommand(definition("cohend","groups",",independent"),listOf(listOf("1","2"),listOf("3",""))))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsIncompleteModelRows() {
        advancedStatisticsCommand(definition("cox","table"),listOf(listOf("1","","0"),listOf("2","1","1")))
    }
    @Test fun preservesMissingFirstRowAndIgnoresHeadersAndTrailingLineBreaks() {
        assertEquals(listOf(listOf("1","0","2"),listOf("2","1","3")),advancedStatisticsRows("time,event,x\n1,0,2\n2,1,3\n"))
        assertEquals(listOf(listOf("NA","NA"),listOf("1","2")),advancedStatisticsRows("NA,NA\n1,2"))
        assertEquals(listOf(listOf("1","2"),listOf("",""),listOf("3","4")),advancedStatisticsRows("1,2\n\n3,4"))
    }
    @Test fun sharedExamplesParseOnAndroidAndWaitForExplicitEvaluation() {
        val schema=JSONArray(File("src/main/assets/advanced_statistics.json").readText())
        for(i in 0 until schema.length()) {
            val item=schema.getJSONObject(i)
            val expression=Parser(item.getString("example")).parse()
            assertEquals(item.getString("id"),expression.value)
            assertTrue(requiresExplicitEvaluation(expression))
        }
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
}
