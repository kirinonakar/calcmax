package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import java.util.Locale
import kotlin.math.roundToInt

@Composable internal fun GraphColorSettings(m:CalculatorModel) {
    val c=LocalInstrument.current
    val colorLabel=tr("Graph color")
    var selectedIndex by rememberSaveable {mutableIntStateOf(0)}
    Text(tr("Graph colors"))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        repeat(6) {index->
            val color=m.graphColors[index]?.let {Color(android.graphics.Color.parseColor(it))} ?: c.curves[index]
            Column(Modifier.weight(1f).border(if(index==selectedIndex)2.dp else 1.dp,if(index==selectedIndex)c.accent else c.grid,RoundedCornerShape(6.dp))
                .clickable {selectedIndex=index}.semantics {contentDescription="$colorLabel ${index+1}";selected=index==selectedIndex}
                .padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.size(22.dp,8.dp).background(color,RoundedCornerShape(4.dp)))
                Text("f${index+1}")
            }
        }
    }
    key(selectedIndex,c.curves) {
        val initial=m.graphColors[selectedIndex]?.let {android.graphics.Color.parseColor(it)} ?: c.curves[selectedIndex].toArgb()
        val initialHsl=remember {FloatArray(3).also {ColorUtils.colorToHSL(initial,it)}}
        var hue by remember {mutableFloatStateOf(initialHsl[0])}
        var saturation by remember {mutableFloatStateOf(initialHsl[1])}
        var lightness by remember {mutableFloatStateOf(initialHsl[2])}
        fun color(h:Float=hue,s:Float=saturation,l:Float=lightness)=ColorUtils.HSLToColor(floatArrayOf(h,s,l))
        fun saveColor(){m.setGraphColor(selectedIndex,String.format(Locale.ROOT,"#%06x",color() and 0xffffff))}
        fun resetSliders(){
            val defaults=FloatArray(3).also {ColorUtils.colorToHSL(c.curves[selectedIndex].toArgb(),it)}
            hue=defaults[0];saturation=defaults[1];lightness=defaults[2]
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(Color(color()),RoundedCornerShape(7.dp)).border(1.dp,c.grid,RoundedCornerShape(7.dp)))
            Text(String.format(Locale.ROOT,"#%06X",color() and 0xffffff))
        }
        HslColorSlider(tr("Hue (H)"),"${hue.roundToInt()}°",hue,0f..360f,(0..6).map {Color(color(h=it*60f,s=1f,l=.5f))}) {hue=it;saveColor()}
        HslColorSlider(tr("Saturation (S)"),"${(saturation*100).roundToInt()}%",saturation,0f..1f,listOf(Color(color(s=0f)),Color(color(s=1f)))) {saturation=it;saveColor()}
        HslColorSlider(tr("Lightness (L)"),"${(lightness*100).roundToInt()}%",lightness,0f..1f,listOf(Color.Black,Color(color(l=.5f)),Color.White)) {lightness=it;saveColor()}
        Row {
            TextButton(onClick={m.setGraphColor(selectedIndex,null);resetSliders()}) {Text(tr("Reset color"))}
            TextButton(onClick={m.resetGraphColors();resetSliders()}) {Text(tr("Reset all colors"))}
        }
    }
}
