package com.kirinonakar.calcmax

import android.content.SharedPreferences
import com.kirinonakar.calcmax.calculator.GraphState
import com.kirinonakar.calcmax.calculator.PythonState
import com.kirinonakar.calcmax.calculator.StatisticsState
import org.junit.Assert.*
import org.junit.Test

class WorkspaceStatesTest {
    @Test fun oldPreferencesStillRoundTripWithoutBackingUpDocumentUri() {
        val prefs=MemoryPreferences(mapOf(
            "graphKind" to "polar", "graphSources" to "{\"polar\":\"2*cos(t)\"}",
            "graphParameters" to "{\"a\":{\"value\":2,\"min\":0,\"max\":4}}",
            "dataSets" to "{\"D1\":{\"csv\":\"1,2\",\"kind\":\"list\"}}",
            "statisticsName" to "D1", "statisticsData" to "1,2", "regressionFit" to "y=x",
            "pythonSource" to "print(42)", "pythonFileName" to "draft.py",
            "pythonUri" to "content://old-device/draft.py"
        ))
        val local=MemoryPreferences()
        val graph=GraphState(prefs)
        val statistics=StatisticsState(prefs)
        val python=PythonState(prefs,local)

        assertEquals("2*cos(t)",graph.graphSource)
        assertEquals(2.0,graph.graphParameters.getValue("a").value,0.0)
        assertEquals("1,2",statistics.dataSets.getJSONObject("D1").getString("csv"))
        assertEquals("print(42)",python.pythonSource)
        assertEquals("content://old-device/draft.py",python.pythonUri)
        assertFalse(prefs.contains("pythonUri"))
        assertEquals(python.pythonUri,local.getString("pythonUri",null))

        graph.graphSource="3*sin(t)"
        statistics.saveDataSet("D2","3,4","list")
        python.editSource("print(43)")
        val editor=prefs.edit()
        graph.writeTo(editor);statistics.writeTo(editor);python.writeTo(editor);editor.apply()

        assertEquals("3*sin(t)",GraphState(prefs).graphSource)
        assertTrue(StatisticsState(prefs).dataSets.has("D2"))
        assertEquals("print(43)",PythonState(prefs,local).pythonSource)
        assertFalse(prefs.contains("pythonUri"))
    }

    @Test fun invalidRestoredGraphBoundsUseDefaults() {
        val graph=GraphState(MemoryPreferences(mapOf("xMin" to "invalid", "xMax" to "NaN")))
        assertEquals(-10.0,graph.xMin,0.0)
        assertEquals(10.0,graph.xMax,0.0)
    }

    @Test fun clearingRegressionAlsoClearsItsSelectedModeAndCorrelation() {
        val prefs=MemoryPreferences(mapOf("regressionFit" to "2*x+1", "regressionData" to "1,3\n2,5", "regressionMode" to "linear", "regressionCorrelation" to "1.0"))
        val state=StatisticsState(prefs)
        assertEquals("linear",state.regressionMode)
        assertEquals(1.0,state.regressionCorrelation!!,0.0)
        state.clearRegression()
        assertEquals("",state.regressionFit)
        assertEquals("",state.regressionMode)
        assertNull(state.regressionCorrelation)
        val editor=prefs.edit()
        state.writeTo(editor);editor.apply()
        val restored=StatisticsState(prefs)
        assertEquals("",restored.regressionMode)
        assertNull(restored.regressionCorrelation)
    }

    @Test fun graphKindsKeepSeparateSourcesAndDiscardOldAnalysis() {
        val graph=GraphState(MemoryPreferences())
        graph.updateSource("x^2")
        graph.graphData=org.json.JSONObject().put("old",true)
        assertTrue(graph.changeKind("polar"))
        assertEquals("2*cos(3*t)",graph.graphSource)
        assertNull(graph.graphData)
        graph.updateSource("3*sin(t)")
        assertTrue(graph.changeKind("cartesian"))
        assertEquals("x^2",graph.graphSource)
        assertFalse(graph.changeKind("cartesian"))
    }

    private class MemoryPreferences(initial:Map<String,Any?> = emptyMap()):SharedPreferences {
        private val values=initial.toMutableMap()
        override fun getAll():MutableMap<String,*> = values.toMutableMap()
        override fun getString(key:String?,defValue:String?):String? = values[key] as? String ?: defValue
        override fun getStringSet(key:String?,defValues:MutableSet<String>?):MutableSet<String>? =
            (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
        override fun getInt(key:String?,defValue:Int):Int = values[key] as? Int ?: defValue
        override fun getLong(key:String?,defValue:Long):Long = values[key] as? Long ?: defValue
        override fun getFloat(key:String?,defValue:Float):Float = values[key] as? Float ?: defValue
        override fun getBoolean(key:String?,defValue:Boolean):Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key:String?):Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit():SharedPreferences.Editor = object:SharedPreferences.Editor {
            private val updates=mutableMapOf<String,Any?>()
            private val removals=mutableSetOf<String>()
            private var clearAll=false
            override fun putString(key:String?,value:String?):SharedPreferences.Editor=apply {updates[key!!]=value}
            override fun putStringSet(key:String?,values:MutableSet<String>?):SharedPreferences.Editor=apply {updates[key!!]=values}
            override fun putInt(key:String?,value:Int):SharedPreferences.Editor=apply {updates[key!!]=value}
            override fun putLong(key:String?,value:Long):SharedPreferences.Editor=apply {updates[key!!]=value}
            override fun putFloat(key:String?,value:Float):SharedPreferences.Editor=apply {updates[key!!]=value}
            override fun putBoolean(key:String?,value:Boolean):SharedPreferences.Editor=apply {updates[key!!]=value}
            override fun remove(key:String?):SharedPreferences.Editor=apply {removals+=key!!}
            override fun clear():SharedPreferences.Editor=apply {clearAll=true}
            override fun commit():Boolean {
                if(clearAll)values.clear()
                removals.forEach(values::remove)
                values.putAll(updates)
                return true
            }
            override fun apply() {commit()}
        }
    }
}
