package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.ui.theme.LocalInstrument

val UnitGroups=linkedMapOf(
    "Length" to listOf("m","km","cm","mm","in","ft","yd","mi"),
    "Area" to listOf("m2","cm2","km2","ha","acre"),
    "Volume" to listOf("L","mL","m3","galUS"),
    "Mass" to listOf("kg","g","mg","lb","oz"),
    "Temperature" to listOf("degC","degF","K"),
    "Speed" to listOf("mps","kph","mph","knot"),
    "Acceleration" to listOf("mps2","g0"),
    "Pressure" to listOf("Pa","kPa","bar","atm"),
    "Force" to listOf("N","kN","lbf"),
    "Energy" to listOf("J","kJ","cal","kWh","eV"),
    "Power" to listOf("W","kW"),
    "Time" to listOf("s","min","h","day","ms"),
    "Frequency" to listOf("Hz","kHz","MHz"),
    "Angle" to listOf("rad","deg","grad"),
    "Data" to listOf("bit","byte","kB","KiB","MB","MiB","GB"),
    "Current" to listOf("A","mA","uA"),
    "Charge" to listOf("C","mC","uC"),
    "Voltage" to listOf("V","mV","kV"),
    "Resistance" to listOf("ohm","kohm","Mohm","Ω"),
    "Conductance" to listOf("S","mS"),
    "Capacitance" to listOf("F","uF","nF","pF"),
    "Inductance" to listOf("H","mH","uH"),
    "Magnetic flux" to listOf("Wb","Vs"),
    "Magnetic flux density" to listOf("T","mT","uT"),
    "Amount" to listOf("mol","mmol","umol")
)
@Composable fun UnitsScreen(m: CalculatorModel) {
    var group by rememberSaveable { mutableStateOf("Length") }; var from by rememberSaveable { mutableStateOf("m") }; var to by rememberSaveable { mutableStateOf("ft") }; var value by rememberSaveable { mutableStateOf("1") }
    Panel("Unit conversion","") {
        Choices(UnitGroups.keys.toList(),group,{group=it;from=UnitGroups[it]!![0];to=UnitGroups[it]!![1]},translate=false)
        Field(value,"Value or expression",Modifier.fillMaxWidth()) { value=it }
        Text("From"); Choices(UnitGroups[group]!!,from,{from=it},translate=false)
        Text("To"); Choices(UnitGroups[group]!!,to,{to=it},translate=false)
        Row { Button(onClick={m.edit(Editor("convert($value,$from,$to)"));m.calculate()}) { Text("Convert") }; SmallAction("Swap",translate=false) { val temp=from;from=to;to=temp } }
        Display(m)
        Text("Units in expressions: qty(2,m) + qty(30,cm). Convert a derived quantity with convert(qty(1,kg)*qty(2,mps2),N).",fontSize=11.sp)
    }
}

data class ConstantEntry(val symbol: String,val name: String,val value: String,val unit: String,val exact: Boolean=true)
@Composable fun ConstantsScreen(m: CalculatorModel) {
    var search by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {m.loadConstants()}
    val constants=remember(m.constants) {(0 until (m.constants?.length() ?: 0)).map {i->m.constants!!.getJSONObject(i).let {ConstantEntry(it.getString("symbol"),it.getString("name"),it.getString("value"),it.getString("unit"),it.getBoolean("exact"))}}}
    Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(tr("Scientific constants"),style=MaterialTheme.typography.titleLarge)
        Text(if(isKorean())"SI 정의 상수 및 CODATA 2022 참조값입니다. 누르면 수식에 넣습니다." else "SI defining constants and CODATA 2022 reference values. Tap to insert.",color=LocalInstrument.current.muted,fontSize=12.sp)
        OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth().keepInputVisible(),label={Text(tr("Search"))},singleLine=true,
            trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""}){Text("\u2715",fontSize=15.sp)}})
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(m.constants==null) Text(if(isKorean())"상수를 불러오는 중…" else "Loading local constants…")
            constants.filter { it.name.contains(search,true)||it.symbol.contains(search,true) }.forEach { item ->
                Column(Modifier.fillMaxWidth().clickable { m.insert(item.symbol);m.mode="Scientific/CAS" }.padding(vertical=8.dp)) {
                    Text("${item.symbol}  ·  ${item.name}",style=MaterialTheme.typography.titleSmall)
                    Text("${item.value} ${item.unit}",fontSize=14.sp)
                    Text(if(item.exact) {if(isKorean())"정확한 정의값" else "Exact definition"} else {if(isKorean())"측정값 · CODATA 2022" else "Measured · CODATA 2022"},fontSize=11.sp,color=LocalInstrument.current.muted)
                }; HorizontalDivider()
            }
            Text(if(isKorean())"출처: NIST physics.nist.gov/cuu/Constants · 측정값은 발표된 정밀도를 유지하며 정확한 물리량이 아닙니다." else "Source: NIST physics.nist.gov/cuu/Constants · Measured values retain their published precision; they are not exact physical quantities.",fontSize=11.sp)
        }
    }
}
