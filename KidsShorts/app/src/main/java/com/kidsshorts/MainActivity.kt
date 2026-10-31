package com.kidsshorts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kidsshorts.ui.nav.AppNav
import com.kidsshorts.ui.theme.KidsShortsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KidsShortsApp).container
        setContent {
            KidsShortsTheme {
                AppNav(container = container, onCloseApp = { finish() })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app always re-locks the parent area.
        (application as KidsShortsApp).container.parentSession.lock()
    }
}
