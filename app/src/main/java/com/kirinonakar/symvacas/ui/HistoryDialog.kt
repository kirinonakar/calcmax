package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import java.text.DateFormat
import java.util.Date

@Composable fun HistoryDialog(m: CalculatorModel,close: ()->Unit) {
    val clipboard=LocalClipboardManager.current
    var favorites by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val needle=search.trim()
    val entries=m.history.filter { entry->(!favorites||entry.favorite)&&(needle.isBlank()||entry.source.contains(needle,true)||entry.exact.contains(needle,true)) }
    AlertDialog(onDismissRequest=close,title={Text(tr("Calculation history"))},text={Column(Modifier.fillMaxWidth().heightIn(max=480.dp)) {
        OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),label={Text(tr("Search history"))},singleLine=true,
            trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""}){Text("\u2715",fontSize=15.sp)}})
        Choices(listOf("All","Favorites"),if(favorites)"Favorites" else "All",{favorites=it=="Favorites"})
        LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).testTag("history-list")) {
            if(entries.isEmpty())item {Text(if(isKorean()) {if(needle.isNotBlank())"\"$needle\"에 맞는 계산이 없습니다." else if(favorites)"즐겨찾기가 없습니다." else "계산 기록이 여기에 표시됩니다."} else if(needle.isNotBlank())"No calculations match \"$needle\"." else if(favorites)"No favorites yet." else "Your calculations will appear here.")}
            items(entries) { entry ->
                val preview=remember(entry) {entry.mathPreview()}
                val input=preview.input
                val response=preview.response
                Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        if(input!=null)MathNode(input,13f)
                        else Text(entry.source.historyPreview(),fontFamily=FontFamily.Monospace,fontSize=13.sp)
                    }
                    Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        MathText("= ",20f)
                        // ResultMath wraps and scrolls its own parts; keep its viewport bounded.
                        Box(Modifier.weight(1f)) {
                            if(response!=null)
                                ResultMath(response,m.decimal,20f,displayMode=m.resultDisplayMode,thousandsSeparator=m.thousandsSeparator,displayDigits=m.displayDigits)
                            else Text((if(m.decimal)entry.decimal else entry.exact).historyPreview(),fontFamily=FontFamily.Serif,fontSize=20.sp)
                        }
                    }
                    Text("${entry.mode} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.id))}",fontSize=10.sp,color=LocalInstrument.current.muted)
                    Row { SmallAction("Reuse") { m.edit(Editor(entry.source));m.mode="Scientific/CAS";close() }; SmallAction(if(entry.favorite)"★" else "☆") { m.favorite(entry.id) }; SmallAction("Copy") { clipboard.setText(AnnotatedString(entry.exact)) }; SmallAction("Delete") { m.deleteHistory(entry.id) } }
                }
                HorizontalDivider()
            }
        }
    }},confirmButton={TextButton(onClick=close) { Text(tr("Done")) }},dismissButton={TextButton(onClick={m.clearHistory()}) { Text(tr("Clear all")) }})
}
