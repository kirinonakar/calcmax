package com.kirinonakar.calcmax.calculator

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONObject

internal object CalculatorPythonActions {
    fun CalculatorModel.performEditPython(source:String,start:Int=source.length,end:Int=start) {
        if(pythonState.editSource(source,start,end))save()
    }
    fun CalculatorModel.performInsertPython(snippet:String,inside:Int=snippet.length) {
        if(pythonState.insert(snippet,inside))save()
    }
    fun CalculatorModel.performNewPythonFile() {pythonState.newFile();save()}
    fun CalculatorModel.performOpenPythonFile(source:String,name:String,uri:String) {pythonState.openFile(source,name,uri);save()}
    fun CalculatorModel.performSavedPythonFile(name:String,uri:String) {pythonState.savedFile(name,uri);save()}
    fun CalculatorModel.performPythonFileError(message:String) {pythonState.pythonError=message}
    fun CalculatorModel.performRunPython() {
        if(pythonBusy)return
        val source=pythonSource;val filename=pythonFileName
        pythonJob=viewModelScope.launch {
            pythonBusy=true;pythonState.pythonOutput="";pythonState.pythonError="";pythonState.pythonHasRun=false;pythonInputPrompt=null;pythonInputSubmit=null
            try {
                val response=engine.execute(JSONObject().put("action","python").put("source",source).put("filename",filename).put("functions",functions).put("variables",variables).put("assumptions",assumptions)) { prompt, output, submit ->
                    pythonState.pythonOutput=output;pythonInputPrompt=prompt;pythonInputSubmit=submit
                }
                pythonState.pythonOutput=response.optString("output","")
                pythonState.pythonError=if(response.optBoolean("ok"))"" else response.optString("error","Python execution failed")
                pythonState.pythonHasRun=true
            } finally {pythonBusy=false;pythonInputPrompt=null;pythonInputSubmit=null}
        }
    }
    fun CalculatorModel.performSubmitPythonInput(value:String) {pythonInputSubmit?.invoke(value);pythonInputSubmit=null;pythonInputPrompt=null}
    fun CalculatorModel.performStopPython() {pythonJob?.cancel();engine.cancel();pythonBusy=false;pythonInputPrompt=null;pythonInputSubmit=null;pythonState.pythonHasRun=true;pythonState.pythonError="Execution stopped"}
}
