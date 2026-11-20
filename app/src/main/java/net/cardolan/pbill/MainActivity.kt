package net.cardolan.pbill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.cardolan.pbill.ui.PBillNavHost
import net.cardolan.pbill.ui.theme.PBillTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PBillApplication
        setContent {
            PBillTheme {
                PBillNavHost(app.repository, app.settings)
            }
        }
    }
}
