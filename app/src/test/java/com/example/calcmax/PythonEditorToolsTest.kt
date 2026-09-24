package com.example.calcmax

import com.example.calcmax.ui.PythonEditorTools
import org.junit.Assert.*
import org.junit.Test

class PythonEditorToolsTest {
    @Test fun importsDoNotDuplicateAndPreserveCursor() {
        val inserted=PythonEditorTools.insertImport("print(1)",3,"import math")
        assertEquals("import math\nprint(1)",inserted.source)
        assertEquals(15,inserted.cursor)
        assertEquals(inserted,PythonEditorTools.insertImport(inserted.source,inserted.cursor,"import math"))
    }
    @Test fun completionUsesCurrentWordAndDefinedNames() {
        val source="def calculate(value):\n    return value\ncalc"
        assertTrue(PythonEditorTools.completions(source,source.length).contains("calculate"))
        assertEquals("def calculate(value):\n    return value\ncalculate",PythonEditorTools.replace(source,source.length-4,source.length,"calculate").source)
    }
    @Test fun moduleCompletionSuppliesImport() {
        val source="print(math.sq)"
        assertTrue(PythonEditorTools.completions(source,source.indexOf(')')).contains("sqrt"))
        assertEquals("import math",PythonEditorTools.requiredImport(source,source.indexOf("sq"),"sqrt"))
    }
}
