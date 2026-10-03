package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.calculator.FunctionTransfer
import com.kirinonakar.calcmax.math.Parser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FunctionTransferTest {
    @Test fun answerLinkedFunctionsExpireWhenTheAnswerChangesOrIsCleared() {
        val answer=JSONObject("""{"kind":"snapshot_symbol","value":"x"}""")
        val functions=JSONObject().put("g",FunctionTransfer.definition("g","x","x").json().put("answerSource",answer))
            .put("f",FunctionTransfer.definition("f","x","x+1").json())
        val restored=JSONObject(functions.toString())
        val imported=FunctionTransfer.decode(FunctionTransfer.encode(restored)).definitions.first {it.name=="g"}.json()
        assertTrue(imported.has("answerSource"))
        assertFalse(FunctionTransfer.removeExpiredAnswerFunctions(JSONObject().put("g",imported),null).has("g"))
        assertTrue(FunctionTransfer.removeExpiredAnswerFunctions(restored,JSONObject("""{"value":"x","kind":"snapshot_symbol"}""")).has("g"))
        val changed=FunctionTransfer.removeExpiredAnswerFunctions(restored,JSONObject("""{"kind":"number","value":"2"}"""))
        assertFalse(changed.has("g"));assertTrue(changed.has("f"));assertTrue(restored.has("g"))
        assertFalse(FunctionTransfer.removeExpiredAnswerFunctions(restored,null).has("g"))
        restored.put("g",FunctionTransfer.definition("g","x","x+3").json())
        assertTrue(FunctionTransfer.removeExpiredAnswerFunctions(restored,null).has("g"))
    }
    @Test fun resultTargetsRecognizeDerivativeAndAnswerStorage() {
        val derivative=FunctionTransfer.resultTarget(Parser("diff(f(x),x)=g(x)").parse())!!
        assertEquals("g",derivative.name)
        assertEquals(listOf("x"),derivative.parameters)
        assertEquals("diff",derivative.expression.value)
        val integral=FunctionTransfer.resultTarget(Parser("integrate(x,x)=f(x)").parse())!!
        assertEquals("f",integral.name)
        assertEquals(listOf("x"),integral.parameters)
        assertEquals("integrate",integral.expression.value)
        assertNull(FunctionTransfer.resultTarget(Parser("integrate(x,x)=Ans").parse())!!.name)
        val reversedIntegral=FunctionTransfer.resultTarget(Parser("f(x)=integrate(x,x)").parse())!!
        assertEquals("f",reversedIntegral.name)
        assertEquals(listOf("x"),reversedIntegral.parameters)
        assertEquals("integrate",reversedIntegral.expression.value)
        val reversedDerivative=FunctionTransfer.resultTarget(Parser("g(x)=diff(f(x),x)").parse())!!
        assertEquals("g",reversedDerivative.name)
        assertEquals("diff",reversedDerivative.expression.value)
        assertNull(FunctionTransfer.resultTarget(Parser("Ans=integrate(x,x)").parse())!!.name)
        val answer=FunctionTransfer.resultTarget(Parser("diff(f(x),x)=Ans").parse())!!
        assertNull(answer.name)
        assertEquals("g",FunctionTransfer.resultTarget(Parser("Ans=g(x)").parse())!!.name)
        val explicitAnswer=FunctionTransfer.resultTarget(Parser("Ans(x)=x^2+1").parse())!!
        assertNull(explicitAnswer.name)
        assertEquals(listOf("x"),explicitAnswer.parameters)
        assertEquals("+",explicitAnswer.expression.value)
        for(source in listOf("f(x)=x^2","x=2","x^2=4","solve(x=2,x)"))assertNull(source,FunctionTransfer.resultTarget(Parser(source).parse()))
        for(source in listOf("diff(f(x),x)=sin(x)","diff(f(x),x)=g(x,x)","diff(f(x),x)=g(1)","diff(f(x),x)=g()"))assertTrue(source,runCatching {FunctionTransfer.resultTarget(Parser(source).parse())}.isFailure)
        for(source in listOf("integrate(x,x)=sin(x)","integrate(x,x)=f(x,x)","integrate(x,x)=f(1)","integrate(x,x)=f()"))assertTrue(source,runCatching {FunctionTransfer.resultTarget(Parser(source).parse())}.isFailure)
        for(source in listOf("sin(x)=integrate(x,x)","f(x,x)=diff(x,x)","f(1)=integrate(x,x)","f()=diff(x,x)"))assertTrue(source,runCatching {FunctionTransfer.resultTarget(Parser(source).parse())}.isFailure)
    }
    @Test fun resultDefinitionKeepsFrozenCallsAndDomainGuardsAndBindsParameters() {
        val snapshot=JSONObject("""{"kind":"restricted","args":[{"kind":"frozen_call","value":"cos","args":[{"kind":"snapshot_symbol","value":"x"}]},{"kind":"relation","value":"!=","args":[{"kind":"snapshot_symbol","value":"x"},{"kind":"number","value":"0"}]}]}""")
        val definition=FunctionTransfer.resultDefinition("g",listOf("x"),snapshot)
        assertEquals("cos(x)",definition.source)
        assertEquals("restricted",definition.body.getString("kind"))
        assertEquals("symbol",definition.body.getJSONArray("args").getJSONObject(0).getJSONArray("args").getJSONObject(0).getString("kind"))
        assertEquals("snapshot_symbol",snapshot.getJSONArray("args").getJSONObject(0).getJSONArray("args").getJSONObject(0).getString("kind"))
    }
    @Test fun encodeThenDecodeKeepsNameParametersAndSource() {
        val functions=JSONObject("""{"f":{"parameters":["x"],"source":"x^2+1"},"g":{"parameters":["x","y"],"source":"x+y"}}""")
        val encoded=FunctionTransfer.encode(functions)
        assertTrue(encoded.contains("calcmax.functions"))
        val decoded=FunctionTransfer.decode(encoded)
        assertEquals(0,decoded.skipped)
        assertEquals(listOf("f","g"),decoded.definitions.map {it.name})
        assertEquals(listOf("x","y"),decoded.definitions[1].parameters)
        assertEquals("x+y",decoded.definitions[1].source)
    }
    @Test fun decodeAcceptsBareMapsAndRebuildsMissingSourceFromTheTree() {
        val decoded=FunctionTransfer.decode("""{"h":{"parameters":["t"],"body":{"kind":"binary","value":"+","args":[{"kind":"symbol","value":"t"},{"kind":"number","value":"1"}]}}}""")
        assertEquals("h",decoded.definitions.single().name)
        assertEquals("(t+1)",decoded.definitions.single().source)
    }
    @Test fun decodeSkipsReservedInvalidAndUnparsableEntries() {
        val decoded=FunctionTransfer.decode("""{"format":"calcmax.functions","version":1,"functions":{"sin":{"parameters":["x"],"source":"x"},"ok":{"parameters":["x"],"source":"x+1"},"bad":{"parameters":[],"source":"x"},"broken":{"parameters":["x"],"source":"x+"}}}""")
        assertEquals(listOf("ok"),decoded.definitions.map {it.name})
        assertEquals(3,decoded.skipped)
    }
    @Test fun decodeRejectsFilesWithoutValidFunctions() {
        listOf("not json","{}","""{"format":"other"}""").forEach {text->
            val error=runCatching {FunctionTransfer.decode(text)}.exceptionOrNull()
            assertTrue(text,error is IllegalArgumentException)
        }
    }
    @Test fun definitionValidatesNamesParametersAndFormula() {
        assertEquals("x+1",FunctionTransfer.definition("f","x","x+1").source)
        assertTrue(runCatching {FunctionTransfer.definition("cos","x","x")}.isFailure)
        assertTrue(runCatching {FunctionTransfer.definition("f","x,x","x")}.isFailure)
        assertTrue(runCatching {FunctionTransfer.definition("f","x","x+")}.isFailure)
    }
}
