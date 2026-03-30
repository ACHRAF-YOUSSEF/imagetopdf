package tech.youssefachraf.image_to_pdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import tech.youssefachraf.image_to_pdf.navigation.AppNavGraph
import tech.youssefachraf.image_to_pdf.ui.theme.ImagetopdfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImagetopdfTheme {
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
            }
        }
    }
}
