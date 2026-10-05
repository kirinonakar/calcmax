package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.ui.theme.LocalInstrument

@Composable fun Panel(title: String,subtitle: String,scrollState: ScrollState?=null,content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(scrollState ?: rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(tr(title),style=MaterialTheme.typography.titleLarge); if(subtitle.isNotBlank())Text(tr(subtitle),color=LocalInstrument.current.muted,fontSize=12.sp); content()
    }
}
@Composable fun Choices(values: List<String>,selected: String,choose: (String)->Unit,translate:Boolean=true) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { values.forEach { value->FilterChip(selected==value,onClick={choose(value)},label={Text(if(translate)tr(value) else value,fontSize=12.sp)}) } }
}
/** Repeat relocation as the IME opens: requesting only on focus uses the old viewport. */
internal fun Modifier.keepInputVisible(includeDescendants:Boolean=false,contentKey:Any?=null):Modifier=composed {
    val requester=remember {BringIntoViewRequester()}
    var focused by remember {mutableStateOf(false)}
    val imeBottom=WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused,imeBottom,contentKey) {
        if(focused) {
            withFrameNanos {}
            requester.bringIntoView()
        }
    }
    this.bringIntoViewRequester(requester).onFocusChanged {focused=if(includeDescendants)it.hasFocus else it.isFocused}
}

@Composable fun Field(value: String,label: String,modifier: Modifier=Modifier,enabled: Boolean=true,translate:Boolean=true,onValue: (String)->Unit) { OutlinedTextField(value,onValue,modifier=modifier.keepInputVisible(),label={Text(if(translate)tr(label) else label)},singleLine=true,enabled=enabled) }

/** Whole-cell activation for editable grid cells. The inner text field consumes pointer events and its own
 *  tap handling is cancelled once a scrolling parent claims the gesture, so watch the cell container instead:
 *  only a genuine drag (moving past twice the touch slop, or a second finger) counts as a scroll. */
@Composable internal fun statCellTouch(focus:FocusRequester):Modifier {
    val keyboard by rememberUpdatedState(LocalSoftwareKeyboardController.current)
    return Modifier.pointerInput(focus) {
        val slop=viewConfiguration.touchSlop*2f
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val start=down.position
            var dragged=false
            while(true) {
                val event=awaitPointerEvent()
                if(event.changes.count {it.pressed}>1){dragged=true;break}
                val change=event.changes.firstOrNull {it.id==down.id} ?: break
                if((change.position-start).getDistance()>slop){dragged=true;break}
                if(!change.pressed)break
            }
            if(!dragged) {
                runCatching {focus.requestFocus()}
                keyboard?.show()
            }
        }
    }
}

@Composable private fun StepKey(label:String,description:String,onClick:()->Unit) {
    val c=LocalInstrument.current
    Box(Modifier.size(32.dp).border(1.dp,c.muted.copy(alpha=.4f)).clickable(onClick=onClick).semantics(mergeDescendants=true){contentDescription=description},contentAlignment=Alignment.Center){Text(label,fontSize=15.sp,color=c.ink)}
}
@Composable internal fun DimStepper(label:String,value:Int,range:IntRange,labelWidth:Dp?=null,onValue:(Int)->Unit) {
    val c=LocalInstrument.current
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,if(labelWidth==null)Modifier else Modifier.width(labelWidth),fontSize=11.sp,color=c.muted)
        StepKey("−","Decrease $label"){onValue((value-1).coerceIn(range))}
        Text("$value",Modifier.widthIn(min=20.dp),textAlign=TextAlign.Center,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        StepKey("+","Increase $label"){onValue((value+1).coerceIn(range))}
    }
}
