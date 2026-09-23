package com.example.calcmax
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.ui.CalculatorApp
import com.example.calcmax.ui.theme.CalcmaxTheme
class MainActivity : ComponentActivity() {
    private val calculator:CalculatorModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CalcmaxTheme(calculator.theme) { CalculatorApp(calculator) } }
    }
    override fun onStop(){calculator.save();super.onStop()}
}
