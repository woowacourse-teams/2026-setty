package com.aksworns22.setty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aksworns22.setty.navigation.SettyNavHost
import com.aksworns22.setty.push.requestNotificationPermissionIfNeeded
import com.aksworns22.setty.ui.theme.SettyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val startFurnitureId = intent.getLongExtra(EXTRA_FURNITURE_ID, -1L).takeIf { it > 0 }
        setContent {
            SettyTheme {
                SettyNavHost(startFurnitureId = startFurnitureId)
            }
        }
    }

    companion object {
        const val EXTRA_FURNITURE_ID = "furnitureId"
    }
}
