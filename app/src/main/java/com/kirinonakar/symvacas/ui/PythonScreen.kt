package com.kirinonakar.symvacas.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.math.BracketAutoClose
import com.kirinonakar.symvacas.ui.theme.LocalInstrument

@Composable fun PythonScreen(m:CalculatorModel) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    val c=LocalInstrument.current
    var editor by remember { mutableStateOf(TextFieldValue(m.pythonSource,selection=TextRange(m.pythonSelectionStart,m.pythonSelectionEnd))) }
    val editorFocus=remember {FocusRequester()}
    var importsOpen by remember {mutableStateOf(false)}
    var templatesOpen by remember {mutableStateOf(false)}
    var confirm by remember {mutableStateOf("")}
    var inputText by remember {mutableStateOf("")}
    LaunchedEffect(m.pythonSource,m.pythonSelectionStart,m.pythonSelectionEnd) {
        if(editor.text!=m.pythonSource || editor.selection.start!=m.pythonSelectionStart || editor.selection.end!=m.pythonSelectionEnd)
            editor=TextFieldValue(m.pythonSource,selection=TextRange(m.pythonSelectionStart,m.pythonSelectionEnd))
    }
    fun update(value:TextFieldValue) {editor=value;m.editPython(value.text,value.selection.start,value.selection.end)}
    fun typed(value:TextFieldValue) {
        val backspace=if(editor.composition==null && value.composition==null && value.selection.collapsed)PythonEditorTools.typedBackspace(editor.text,editor.selection.start,editor.selection.end,value.text,value.selection.start) else null
        if(backspace!=null) {update(TextFieldValue(backspace.source,selection=TextRange(backspace.cursor)));return}
        val newline=if(value.composition==null && value.selection.collapsed)PythonEditorTools.typedNewline(editor.text,editor.selection.start,editor.selection.end,value.text,value.selection.start) else null
        if(newline!=null) {update(TextFieldValue(newline.source,selection=TextRange(newline.cursor)));return}
        val auto=if(m.autoCloseBrackets&&editor.selection.collapsed&&value.selection.collapsed)BracketAutoClose.typed(editor.text,editor.selection.start,value.text,value.selection.start) else null
        update(if(auto!=null)TextFieldValue(auto.source,selection=TextRange(auto.cursor)) else value)
    }
    fun apply(edit:PythonEdit) {update(TextFieldValue(edit.source,selection=TextRange(edit.cursor,edit.selectionEnd)));editorFocus.requestFocus()}
    fun documentName(uri:Uri):String = runCatching {
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->
            if(cursor.moveToFirst())cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "script.py"
    fun write(uri:Uri) {
        try {
            context.contentResolver.openOutputStream(uri,"wt")?.use {stream->stream.write(editor.text.toByteArray(Charsets.UTF_8))}
                ?: error("Could not open the selected file for writing")
            m.savedPythonFile(documentName(uri),uri.toString())
        } catch(e:Exception) {m.pythonFileError(e.message ?: "Could not save file")}
    }
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-python")) {uri->if(uri!=null)write(uri)}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)try {
            runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
                .onFailure {runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}}
            val source=context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {it.readText()}
                ?: error("Could not read the selected file")
            editor=TextFieldValue(source,selection=TextRange(source.length))
            m.openPythonFile(source,documentName(uri),uri.toString())
        } catch(e:Exception) {m.pythonFileError(e.message ?: "Could not open file")}
    }
    val position=editor.selection.start.coerceIn(0,editor.text.length)
    val suggestions=remember(editor.text,position,editor.selection) {
        if(editor.selection.collapsed)PythonEditorTools.completions(editor.text,position) else emptyList()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PYTHON",style=MaterialTheme.typography.titleMedium,color=c.ink)
                Text(m.pythonFileName+if(m.pythonDirty) if(isKorean())"  • 저장 안 됨" else "  • unsaved" else "",fontSize=11.sp,color=c.muted,maxLines=1)
            }
            Button(onClick={m.runPython()},enabled=!m.pythonBusy,contentPadding=PaddingValues(horizontal=16.dp)) {Text(if(isKorean())"▶ 실행" else "▶ Run")}
            if(m.pythonBusy)SmallAction("Stop") {m.stopPython()}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            SmallAction("New") {if(m.pythonDirty)confirm="new" else {m.newPythonFile();editor=TextFieldValue("")}}
            SmallAction("Open .py") {if(m.pythonDirty)confirm="open" else open.launch(arrayOf("*/*"))}
            SmallAction("Save") {if(m.pythonUri.isBlank())create.launch(m.pythonFileName) else write(Uri.parse(m.pythonUri))}
            SmallAction("Save as") {create.launch(m.pythonFileName)}
            val hasSelection=editor.selection.min<editor.selection.max
            TextButton(onClick={
                val a=editor.selection.min;val b=editor.selection.max
                clipboard.setText(AnnotatedString(editor.text.substring(a,b)))
                apply(PythonEditorTools.replace(editor.text,a,b,""))
            },enabled=hasSelection,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) {
                Text(tr("Cut"),fontSize=11.sp)
            }
            SmallAction("Copy") {
                val a=editor.selection.min;val b=editor.selection.max
                clipboard.setText(AnnotatedString(if(a==b)editor.text else editor.text.substring(a,b)))
            }
            SmallAction("Paste") {clipboard.getText()?.text?.let {apply(PythonEditorTools.replace(editor.text,editor.selection.min,editor.selection.max,it))}}
            Box {
                SmallAction("Import ▾",translate=false) {importsOpen=true}
                DropdownMenu(importsOpen,{importsOpen=false}) {PythonEditorTools.imports.forEach {line->DropdownMenuItem(text={Text(line)},onClick={apply(PythonEditorTools.insertImport(editor.text,editor.selection.start,line));importsOpen=false})}}
            }
            Box {
                SmallAction("Function ▾",translate=false) {templatesOpen=true}
                DropdownMenu(templatesOpen,{templatesOpen=false}) {PythonEditorTools.snippets.forEach {snippet->DropdownMenuItem(text={Text(snippet.label)},onClick={apply(PythonEditorTools.insertSnippet(editor.text,editor.selection.min,editor.selection.max,snippet));templatesOpen=false})}}
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            SmallAction("Indent") {apply(PythonEditorTools.indent(editor.text,editor.selection.start,editor.selection.end))}
            SmallAction("Outdent") {apply(PythonEditorTools.indent(editor.text,editor.selection.start,editor.selection.end,outdent=true))}
        }
        OutlinedTextField(editor,::typed,Modifier.fillMaxWidth().height(320.dp).focusRequester(editorFocus).onPreviewKeyEvent {event->
            if(event.key==Key.Backspace && event.type==KeyEventType.KeyDown && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed && editor.composition==null) {
                val edit=PythonEditorTools.backspace(editor.text,editor.selection.start,editor.selection.end)
                if(edit!=null) {apply(edit);true} else false
            } else if(event.key==Key.Tab && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
                if(event.type==KeyEventType.KeyDown)apply(PythonEditorTools.tab(editor.text,editor.selection.start,editor.selection.end,event.isShiftPressed))
                true
            } else false
        },textStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace),label={Text(tr("Python code"))},placeholder={Text("print('Hello, world!')")},singleLine=false,
            keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false))
        if(suggestions.isNotEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            suggestions.forEach {candidate->SmallAction(candidate,translate=false) {
                apply(PythonEditorTools.complete(editor.text,position,candidate))
            }}
        }
        HorizontalDivider()
        Text(tr(if(m.pythonBusy)"Running…" else "Output"),fontSize=13.sp,color=c.muted)
        Box(Modifier.fillMaxWidth().heightIn(min=160.dp,max=320.dp).background(c.display).verticalScroll(rememberScrollState()).padding(10.dp)) {
            SelectionContainer {
                Text(buildString {
                    append(m.pythonOutput)
                    if(m.pythonError.isNotBlank()) {if(isNotEmpty())append('\n');append(m.pythonError)}
                    if(isEmpty()&&!m.pythonBusy)append(if(isKorean()) {if(m.pythonHasRun)"완료(출력 없음)." else "스크립트를 실행하면 출력이 여기에 표시됩니다."} else if(m.pythonHasRun)"Finished (no output)." else "Run a script to see its output here.")
                },fontFamily=FontFamily.Monospace,fontSize=15.sp,lineHeight=22.sp,color=if(m.pythonError.isNotBlank())c.danger else c.ink)
            }
        }
        m.pythonInputPrompt?.let { prompt ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(inputText,{inputText=it},Modifier.weight(1f).keepInputVisible(),label={Text(prompt.ifEmpty { tr("Input") })},singleLine=true)
                Button(onClick={m.submitPythonInput(inputText);inputText=""}) {Text(tr("Enter"))}
            }
        }
    }
    if(confirm.isNotBlank())AlertDialog(onDismissRequest={confirm=""},title={Text(tr("Unsaved changes"))},text={Text(if(isKorean())"${m.pythonFileName}의 변경 사항을 버릴까요?" else "Discard changes to ${m.pythonFileName}?")},confirmButton={TextButton(onClick={val action=confirm;confirm="";if(action=="new"){m.newPythonFile();editor=TextFieldValue("")}else open.launch(arrayOf("*/*"))}){Text(tr("Discard"))}},dismissButton={TextButton(onClick={confirm=""}){Text(tr("Cancel"))}})
}
