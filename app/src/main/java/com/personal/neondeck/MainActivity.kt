package com.personal.neondeck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recupera el tema de color que elegiste la última vez
        Cy.themeIndex = getSharedPreferences("neon_ui", MODE_PRIVATE).getInt("theme", 0)
        UiSound.init(this)
        setContent { NeonApp() }
    }
}
