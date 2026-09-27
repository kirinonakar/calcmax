package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.calculator.DisplayShortcut
import com.kirinonakar.calcmax.ui.theme.LocalInstrument

internal fun catalogCursor(source:String):Int = source.indexOf("[]").takeIf {it>=0}?.plus(1)
    ?: source.indexOf('(').takeIf {it>=0}?.plus(1)
    ?: source.length

internal fun runDisplayShortcut(m:CalculatorModel,shortcut:DisplayShortcut,open:(String)->Unit) {
    if(shortcut.source=="catalog")m.insert(shortcut.input,catalogCursor(shortcut.input))
    else performKeypadInput(m,shortcut.input,open)
}

@Composable fun DisplayShortcutsDialog(m:CalculatorModel,close:()->Unit) {
    var selected by remember {mutableIntStateOf(0)}
    var source by remember {mutableStateOf("Keypad")}
    var category by remember {mutableStateOf("Main keys")}
    var search by remember {mutableStateOf("")}
    val catalog=catalogCategories(m)
    val groups=if(source=="Keypad")KeypadShortcutGroups else catalog.mapValues {(_,items)->items.map {DisplayShortcut(it.substringBefore('('),it,"catalog")} }
    LaunchedEffect(source) {category=if(source=="Keypad")"Main keys" else "Scientific"}
    AlertDialog(onDismissRequest=close,title={Text(tr("Customize display buttons"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=550.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(if(isKorean())"scr과 Share 사이에 버튼을 최대 6개 선택하세요." else "Choose up to 6 buttons between scr and Share.",fontSize=12.sp,color=LocalInstrument.current.muted)
            Column(Modifier.fillMaxWidth().heightIn(max=190.dp).verticalScroll(rememberScrollState())) {
                m.displayShortcuts.forEachIndexed {index,item->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(onClick={selected=index},modifier=Modifier.weight(1f)) {
                            Text("${index+1}. ${item.label}",fontSize=12.sp,maxLines=1,color=if(selected==index)LocalInstrument.current.accent else LocalInstrument.current.ink)
                        }
                        IconButton(onClick={m.removeDisplayShortcut(index);selected=selected.coerceAtMost(m.displayShortcuts.size)},
                            modifier=Modifier.semantics { contentDescription="Remove ${item.label}" }) {Text("\u2715",fontSize=15.sp)}
                    }
                }
                if(m.displayShortcuts.size<6)TextButton(onClick={selected=m.displayShortcuts.size}) {Text(tr("+ Add button"),fontSize=12.sp)}
            }
            Text(if(selected==m.displayShortcuts.size)"Select a button to add" else "Replace button ${selected+1}",fontSize=12.sp)
            Choices(listOf("Keypad","Catalog"),source,{source=it})
            OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),label={Text(tr("Find button or function"))},singleLine=true,
                trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""}){Text("\u2715",fontSize=15.sp)}})
            Choices(groups.keys.toList(),category,{category=it})
            val entries=if(search.isBlank())groups[category].orEmpty() else groups.values.flatten().filter {it.label.contains(search,true)||it.input.contains(search,true)}
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                if(entries.isEmpty())Text(tr("No matching buttons"),fontSize=12.sp)
                entries.chunked(2).forEach {row->Row(Modifier.fillMaxWidth()) {
                    row.forEach {item->TextButton(onClick={
                        m.setDisplayShortcut(selected,item)
                        selected=selected.coerceAtMost(m.displayShortcuts.lastIndex)
                    },modifier=Modifier.weight(1f)) {Text(if(source=="Catalog")item.input else item.label,fontSize=11.sp,maxLines=2)} }
                    if(row.size==1)Spacer(Modifier.weight(1f))
                }}
            }
        }
    },confirmButton={TextButton(onClick=close){Text(tr("Done"))}},dismissButton={TextButton(onClick={m.resetDisplayShortcuts();selected=0}){Text(tr("Restore defaults"))}})
}
