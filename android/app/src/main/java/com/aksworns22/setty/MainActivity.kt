package com.aksworns22.setty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aksworns22.setty.navigation.SettyNavHost
import com.aksworns22.setty.ui.theme.SettyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SettyTheme {
                SettyNavHost()
            }
        }
    }
}
