package com.kirinonakar.symvacas.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable internal fun delayedCalculationStatus(active:Boolean,requestKey:Any):Boolean {
    var visible by remember(active,requestKey){mutableStateOf(false)}
    LaunchedEffect(active,requestKey) {
        if(active){delay(1000);visible=true}
    }
    return active&&visible
}

@Composable internal fun CalculationButton(
    label:String,active:Boolean,requestKey:Any,enabled:Boolean=true,
    modifier:Modifier=Modifier,onCancel:()->Unit,onClick:()->Unit
) {
    val cancel=delayedCalculationStatus(active,requestKey)
    Button(onClick={if(cancel)onCancel()else onClick()},
        enabled=cancel||(!active&&enabled),modifier=modifier) {
        Text(tr(if(cancel)"Cancel" else label))
    }
}
