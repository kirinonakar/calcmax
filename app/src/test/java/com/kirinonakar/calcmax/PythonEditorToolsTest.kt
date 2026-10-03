package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.PythonEditorTools
import com.kirinonakar.calcmax.ui.PythonEdit
import org.junit.Assert.*
import org.junit.Test

class PythonEditorToolsTest {
    @Test fun inputCompletionWorksWithoutAnExistingInputCall() {
        for(prefix in listOf("in","inp","inpu")) {
            assertTrue(prefix,PythonEditorTools.completions(prefix,prefix.length).contains("input"))
            assertEquals(PythonEdit("input",5),PythonEditorTools.complete(prefix,prefix.length,"input"))
        }
        val source="name = inp('Name: ')"
        val cursor=source.indexOf('(')
        assertTrue(PythonEditorTools.completions(source,cursor).contains("input"))
        assertEquals(PythonEdit("name = input('Name: ')",12),PythonEditorTools.complete(source,cursor,"input"))
        assertNull(PythonEditorTools.requiredImport("inp",0,"input"))
    }
    @Test fun backspaceRemovesIndentationToThePreviousFourColumnBoundary() {
        for(spaces in 1..12) {
            val remaining=(spaces-1)/4*4
            val source="if ready:\n"+" ".repeat(spaces)+"print(value)"
            assertEquals(PythonEdit("if ready:\n"+" ".repeat(remaining)+"print(value)",10+remaining),PythonEditorTools.backspace(source,10+spaces))
        }
        var edit=PythonEdit("            ",12)
        for(remaining in listOf(8,4,0)) {
            edit=PythonEditorTools.backspace(edit.source,edit.cursor)!!
            assertEquals(PythonEdit(" ".repeat(remaining),remaining),edit)
        }
    }
    @Test fun backspaceWithinIndentKeepsFollowingSpacesAndCode() {
        assertEquals(PythonEdit("    print(value)",0),PythonEditorTools.backspace("        print(value)",4))
        assertEquals(PythonEdit("    value",4),PythonEditorTools.backspace("      value",6))
    }
    @Test fun backspaceSupportsTabAndMixedIndentation() {
        assertEquals(PythonEdit("value",0),PythonEditorTools.backspace("\tvalue",1))
        assertEquals(PythonEdit("\tvalue",1),PythonEditorTools.backspace("\t    value",5))
        assertEquals(PythonEdit("\tvalue",1),PythonEditorTools.backspace("\t  value",3))
        assertEquals(PythonEdit("  value",2),PythonEditorTools.backspace("  \tvalue",3))
    }
    @Test fun backspaceLeavesOrdinaryTextSelectionsAndLineBreaksToTheEditor() {
        assertNull(PythonEditorTools.backspace("value    ",9))
        assertNull(PythonEditorTools.backspace("    value",5))
        assertNull(PythonEditorTools.backspace("    value",0,4))
        assertNull(PythonEditorTools.backspace("    value",4,0))
        assertNull(PythonEditorTools.backspace("if ready:\n",10))
        assertNull(PythonEditorTools.backspace("",0))
    }
    @Test fun imeBackspaceRemovesOneIndentLevelAndRejectsOtherChanges() {
        val source="if ready:\n        print(value)"
        assertEquals(PythonEdit("if ready:\n    print(value)",14),PythonEditorTools.typedBackspace(source,18,18,source.removeRange(17,18),17))
        val auto=PythonEditorTools.newline("if ready:",9)
        assertEquals(PythonEdit("if ready:\n",10),PythonEditorTools.typedBackspace(auto.source,auto.cursor,auto.cursor,auto.source.dropLast(1),auto.cursor-1))
        assertNull(PythonEditorTools.typedBackspace(source,18,18,source.removeRange(18,19),18))
        assertNull(PythonEditorTools.typedBackspace(source,18,18,source.removeRange(14,18),14))
        assertNull(PythonEditorTools.typedBackspace(source,17,18,source.removeRange(17,18),17))
        assertNull(PythonEditorTools.typedBackspace(source,18,18,source,17))
    }
    @Test fun indentCurrentLinePreservesCaretAndOtherLines() {
        val source="first\nsecond\nthird"
        val edit=PythonEditorTools.indent(source,8)
        assertEquals(PythonEdit("first\n    second\nthird",12),edit)
        assertEquals(PythonEdit(source,8),PythonEditorTools.indent(edit.source,edit.cursor,outdent=true))
        assertEquals(PythonEdit("    ",4),PythonEditorTools.indent("",0))
        assertEquals(PythonEdit("first\n    ",10),PythonEditorTools.indent("first\n",6))
    }
    @Test fun multilineIndentKeepsSelectionAndExcludesTheNextLine() {
        val source="one\ntwo\nthree"
        val edit=PythonEditorTools.indent(source,0,8)
        assertEquals(PythonEdit("    one\n    two\nthree",4,16),edit)
        assertEquals(PythonEdit(source,0,8),PythonEditorTools.indent(edit.source,edit.cursor,edit.selectionEnd,outdent=true))
        assertEquals(PythonEdit(edit.source,16,4),PythonEditorTools.indent(source,8,0))
    }
    @Test fun outdentHandlesTabsShortIndentAndCaretInsideIndent() {
        val source="\tfirst\n  second\nplain"
        assertEquals(PythonEdit("first\nsecond\nplain",0,18),PythonEditorTools.indent(source,0,source.length,outdent=true))
        assertEquals(PythonEdit("value",0),PythonEditorTools.indent("    value",2,outdent=true))
        assertEquals(PythonEdit("    value",2),PythonEditorTools.indent("        value",6,outdent=true))
    }
    @Test fun tabInsertsSpacesAtCaretAndIndentsSelections() {
        assertEquals(PythonEdit("ab    cd",6),PythonEditorTools.tab("abcd",2))
        assertEquals(PythonEdit("    one\n    two",5,14),PythonEditorTools.tab("one\ntwo",1,6))
        assertEquals(PythonEdit("one",1),PythonEditorTools.tab("    one",5,shift=true))
    }
    @Test fun newlineKeepsIndentAndAddsFourSpacesAfterColon() {
        for(source in listOf("if ready:","    for item in items:","\tdef function():")) {
            val prefix=source.takeWhile {it==' ' || it=='\t'}+"    "
            val expected=source+"\n"+prefix
            assertEquals(PythonEdit(expected,expected.length),PythonEditorTools.newline(source,source.length))
        }
        val source="    print(value)"
        assertEquals(PythonEdit(source+"\n    ",source.length+5),PythonEditorTools.newline(source,source.length))
        assertEquals(PythonEdit("\n",1),PythonEditorTools.newline("",0))
        assertEquals(PythonEdit("    \n    value",9),PythonEditorTools.newline("    value",4))
    }
    @Test fun typedNewlineHandlesSelectionButDoesNotReindentPastedCode() {
        val source="if ready:\n    pass"
        assertEquals(PythonEdit("if ready:\n    ",14),PythonEditorTools.typedNewline(source,9,source.length,"if ready:\n",10))
        assertEquals(PythonEditorTools.newline(source,source.length),PythonEditorTools.typedNewline(source,source.length,source.length,source+"\n",source.length+1))
        assertNull(PythonEditorTools.typedNewline(source,source.length,source.length,source+"\nprint(1)",source.length+9))
        assertNull(PythonEditorTools.typedNewline(source,0,0,source,0))
    }
    @Test fun completionReplacesWholeWordAndPreservesFollowingCode() {
        assertEquals(PythonEdit("calculate(value)",9),PythonEditorTools.complete("calxyz(value)",3,"calculate"))
        val source="print(math.sq)"
        val edit=PythonEditorTools.complete(source,source.indexOf(')'),"sqrt")
        assertEquals("import math\nprint(math.sqrt)",edit.source)
        assertEquals(')',edit.source[edit.cursor])
        val existing="import math\nprint(math.sq)"
        assertEquals("import math\nprint(math.sqrt)",PythonEditorTools.complete(existing,existing.indexOf(')'),"sqrt").source)
    }
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
