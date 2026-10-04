package com.kirinonakar.symvacas
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.ui.CalculatorApp
import com.kirinonakar.symvacas.ui.theme.SymvaCASTheme
class MainActivity : ComponentActivity() {
    private val calculator:CalculatorModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SymvaCASTheme(calculator.theme) { CalculatorApp(calculator) } }
    }
    override fun onStop(){calculator.save();super.onStop()}
}
