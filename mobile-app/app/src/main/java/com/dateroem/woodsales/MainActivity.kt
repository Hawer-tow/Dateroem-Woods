package com.dateroem.woodsales

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dateroem.woodsales.ui.nav.AppRoot
import com.dateroem.woodsales.ui.theme.DateroemWoodSalesTheme

// FragmentActivity (still a ComponentActivity) so the fingerprint lock's
// BiometricPrompt can attach to it.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() and before setContent().
        // Styled to match the in-app splash (see themes.xml,
        // Theme.App.Starting) and not held open artificially, so it
        // dismisses the instant Compose's own SplashScreen is ready to
        // draw - the two read as one continuous scene.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = remember { AppContainer(applicationContext) }
            // Each phase in AppRoot (splash, login, the drawer shell) owns
            // its own full-bleed layout, so there's no shared outer
            // Scaffold here.
            DateroemWoodSalesTheme {
                AppRoot(container = container)
            }
        }
    }
}
