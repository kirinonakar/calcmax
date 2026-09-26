package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.calculator.FunctionTransfer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FunctionTransferTest {
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
