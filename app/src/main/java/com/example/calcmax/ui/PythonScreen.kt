package com.example.calcmax.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.math.BracketAutoClose
import com.example.calcmax.ui.theme.LocalInstrument

@Composable fun PythonScreen(m:CalculatorModel) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    val c=LocalInstrument.current
    var editor by remember { mutableStateOf(TextFieldValue(m.pythonSource,selection=TextRange(m.pythonSelectionStart,m.pythonSelectionEnd))) }
    var importsOpen by remember {mutableStateOf(false)}
    var templatesOpen by remember {mutableStateOf(false)}
    var confirm by remember {mutableStateOf("")}
    var inputText by remember {mutableStateOf("")}
    DisposableEffect(context) {
        val window=(context as? Activity)?.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)}
    }
    LaunchedEffect(m.pythonSource,m.pythonSelectionStart,m.pythonSelectionEnd) {
        if(editor.text!=m.pythonSource || editor.selection.start!=m.pythonSelectionStart || editor.selection.end!=m.pythonSelectionEnd)
            editor=TextFieldValue(m.pythonSource,selection=TextRange(m.pythonSelectionStart,m.pythonSelectionEnd))
    }
    fun update(value:TextFieldValue) {editor=value;m.editPython(value.text,value.selection.start,value.selection.end)}
    fun typed(value:TextFieldValue) {
        val auto=if(m.autoCloseBrackets&&editor.selection.collapsed&&value.selection.collapsed)BracketAutoClose.typed(editor.text,editor.selection.start,value.text,value.selection.start) else null
        update(if(auto!=null)TextFieldValue(auto.source,selection=TextRange(auto.cursor)) else value)
    }
    fun apply(edit:PythonEdit) {update(TextFieldValue(edit.source,selection=TextRange(edit.cursor)))}
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
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PYTHON",style=MaterialTheme.typography.titleMedium,color=c.ink)
                Text(m.pythonFileName+if(m.pythonDirty)"  • unsaved" else "",fontSize=11.sp,color=c.muted,maxLines=1)
            }
            Button(onClick={m.runPython()},enabled=!m.pythonBusy,contentPadding=PaddingValues(horizontal=16.dp)) {Text("▶ Run")}
            if(m.pythonBusy)SmallAction("Stop") {m.stopPython()}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            SmallAction("New") {if(m.pythonDirty)confirm="new" else {m.newPythonFile();editor=TextFieldValue("")}}
            SmallAction("Open .py") {if(m.pythonDirty)confirm="open" else open.launch(arrayOf("*/*"))}
            SmallAction("Save") {if(m.pythonUri.isBlank())create.launch(m.pythonFileName) else write(Uri.parse(m.pythonUri))}
            SmallAction("Save as") {create.launch(m.pythonFileName)}
            SmallAction("Copy") {
                val a=editor.selection.min;val b=editor.selection.max
                clipboard.setText(AnnotatedString(if(a==b)editor.text else editor.text.substring(a,b)))
            }
            SmallAction("Paste") {clipboard.getText()?.text?.let {apply(PythonEditorTools.replace(editor.text,editor.selection.min,editor.selection.max,it))}}
            Box {
                SmallAction("Import ▾") {importsOpen=true}
                DropdownMenu(importsOpen,{importsOpen=false}) {PythonEditorTools.imports.forEach {line->DropdownMenuItem(text={Text(line)},onClick={apply(PythonEditorTools.insertImport(editor.text,editor.selection.start,line));importsOpen=false})}}
            }
            Box {
                SmallAction("Function ▾") {templatesOpen=true}
                DropdownMenu(templatesOpen,{templatesOpen=false}) {PythonEditorTools.snippets.forEach {snippet->DropdownMenuItem(text={Text(snippet.label)},onClick={apply(PythonEditorTools.replace(editor.text,editor.selection.min,editor.selection.max,snippet.code,snippet.cursorOffset));templatesOpen=false})}}
            }
        }
        OutlinedTextField(editor,::typed,Modifier.fillMaxWidth().height(320.dp),textStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace),label={Text("Python code")},placeholder={Text("print('Hello, world!')")},singleLine=false)
        if(suggestions.isNotEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            suggestions.forEach {candidate->SmallAction(candidate) {
                val start=PythonEditorTools.wordStart(editor.text,position)
                var end=position
                while(end<editor.text.length && (editor.text[end].isLetterOrDigit()||editor.text[end]=='_'))end++
                var edit=PythonEditorTools.replace(editor.text,start,end,candidate)
                val required=PythonEditorTools.requiredImport(editor.text,start,candidate)
                if(required!=null)edit=PythonEditorTools.insertImport(edit.source,edit.cursor,required)
                apply(edit)
            }}
        }
        HorizontalDivider()
        Text(if(m.pythonBusy)"Running…" else "Output",fontSize=13.sp,color=c.muted)
        Box(Modifier.fillMaxWidth().heightIn(min=160.dp,max=320.dp).background(c.display).verticalScroll(rememberScrollState()).padding(10.dp)) {
            SelectionContainer {
                Text(buildString {
                    append(m.pythonOutput)
                    if(m.pythonError.isNotBlank()) {if(isNotEmpty())append('\n');append(m.pythonError)}
                    if(isEmpty()&&!m.pythonBusy)append(if(m.pythonHasRun)"Finished (no output)." else "Run a script to see its output here.")
                },fontFamily=FontFamily.Monospace,fontSize=15.sp,lineHeight=22.sp,color=if(m.pythonError.isNotBlank())c.danger else c.ink)
            }
        }
        m.pythonInputPrompt?.let { prompt ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(inputText,{inputText=it},Modifier.weight(1f),label={Text(prompt.ifEmpty { "Input" })},singleLine=true)
                Button(onClick={m.submitPythonInput(inputText);inputText=""}) {Text("Enter")}
            }
        }
    }
    if(confirm.isNotBlank())AlertDialog(onDismissRequest={confirm=""},title={Text("Unsaved changes")},text={Text("Discard changes to ${m.pythonFileName}?")},confirmButton={TextButton(onClick={val action=confirm;confirm="";if(action=="new"){m.newPythonFile();editor=TextFieldValue("")}else open.launch(arrayOf("*/*"))}){Text("Discard")}},dismissButton={TextButton(onClick={confirm=""}){Text("Cancel")}})
}
