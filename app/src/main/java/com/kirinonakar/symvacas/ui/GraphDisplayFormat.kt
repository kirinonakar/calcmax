package com.kirinonakar.symvacas.ui

import com.kirinonakar.symvacas.calculator.ResultDisplayMode

/** Round only the visible text. Numeric drafts, slider values and engine inputs stay intact. */
internal fun graphDisplayNumber(value:String,displayDigits:Int):String =
    ResultDisplayFormat.formatText(value,ResultDisplayMode.OFF,false,maxFractionDigits=displayDigits)
        .replace("×10^","e")

internal fun graphDisplayNumber(value:Double,displayDigits:Int):String =
    if(value.isFinite())graphDisplayNumber(value.toString(),displayDigits) else "—"
