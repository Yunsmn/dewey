package app.dewey.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.dewey.DeweyApplication
import app.dewey.ui.theme.DeweyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as DeweyApplication).container

        setContent {
            DeweyTheme {
                DeweyApp(container)
            }
        }
    }
}
