package com.kirinonakar.calcmax

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.math.Editor
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises model input paths directly, without an activity or UI automation. */
@RunWith(AndroidJUnit4::class)
class ConstantInputInstrumentedTest {
    @Test fun normalAndCalcInputKeepConstantsSeparate()=runBlocking {
        withContext(Dispatchers.Main) {
            val app=ApplicationProvider.getApplicationContext<Application>()
            val store=ViewModelStore()
            val model=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[CalculatorModel::class.java]
            try {
                model.clear(recordUndo=false)
                model.insert("Ans");model.insert("pi");model.insert("e")
                assertEquals("Ans*pi*e",model.editor.source)
                assertEquals("*",model.editor.tree()?.value)
                model.clear(recordUndo=false)
                model.edit(Editor("x+1"));model.startCalc()
                model.insertCalcValue("pi");model.insertCalcValue("e")
                assertEquals("pi*e",model.calcSession?.input?.source)
                model.cancelCalc()
                model.clear(recordUndo=false)
            } finally { store.clear() }
        }
    }
}
