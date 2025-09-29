package com.apbarrero.wheresmycar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apbarrero.wheresmycar.ui.MainScreen
import com.apbarrero.wheresmycar.ui.MainViewModel
import com.apbarrero.wheresmycar.ui.theme.WheresMyCarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WheresMyCarTheme {
                val viewModel: MainViewModel = viewModel()
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
