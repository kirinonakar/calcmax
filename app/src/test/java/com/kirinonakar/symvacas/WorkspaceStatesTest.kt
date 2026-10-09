package com.kirinonakar.symvacas

import android.content.SharedPreferences
import com.kirinonakar.symvacas.calculator.GraphState
import org.junit.Assert.*
import org.junit.Test

class WorkspaceStatesTest {
    @Test fun graphUndoRestoresDeletedInputsAndParametersWithoutRecordingUndo() {
        val state=GraphState(MemoryPreferences())
        val source="a*x\ncos(x)\n[s] x, 2, -1..1"
        state.updateSource(source)
        state.syncParameters(org.json.JSONArray("[\"a\"]"))
        state.setParameter("a",2.5)
        val parameters=state.graphParameters
        state.graphDerivativeSelected=0
        state.graphSecondDerivativeSelected=1
        state.updateSource("a*x\ncos(x)")
        assertNull(state.graphDerivativeSelected);assertNull(state.graphSecondDerivativeSelected)
        state.updateSource("")
        state.graphParameters=emptyMap()
        assertTrue(state.canUndoInput)
        assertTrue(state.undoInput());assertEquals("a*x\ncos(x)",state.graphSource)
        assertEquals(parameters,state.graphParameters)
        assertTrue(state.undoInput());assertEquals(source,state.graphSource)
        assertEquals(0,state.graphDerivativeSelected)
        assertEquals(1,state.graphSecondDerivativeSelected)
        assertTrue(state.undoInput());assertEquals("sin(x)\ncos(x)",state.graphSource)
        assertFalse(state.canUndoInput);assertFalse(state.undoInput())
        state.updateSource("")
        state.changeKind("polar");assertFalse(state.canUndoInput)
        state.changeKind("cartesian");assertTrue(state.canUndoInput)
        assertTrue(state.undoInput());assertEquals("sin(x)\ncos(x)",state.graphSource)
    }

    @Test fun hmcSettingsMigrateToNutsAndNewSettingsPersist() {
        val prefs=MemoryPreferences(mapOf("statisticsBayesianMethod" to "hmc","statisticsHmcSamples" to "700","statisticsHmcWarmup" to "600","statisticsHmcLeapfrog" to "40","statisticsHmcSeed" to "13","statisticsHmcChains" to "4","regressionReport" to "{\"method\":\"hmc\"}"))
        val state=com.kirinonakar.symvacas.calculator.StatisticsState(prefs)
        assertEquals("nuts",state.statisticsBayesianMethod)
        assertEquals("700",state.statisticsNutsSamples);assertEquals("600",state.statisticsNutsWarmup)
        assertEquals("8",state.statisticsNutsMaxDepth);assertEquals("13",state.statisticsNutsSeed);assertEquals("4",state.statisticsNutsChains)
        assertNull(state.regressionReport)
        state.statisticsNutsSamples="900";state.statisticsNutsMaxDepth="6";state.saveSelection()
        val restored=com.kirinonakar.symvacas.calculator.StatisticsState(prefs)
        assertEquals("nuts",restored.statisticsBayesianMethod);assertEquals("900",restored.statisticsNutsSamples);assertEquals("6",restored.statisticsNutsMaxDepth)
        restored.statisticsNutsMaxDepth="7";val editor=prefs.edit();restored.writeTo(editor);editor.apply()
        assertEquals("7",com.kirinonakar.symvacas.calculator.StatisticsState(prefs).statisticsNutsMaxDepth)
    }

