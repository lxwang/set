package com.example.set

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.set.data.GameStore
import com.example.set.data.SettingsRepository
import com.example.set.ui.theme.SetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsRepository(applicationContext)
        val store = GameStore(applicationContext)
        setContent {
            SetTheme {
                SetApp(settings, store)
            }
        }
    }
}
