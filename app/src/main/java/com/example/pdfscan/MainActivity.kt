package com.example.pdfscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdfscan.ui.AppNav
import com.example.pdfscan.ui.theme.ScanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ScanApp).container
        setContent {
            val dynamic by container.settings.dynamicColor.collectAsStateWithLifecycle()
            ScanTheme(dynamicColor = dynamic) { AppNav(container) }
        }
    }
}
