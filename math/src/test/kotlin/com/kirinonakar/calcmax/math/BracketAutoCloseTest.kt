package com.kirinonakar.calcmax.math
import org.junit.Assert.*
import org.junit.Test
class BracketAutoCloseTest {
    @Test fun openerInsertsTheMatchingCloserAndKeepsTheCaretInside() {
        assertEquals(BracketEdit("print()",6),BracketAutoClose.typed("print",5,"print(",6))
        assertEquals(BracketEdit("a()b",2),BracketAutoClose.typed("ab",1,"a(b",2))
        assertEquals(BracketEdit("{}",1),BracketAutoClose.typed("",0,"{",1))
        assertEquals(BracketEdit("a[]b",2),BracketAutoClose.typed("ab",1,"a[b",2))
    }
    @Test fun closerSkipsTheBracketAlreadyAtTheCaret() {
        assertEquals(BracketEdit("sin(x)",6),BracketAutoClose.typed("sin(x)",5,"sin(x))",6))
    }
    @Test fun otherEditsAreLeftUntouched() {
        assertNull(BracketAutoClose.typed("print",5,"print+",6))
        assertNull(BracketAutoClose.typed("print",5,"print(",3))
        assertNull(BracketAutoClose.typed("ab",1,"a)b",2))
        assertNull(BracketAutoClose.typed("print",2,"print()",5))
    }
}
