package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kirinonakar.calcmax.ui.theme.LocalInstrument

@Composable private fun CompactSliderThumb() {
    val c=LocalInstrument.current
    Box(Modifier.size(18.dp).background(c.accent,CircleShape))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CompactSlider(
    value:Float,
    onValueChange:(Float)->Unit,
    modifier:Modifier=Modifier,
    valueRange:ClosedFloatingPointRange<Float> = 0f..1f,
    steps:Int=0,
    onValueChangeFinished:(()->Unit)?=null
) {
    val c=LocalInstrument.current
    val colors=SliderDefaults.colors(activeTrackColor=c.accent,inactiveTrackColor=c.muted.copy(alpha=.3f))
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Slider(value,onValueChange,modifier.height(36.dp),valueRange=valueRange,steps=steps,onValueChangeFinished=onValueChangeFinished,
            colors=colors,thumb={CompactSliderThumb()},track={state->
                SliderDefaults.Track(sliderState=state,modifier=Modifier.height(4.dp),colors=colors,drawStopIndicator=null,drawTick={_,_->},thumbTrackGapSize=0.dp,trackInsideCornerSize=0.dp)
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CompactRangeSlider(
    value:ClosedFloatingPointRange<Float>,
    onValueChange:(ClosedFloatingPointRange<Float>)->Unit,
    modifier:Modifier=Modifier
) {
    val c=LocalInstrument.current
    val colors=SliderDefaults.colors(activeTrackColor=c.accent,inactiveTrackColor=c.muted.copy(alpha=.3f))
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        RangeSlider(value,onValueChange,modifier.height(36.dp),colors=colors,
            startThumb={CompactSliderThumb()},endThumb={CompactSliderThumb()},track={state->
                SliderDefaults.Track(rangeSliderState=state,modifier=Modifier.height(4.dp),colors=colors,drawStopIndicator=null,thumbTrackGapSize=0.dp,trackInsideCornerSize=0.dp)
            })
    }
}
