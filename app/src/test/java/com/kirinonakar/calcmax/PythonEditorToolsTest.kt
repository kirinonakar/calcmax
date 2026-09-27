package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.PythonEditorTools
import com.kirinonakar.calcmax.ui.PythonEdit
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
    @Test fun catalogAddsOneTopImportAndPythonNames() {
        val first=PythonEditorTools.insertCatalog("print()",6,6,"mean([])",6)
        assertEquals("import calcmax_catalog as calc\nfrom calcmax_catalog import x, y, z, t, pi\nprint(calc.mean([]))",first.source)
        assertEquals("[]",first.source.substring(first.cursor-1,first.cursor+1))
        val second=PythonEditorTools.insertCatalog(first.source,first.cursor,first.cursor,"diff(,x)",5)
        assertEquals(1,second.source.lines().count {it=="import calcmax_catalog as calc"})
        assertEquals(1,second.source.lines().count {it=="from calcmax_catalog import x, y, z, t, pi"})
        assertTrue(second.source.contains("calc.diff(,calc.x)"))
        assertEquals(0,second.source.indexOf("import calcmax_catalog as calc"))
    }
    @Test fun catalogInEmptyFileCreatesRunnableOutputTemplate() {
        val edit=PythonEditorTools.insertCatalog("",0,0,"factorint()",10)
        assertTrue(edit.source.startsWith("import calcmax_catalog as calc\nfrom calcmax_catalog import x, y, z, t, pi\n"))
        assertTrue(edit.source.endsWith("print(calc.factorint())"))
        assertEquals(')',edit.source[edit.cursor])
    }
    @Test fun existingImportIsMovedToTopWithoutDuplicates() {
        val source="print(1)\nimport calcmax_catalog as calc\nimport calcmax_catalog as calc\n"
        val edit=PythonEditorTools.insertImport(source,7,"import calcmax_catalog as calc")
        assertEquals("import calcmax_catalog as calc\nprint(1)\n",edit.source)
        assertEquals("import calcmax_catalog as calc\nprint(1",edit.source.substring(0,edit.cursor))
    }
    @Test fun functionMenuInsertsBuiltinsAndImportsSympyOnce() {
        val input=PythonEditorTools.snippets.single { it.label=="input" }
        val print=PythonEditorTools.snippets.single { it.label=="print" }
        val numeric=PythonEditorTools.snippets.single { it.label=="sp.N" }
        assertEquals(PythonEdit("input()",6),PythonEditorTools.insertSnippet("",0,0,input))
        assertEquals(PythonEdit("print()",6),PythonEditorTools.insertSnippet("",0,0,print))
        val first=PythonEditorTools.insertSnippet("",0,0,numeric)
        assertEquals("import sympy as sp\nsp.N()",first.source)
        assertEquals(first.source.indexOf(')'),first.cursor)
        val second=PythonEditorTools.insertSnippet(first.source,first.cursor,first.cursor,numeric)
        assertEquals(1,second.source.lines().count {it=="import sympy as sp"})
        assertEquals("import sympy as sp\nsp.N(sp.N())",second.source)
    }
}
