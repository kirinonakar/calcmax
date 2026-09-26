package com.example.calcmax.math
import org.junit.Assert.*
import org.junit.Test
class TypedParensTest {
    @Test fun markRecordsThePairAroundTheCaret() {
        assertEquals(listOf(2..4),TypedParens.mark(emptyList(),"5×()",3))
        assertEquals(listOf(2..4),TypedParens.mark(emptyList(),"5×()",4))
        assertEquals(emptyList<IntRange>(),TypedParens.mark(emptyList(),"5×(",3))
        assertEquals(emptyList<IntRange>(),TypedParens.mark(emptyList(),"(x)",2))
    }
    @Test fun shiftKeepsPairsAlignedAroundEdits() {
        assertEquals(listOf(3..5),TypedParens.shift(listOf(2..4),"5×()","5×3()"))
        assertEquals(listOf(2..4),TypedParens.shift(listOf(2..4),"5×()","5×()3"))
    }
    @Test fun shiftDropsPairsThatWereEditedOrLost() {
        assertEquals(emptyList<IntRange>(),TypedParens.shift(listOf(2..4),"5×()","5×(3)"))
        assertEquals(emptyList<IntRange>(),TypedParens.shift(listOf(2..4),"5×()","abc"))
    }
}
