package com.example.calcmax
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.ui.CalculatorApp
import com.example.calcmax.ui.theme.CalcmaxTheme
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { val model: CalculatorModel=viewModel(); CalcmaxTheme(model.theme) { CalculatorApp(model) } }
    }
}
