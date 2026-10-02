package com.kirinonakar.calcmax

import android.content.SharedPreferences
import com.kirinonakar.calcmax.calculator.GraphState
import com.kirinonakar.calcmax.calculator.PythonState
import com.kirinonakar.calcmax.calculator.StatisticsState
import org.junit.Assert.*
import org.junit.Test

class WorkspaceStatesTest {
    @Test fun legacyImplicitWorkspaceRestoresAsCartesianWithoutLosingItsEquationOrBounds() {
        val prefs=MemoryPreferences(mapOf("graphKind" to "implicit","graphSources" to "{\"implicit\":\"y^2+x^2=1\",\"cartesian\":\"x+1\"}","xMin" to "-2","xMax" to "2"))
        val state=GraphState(prefs)
        assertEquals("cartesian",state.graphKind);assertEquals("y^2+x^2=1",state.graphSource)
        assertEquals(-2.0,state.xMin,0.0);assertEquals(2.0,state.xMax,0.0)
        state.changeKind("polar");state.changeKind("cartesian")
        assertEquals("y^2+x^2=1",state.graphSource)
        val editor=prefs.edit();state.writeTo(editor);editor.apply()
        assertEquals("y^2+x^2=1",GraphState(prefs).graphSource)
    }
    @Test fun typedParametersPreservePrecisionExpandRangesAndPersist() {
        val prefs=MemoryPreferences(mapOf("graphParameters" to "{\"a\":{\"value\":1,\"min\":-5,\"max\":5,\"animate\":false}}"))
        val state=GraphState(prefs)
        state.setParameter("a",12.345678901,expandRange=true)
        assertEquals(12.345678901,state.parameterPayload().getDouble("a"),0.0)
        assertEquals(12.345678901,state.graphParameters.getValue("a").max,0.0)
        state.setParameter("a",-20.125,expandRange=true)
        assertEquals(-20.125,state.graphParameters.getValue("a").min,0.0)
        assertFalse(state.graphParameters.getValue("a").animate)
        val before=state.graphParameters
        for(value in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,1e10))state.setParameter("a",value,expandRange=true)
        assertEquals(before,state.graphParameters)
        val editor=prefs.edit();state.writeTo(editor);editor.apply()
        assertEquals(state.graphParameters,GraphState(prefs).graphParameters)
        state.setParameter("a",100.0)
        assertEquals(12.345678901,state.graphParameters.getValue("a").value,0.0)
    }
    @Test fun typedParameterUpdatesRealignRunningAnimation() {
        val state=GraphState(MemoryPreferences(mapOf("graphParameters" to "{\"a\":{\"value\":1,\"min\":-5,\"max\":5}}")))
        state.graphAnimating=true;state.beginAnimation();state.advanceAnimation(.1)
        state.setParameter("a",-12.3456789,expandRange=true);state.advanceAnimation(0.0)
        assertEquals(-12.3456789,state.graphParameters.getValue("a").value,1e-12)
        state.advanceAnimation(.01)
        val spec=state.graphParameters.getValue("a")
        assertTrue(spec.value in spec.min..spec.max)
    }
    @Test fun animationStartsAtCurrentValuesAndElapsedTimeIsIndependentOfTickRate() {
        val prefs=MemoryPreferences(mapOf("graphParameters" to "{\"a\":{\"value\":2,\"min\":-5,\"max\":5},\"b\":{\"value\":-1,\"min\":-5,\"max\":5,\"animate\":false}}"))
        val fast=GraphState(prefs);val slow=GraphState(prefs)
        fast.graphAnimating=true;slow.graphAnimating=true;fast.beginAnimation();slow.beginAnimation()
        fast.advanceAnimation(0.0)
        assertEquals(2.0,fast.graphParameters.getValue("a").value,1e-12)
        repeat(60){fast.advanceAnimation(1.0/60)}
        repeat(30){slow.advanceAnimation(1.0/30)}
        assertEquals(fast.graphParameters.getValue("a").value,slow.graphParameters.getValue("a").value,1e-12)
        assertEquals(-1.0,fast.graphParameters.getValue("b").value,0.0)
        fast.setParameterAnimation("b",true);fast.advanceAnimation(0.0)
        assertEquals(-1.0,fast.graphParameters.getValue("b").value,1e-12)
        fast.setParameter("a",3.0);fast.advanceAnimation(0.0)
        assertEquals(3.0,fast.graphParameters.getValue("a").value,1e-12)
        val phase=fast.animationPhase;fast.advanceAnimation(10.0)
        assertEquals(.1,fast.animationPhase-phase,1e-12)
    }
    @Test fun surfaceRecalculationFailuresRetainTheLastMeshAndCanBeRetried() {
        val state=GraphState(MemoryPreferences());state.changeKind("surface")
        val first=org.json.JSONObject("{\"ok\":true,\"surface\":[[[0,0,1],[1,0,2]],[[0,1,2],[1,1,3]]],\"parameters\":[]}")
        state.applyPlotResponse(first,"first")
        state.updateSource(state.graphSource)
        assertSame(first,state.graphData)
        state.surfaceZoom=3f
        state.applyPlotResponse(org.json.JSONObject().put("ok",false).put("error","Computation timed out"),"higher-density")
        assertSame(first,state.graphData)
        assertEquals("first",state.graphResultSignature)
        assertNotEquals("higher-density",state.graphResultSignature)
        state.updateSource("sin(x+y)")
        assertSame(first,state.graphData)
        val next=org.json.JSONObject(first.toString())
        state.applyPlotResponse(next,"higher-density")
        assertSame(next,state.graphData)
        assertEquals("higher-density",state.graphResultSignature)
        state.changeKind("cartesian")
        assertNull(state.graphData);assertNull(state.graphResultSignature)
    }
    @Test fun surfaceRangeResetPreservesAppearanceAndZoomAndPersistsAutomaticZ() {
        val prefs=MemoryPreferences();val state=GraphState(prefs)
        state.xMin=100.0;state.xMax=101.0;state.yMin=200.0;state.yMax=201.0;state.zMin=3.0;state.zMax=4.0
        state.surfaceZoom=2f;state.surfaceRenderMode="surface";state.surfaceColor="#ff0000"
        state.resetSurfaceRanges()
        assertEquals(-3.0,state.xMin,0.0);assertEquals(3.0,state.xMax,0.0)
        assertEquals(-3.0,state.yMin,0.0);assertEquals(3.0,state.yMax,0.0)
        assertNull(state.zMin);assertNull(state.zMax)
        assertEquals(2f,state.surfaceZoom,0f);assertEquals("surface",state.surfaceRenderMode);assertEquals("#ff0000",state.surfaceColor)
        val editor=prefs.edit();state.writeTo(editor);editor.apply()
        val restored=GraphState(prefs);assertNull(restored.zMin);assertNull(restored.zMax);assertEquals(-3.0,restored.xMin,0.0)
    }
    @Test fun graphAnimationOnlyMovesEnabledParametersAndPersistsSelection() {
        val prefs=MemoryPreferences(mapOf("graphParameters" to "{\"a\":{\"value\":1,\"min\":0,\"max\":4},\"b\":{\"value\":2,\"min\":-5,\"max\":5}}"))
        val state=GraphState(prefs)
        assertTrue(state.graphParameters.getValue("a").animate)
        state.setParameterAnimation("b",false)
        assertTrue(state.animateParameters(.75))
        assertEquals(3.0,state.graphParameters.getValue("a").value,0.0)
        assertEquals(2.0,state.graphParameters.getValue("b").value,0.0)
        val editor=prefs.edit();state.writeTo(editor);editor.apply()
        val restored=GraphState(prefs)
        assertFalse(restored.graphParameters.getValue("b").animate)
        restored.setParameterAnimation("a",false)
        assertFalse(restored.animateParameters(.25))
        assertEquals(3.0,restored.graphParameters.getValue("a").value,0.0)
        restored.setParameterAnimation("b",true)
        assertTrue(restored.animateParameters(.25))
        assertEquals(-2.5,restored.graphParameters.getValue("b").value,0.0)
    }
    @Test fun surfaceAppearancePersistsAndInvalidSavedOptionsUseDefaults() {
        val prefs=MemoryPreferences()
        val state=GraphState(prefs)
        state.surfaceRenderMode="surface-wireframe";state.surfaceColor="#3b70bd";state.surfaceSamples=40;state.surfaceAutoDensity=false;state.surfaceZoom=2f
        val editor=prefs.edit();state.writeTo(editor);editor.apply()
        val restored=GraphState(prefs)
        assertEquals("surface-wireframe",restored.surfaceRenderMode)
        assertEquals("#3b70bd",restored.surfaceColor);assertEquals(40,restored.surfaceSamples)
        assertFalse(restored.surfaceAutoDensity);assertEquals(2f,restored.surfaceZoom,0f)
        val invalid=GraphState(MemoryPreferences(mapOf("surfaceRenderMode" to "unknown","surfaceColor" to "invalid","surfaceSamples" to 1000)))
        assertEquals("wireframe",invalid.surfaceRenderMode);assertEquals("#007b68",invalid.surfaceColor);assertEquals(96,invalid.surfaceSamples)
    }
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
        statistics.saveDataSet("D3","x,y,z\n1,2,3","xyz")
        python.editSource("print(43)")
        val editor=prefs.edit()
        graph.writeTo(editor);statistics.writeTo(editor);python.writeTo(editor);editor.apply()

        assertEquals("3*sin(t)",GraphState(prefs).graphSource)
        assertTrue(StatisticsState(prefs).dataSets.has("D2"))
        assertEquals("xyz",StatisticsState(prefs).dataSets.getJSONObject("D3").getString("kind"))
        assertEquals("print(43)",PythonState(prefs,local).pythonSource)
        assertFalse(prefs.contains("pythonUri"))
    }

    @Test fun invalidRestoredGraphBoundsUseDefaults() {
        val graph=GraphState(MemoryPreferences(mapOf("xMin" to "invalid", "xMax" to "NaN")))
        assertEquals(-10.0,graph.xMin,0.0)
        assertEquals(10.0,graph.xMax,0.0)
    }

    @Test fun clearingRegressionAlsoClearsItsSelectedModeAndCorrelation() {
        val prefs=MemoryPreferences(mapOf("regressionFit" to "2*x+1", "regressionData" to "1,3\n2,5", "regressionMode" to "linear", "regressionCorrelation" to "1.0", "regressionParameters" to "[[\"ADC\",\"0.001\"]]"))
        val state=StatisticsState(prefs)
        assertEquals("linear",state.regressionMode)
        assertEquals(1.0,state.regressionCorrelation!!,0.0)
        assertEquals(listOf("ADC" to "0.001"),state.regressionParameters)
        state.clearRegression()
        assertEquals("",state.regressionFit)
        assertEquals("",state.regressionMode)
        assertNull(state.regressionCorrelation)
        assertEquals(emptyList<Pair<String,String>>(),state.regressionParameters)
        val editor=prefs.edit()
        state.writeTo(editor);editor.apply()
        val restored=StatisticsState(prefs)
        assertEquals("",restored.regressionMode)
        assertNull(restored.regressionCorrelation)
        assertEquals(emptyList<Pair<String,String>>(),restored.regressionParameters)
    }

    @Test fun graphKindsKeepSeparateSourcesAndDiscardOldAnalysis() {
        val prefs=MemoryPreferences()
        val graph=GraphState(prefs)
        graph.updateSource("x^2")
        graph.graphData=org.json.JSONObject().put("old",true)
        assertTrue(graph.changeKind("polar"))
        assertEquals("2*cos(3*t)",graph.graphSource)
        assertNull(graph.graphData)
        graph.updateSource("3*sin(t)")
        assertTrue(graph.changeKind("cartesian"))
        assertEquals("x^2",graph.graphSource)
        assertFalse(graph.changeKind("cartesian"))
        assertFalse(graph.changeKind("implicit"))
        assertEquals("cartesian",graph.graphKind)
        graph.updateSource("x*y=1")
        assertTrue(graph.changeKind("polar"))
        assertTrue(graph.changeKind("cartesian"))
        assertEquals("x*y=1",graph.graphSource)
        val editor=prefs.edit()
        graph.writeTo(editor);editor.apply()
        val restored=GraphState(prefs)
        assertEquals("cartesian",restored.graphKind)
        assertEquals("x*y=1",restored.graphSource)
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
