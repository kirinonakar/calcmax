package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import java.util.Locale
import kotlin.math.roundToInt

@Composable internal fun SurfaceColorPickerDialog(
    initialColor:String,
    onDismiss:()->Unit,
    onConfirm:(String)->Unit
) {
    val c=LocalInstrument.current
    val korean=isKorean()
    val initialHsl=remember(initialColor) {
        FloatArray(3).also {ColorUtils.colorToHSL(android.graphics.Color.parseColor(initialColor),it)}
    }
    var hue by rememberSaveable {mutableFloatStateOf(initialHsl[0])}
    var saturation by rememberSaveable {mutableFloatStateOf(initialHsl[1])}
    var lightness by rememberSaveable {mutableFloatStateOf(initialHsl[2])}
    fun color(h:Float=hue,s:Float=saturation,l:Float=lightness)=ColorUtils.HSLToColor(floatArrayOf(h,s,l))
    val selectedColor=color()
    val hex=String.format(Locale.ROOT,"#%06x",selectedColor and 0xffffff)
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(if(korean)"표면 색상 · HSL" else "Surface color · HSL")},
        text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(Color(selectedColor),RoundedCornerShape(8.dp)).border(1.dp,c.grid,RoundedCornerShape(8.dp)))
                Text(hex.uppercase(Locale.ROOT),color=c.ink)
            }
            HslColorSlider(if(korean)"색조 (H)" else "Hue (H)","${hue.roundToInt()}°",hue,0f..360f,
                (0..6).map {Color(color(h=it*60f,s=1f,l=.5f))}) {hue=it}
            HslColorSlider(if(korean)"채도 (S)" else "Saturation (S)","${(saturation*100).roundToInt()}%",saturation,0f..1f,
                listOf(Color(color(s=0f)),Color(color(s=1f)))) {saturation=it}
            HslColorSlider(if(korean)"명도 (L)" else "Lightness (L)","${(lightness*100).roundToInt()}%",lightness,0f..1f,
                listOf(Color.Black,Color(color(l=.5f)),Color.White)) {lightness=it}
        }},
        confirmButton={TextButton(onClick={onConfirm(hex)}) {Text(if(korean)"적용" else "Apply")}},
        dismissButton={TextButton(onClick=onDismiss) {Text(tr("Cancel"))}}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HslColorSlider(
    label:String,
    formattedValue:String,
    value:Float,
    range:ClosedFloatingPointRange<Float>,
    colors:List<Color>,
    onValueChange:(Float)->Unit
) {
    val c=LocalInstrument.current
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(label,color=c.ink)
            Text(formattedValue,color=c.muted)
        }
        Slider(value,onValueChange,Modifier.fillMaxWidth().semantics {contentDescription=label},valueRange=range,
            thumb={Box(Modifier.size(24.dp).background(c.body,CircleShape).border(2.dp,c.ink,CircleShape))},
            track={Box(Modifier.fillMaxWidth().height(12.dp).background(Brush.horizontalGradient(colors),CircleShape).border(1.dp,c.grid,CircleShape))})
    }
}
