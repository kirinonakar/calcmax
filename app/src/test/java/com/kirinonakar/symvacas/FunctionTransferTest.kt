package com.kirinonakar.symvacas

import com.kirinonakar.symvacas.calculator.FunctionTransfer
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
        assertTrue(encoded.contains("symvacas.functions"))
        val decoded=FunctionTransfer.decode(encoded)
        assertEquals(0,decoded.skipped)
        assertEquals(listOf("f","g"),decoded.definitions.map {it.name})
        assertEquals(listOf("x","y"),decoded.definitions[1].parameters)
        assertEquals("x+y",decoded.definitions[1].source)
    }
    @Test fun decodeSkipsReservedInvalidAndUnparsableEntries() {
        val decoded=FunctionTransfer.decode("""{"format":"symvacas.functions","version":1,"functions":{"sin":{"parameters":["x"],"source":"x"},"ok":{"parameters":["x"],"source":"x+1"},"bad":{"parameters":[],"source":"x"},"broken":{"parameters":["x"],"source":"x+"}}}""")
        assertEquals(listOf("ok"),decoded.definitions.map {it.name})
        assertEquals(3,decoded.skipped)
    }
}
