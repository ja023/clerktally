package com.vague.crewtally

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vague.crewtally.ui.nav.CrewTallyNavHost
import com.vague.crewtally.ui.theme.CrewTallyTheme

/** Single-activity host. Compose owns all UI; the theme + nav shell start here. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CrewTallyTheme {
                CrewTallyNavHost()
            }
        }
    }
}
