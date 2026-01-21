package com.example.mejustmix

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mejustmix.ui.MainScreen
import com.example.mejustmix.ui.theme.MeJustMixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Lock to portrait on phones (screen width < 600dp)
        // This ensures phones stay in portrait mode while tablets/desktop (DeX) 
        // can still rotate or resize to trigger the landscape/tablet UI in MainScreen
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        
        enableEdgeToEdge()
        setContent {
            MeJustMixTheme {
                MainScreen()
            }
        }
    }
}