    @Test fun distributionOrientationPersistsInSelectionAndWorkspaceState() {
        val prefs=MemoryPreferences()
        val state=com.kirinonakar.symvacas.calculator.StatisticsState(prefs)
        assertEquals("horizontal",state.statisticsPlotOrientation)
        state.statisticsPlotOrientation="vertical";state.statisticsPlot="Box plot";state.saveSelection()
        val restored=com.kirinonakar.symvacas.calculator.StatisticsState(prefs)
        assertEquals("vertical",restored.statisticsPlotOrientation);assertEquals("Box plot",restored.statisticsPlot)
        restored.statisticsPlotOrientation="horizontal";restored.statisticsPlot="Violin + points"
        val editor=prefs.edit();restored.writeTo(editor);editor.apply()
        val next=com.kirinonakar.symvacas.calculator.StatisticsState(prefs)
        assertEquals("horizontal",next.statisticsPlotOrientation);assertEquals("Violin + points",next.statisticsPlot)
        assertEquals("horizontal",com.kirinonakar.symvacas.calculator.StatisticsState(MemoryPreferences(mapOf("statisticsPlotOrientation" to "invalid"))).statisticsPlotOrientation)
    }
    @Test fun graphParametersValidatePersistAndResetIndependently() {
        run { // typedParametersPreservePrecisionCenterRangesAndPersist
            val prefs=MemoryPreferences(mapOf("graphParameters" to "{\"a\":{\"value\":1,\"min\":-5,\"max\":5,\"animate\":false}}"))
            val state=GraphState(prefs)
            state.setParameter("a",12.345678901,centerRange=true)
            assertEquals(12.345678901,state.parameterPayload().getDouble("a"),0.0)
            assertEquals(7.345678901,state.graphParameters.getValue("a").min,1e-12)
            assertEquals(17.345678901,state.graphParameters.getValue("a").max,1e-12)
            state.setParameter("a",-20.125,centerRange=true)
            assertEquals(-25.125,state.graphParameters.getValue("a").min,1e-12)
            assertEquals(-15.125,state.graphParameters.getValue("a").max,1e-12)
            assertFalse(state.graphParameters.getValue("a").animate)
            val before=state.graphParameters
            for(value in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,1e10))state.setParameter("a",value,centerRange=true)
            assertEquals(before,state.graphParameters)
            val editor=prefs.edit();state.writeTo(editor);editor.apply()
            assertEquals(state.graphParameters,GraphState(prefs).graphParameters)
            state.setParameter("a",100.0)
            assertEquals(-15.125,state.graphParameters.getValue("a").value,1e-12)
        }
        run { // editingBoundsCentersValueAndResetRestoresDefaultsDuringAnimation
            val prefs=MemoryPreferences(mapOf("graphParameters" to "{\"a\":{\"value\":1,\"min\":-5,\"max\":5,\"animate\":true}}"))
            val state=GraphState(prefs)
            state.graphAnimating=true;state.beginAnimation();state.advanceAnimation(.1)
            assertTrue(state.setParameterRange("a",10.0,30.0))
            assertEquals(20.0,state.graphParameters.getValue("a").value,0.0)
            state.advanceAnimation(0.0)
            assertEquals(20.0,state.graphParameters.getValue("a").value,1e-12)
            state.resetParameters()
            val spec=state.graphParameters.getValue("a")
            assertEquals(1.0,spec.value,0.0);assertEquals(-5.0,spec.min,0.0);assertEquals(5.0,spec.max,0.0)
            assertTrue(spec.animate)
            state.advanceAnimation(0.0)
            assertEquals(1.0,state.graphParameters.getValue("a").value,1e-12)
            val editor=prefs.edit();state.writeTo(editor);editor.apply()
            assertEquals(state.graphParameters,GraphState(prefs).graphParameters)
        }
        run { // resettingOneParameterPreservesOtherValuesRangesAndAnimationChoices
            val prefs=MemoryPreferences(mapOf("graphParameters" to "{\"a\":{\"value\":20,\"min\":10,\"max\":30,\"animate\":false},\"b\":{\"value\":-10,\"min\":-20,\"max\":0,\"animate\":true}}"))
            val state=GraphState(prefs)
            val other=state.graphParameters.getValue("b")
            state.resetParameters("a")
            val reset=state.graphParameters.getValue("a")
            assertEquals(1.0,reset.value,0.0);assertEquals(-5.0,reset.min,0.0);assertEquals(5.0,reset.max,0.0)
            assertFalse(reset.animate);assertEquals(other,state.graphParameters.getValue("b"))
            val editor=prefs.edit();state.writeTo(editor);editor.apply()
            assertEquals(state.graphParameters,GraphState(prefs).graphParameters)
        }
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
    @Test fun surfaceStatePreservesLastMeshAppearanceAndValidBounds() {
        run { // surfaceRecalculationFailuresRetainTheLastMeshAndCanBeRetried
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
        run { // surfaceAppearancePersistsAndInvalidSavedOptionsUseDefaults
            run { // surfaceAppearancePersistsAndInvalidSavedOptionsUseDefaults
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
            run { // invalidRestoredGraphBoundsUseDefaults
                val graph=GraphState(MemoryPreferences(mapOf("xMin" to "invalid", "xMax" to "NaN")))
                assertEquals(-10.0,graph.xMin,0.0)
                assertEquals(10.0,graph.xMax,0.0)
            }
        }
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
