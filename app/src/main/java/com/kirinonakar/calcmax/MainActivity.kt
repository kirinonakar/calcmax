package com.kirinonakar.calcmax
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.ui.CalculatorApp
import com.kirinonakar.calcmax.ui.theme.CalcmaxTheme
class MainActivity : ComponentActivity() {
    private val calculator:CalculatorModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CalcmaxTheme(calculator.theme) { CalculatorApp(calculator) } }
    }
    override fun onStop(){calculator.save();super.onStop()}
}
