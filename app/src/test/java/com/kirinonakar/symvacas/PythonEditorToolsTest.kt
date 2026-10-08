package com.kirinonakar.symvacas

import com.kirinonakar.symvacas.ui.PythonEditorTools
import com.kirinonakar.symvacas.ui.PythonEdit
import org.junit.Assert.*
import org.junit.Test

class PythonEditorToolsTest {
    @Test fun imeBackspaceRemovesOneIndentLevelAndRejectsOtherChanges() {
        run { // imeBackspaceRemovesOneIndentLevelAndRejectsOtherChanges
            val source="if ready:\n        print(value)"
            assertEquals(PythonEdit("if ready:\n    print(value)",14),PythonEditorTools.typedBackspace(source,18,18,source.removeRange(17,18),17))
            val auto=PythonEditorTools.newline("if ready:",9)
            assertEquals(PythonEdit("if ready:\n",10),PythonEditorTools.typedBackspace(auto.source,auto.cursor,auto.cursor,auto.source.dropLast(1),auto.cursor-1))
            assertNull(PythonEditorTools.typedBackspace(source,18,18,source.removeRange(18,19),18))
            assertNull(PythonEditorTools.typedBackspace(source,18,18,source.removeRange(14,18),14))
            assertNull(PythonEditorTools.typedBackspace(source,17,18,source.removeRange(17,18),17))
            assertNull(PythonEditorTools.typedBackspace(source,18,18,source,17))
        }
        run { // outdentHandlesTabsShortIndentAndCaretInsideIndent
            val source="\tfirst\n  second\nplain"
            assertEquals(PythonEdit("first\nsecond\nplain",0,18),PythonEditorTools.indent(source,0,source.length,outdent=true))
            assertEquals(PythonEdit("value",0),PythonEditorTools.indent("    value",2,outdent=true))
            assertEquals(PythonEdit("    value",2),PythonEditorTools.indent("        value",6,outdent=true))
        }
    }
    @Test fun multilineIndentKeepsSelectionAndExcludesTheNextLine() {
        run { // multilineIndentKeepsSelectionAndExcludesTheNextLine
            val source="one\ntwo\nthree"
            val edit=PythonEditorTools.indent(source,0,8)
            assertEquals(PythonEdit("    one\n    two\nthree",4,16),edit)
            assertEquals(PythonEdit(source,0,8),PythonEditorTools.indent(edit.source,edit.cursor,edit.selectionEnd,outdent=true))
            assertEquals(PythonEdit(edit.source,16,4),PythonEditorTools.indent(source,8,0))
        }
        run { // typedNewlineHandlesSelectionButDoesNotReindentPastedCode
            val source="if ready:\n    pass"
            assertEquals(PythonEdit("if ready:\n    ",14),PythonEditorTools.typedNewline(source,9,source.length,"if ready:\n",10))
            assertEquals(PythonEditorTools.newline(source,source.length),PythonEditorTools.typedNewline(source,source.length,source.length,source+"\n",source.length+1))
            assertNull(PythonEditorTools.typedNewline(source,source.length,source.length,source+"\nprint(1)",source.length+9))
            assertNull(PythonEditorTools.typedNewline(source,0,0,source,0))
        }
    }

    @Test fun completionReplacesWholeWordAndPreservesFollowingCode() {
        run { // completionReplacesWholeWordAndPreservesFollowingCode
            assertEquals(PythonEdit("calculate(value)",9),PythonEditorTools.complete("calxyz(value)",3,"calculate"))
            val source="print(math.sq)"
            val edit=PythonEditorTools.complete(source,source.indexOf(')'),"sqrt")
            assertEquals("import math\nprint(math.sqrt)",edit.source)
            assertEquals(')',edit.source[edit.cursor])
            val existing="import math\nprint(math.sq)"
            assertEquals("import math\nprint(math.sqrt)",PythonEditorTools.complete(existing,existing.indexOf(')'),"sqrt").source)
        }
        run { // catalogAddsOneTopImportAndPythonNames
            val first=PythonEditorTools.insertCatalog("print()",6,6,"mean([])",6)
            assertEquals("import symvacas_catalog as calc\nfrom symvacas_catalog import x, y, z, t, pi\nprint(calc.mean([]))",first.source)
            assertEquals("[]",first.source.substring(first.cursor-1,first.cursor+1))
            val second=PythonEditorTools.insertCatalog(first.source,first.cursor,first.cursor,"diff(,x)",5)
            assertEquals(1,second.source.lines().count {it=="import symvacas_catalog as calc"})
            assertEquals(1,second.source.lines().count {it=="from symvacas_catalog import x, y, z, t, pi"})
            assertTrue(second.source.contains("calc.diff(,calc.x)"))
            assertEquals(0,second.source.indexOf("import symvacas_catalog as calc"))
            val domain=PythonEditorTools.insertCatalog("",0,0,"solve(,x,real)",6)
            assertTrue(domain.source.contains("calc.solve(,calc.x,calc.real)"))
        }
    }

}
