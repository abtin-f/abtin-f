package com.abtinf.glassmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.abtinf.glassmusic.ui.AppRoot
import com.abtinf.glassmusic.ui.theme.GlassMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GlassMusicTheme { AppRoot() }
        }
    }
}
