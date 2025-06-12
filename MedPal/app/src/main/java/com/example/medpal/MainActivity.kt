package com.example.medpal

import ChatScreen
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import com.example.medpal.ui.theme.MedPalTheme

class MainActivity : ComponentActivity() {

//    private val chatViewModel:ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedPalTheme(darkTheme = true) {
                MainScreen()
            }
        }
    }
}


