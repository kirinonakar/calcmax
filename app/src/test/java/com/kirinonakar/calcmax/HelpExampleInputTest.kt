package com.kirinonakar.calcmax

import com.kirinonakar.calcmax.ui.helpExampleInput
import org.junit.Assert.assertEquals
import org.junit.Test

class HelpExampleInputTest {
    @Test fun extractsOnlyTheRunnableExpression() {
        assertEquals("dsolve(diff(y(t),t)=y(t),y(t),t)",helpExampleInput("dsolve(diff(y(t),t)=y(t),y(t),t)"))
        assertEquals("round(2.5)",helpExampleInput("round(2.5) → 2 (banker's rounding)"))
        assertEquals("sin(pi/6)",helpExampleInput("`sin(pi/6)`"))
    }
}
