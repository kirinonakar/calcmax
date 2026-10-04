package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.ui.theme.LocalInstrument

@Composable fun ProgrammerScreen(m: CalculatorModel) {
    var a by rememberSaveable { mutableStateOf("255") }; var b by rememberSaveable { mutableStateOf("15") }
    var base by rememberSaveable { mutableIntStateOf(10) }; var width by rememberSaveable { mutableIntStateOf(32) }; var signed by rememberSaveable { mutableStateOf(false) }
    Panel("Programmer","Fixed-width integers · two’s complement · exact bit operations") {
        Choices(listOf("2","8","10","16"),base.toString(),{base=it.toInt()})
        Choices(listOf("8","16","32","64"),width.toString(),{width=it.toInt()})
        Row(verticalAlignment=Alignment.CenterVertically) { Switch(signed,{signed=it}); Text("  "+tr("Signed interpretation")) }
        Field(a,"A · base $base",Modifier.fillMaxWidth()) { a=it }
        Field(b,"B / shift count · base $base",Modifier.fillMaxWidth()) { b=it }
        listOf("","AND","OR","XOR","NOT","NAND","NOR","<<",">>").chunked(3).forEach { row->Row { row.forEach { op->SmallAction(op.ifBlank { "Convert" }) { m.program(a,b,base,width,signed,op) } } } }
        m.result?.optJSONObject("bases")?.let { bases -> listOf("BIN","OCT","DEC","HEX").forEach { key->Text(key,color=LocalInstrument.current.muted,fontSize=11.sp); Text(bases.optString(key),fontFamily=FontFamily.Monospace,fontSize=18.sp) } }
        if(m.busy) Text(if(isKorean())"계산 중…" else "Computing…")
    }
}
