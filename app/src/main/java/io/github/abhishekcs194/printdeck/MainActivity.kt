package io.github.abhishekcs194.printdeck

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.ui.PrintDeckApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 16 enforces edge-to-edge; opting in explicitly keeps behaviour
        // identical across the whole minSdk 26..36 range rather than depending on
        // where the platform default happens to sit.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            PrintDeckTheme {
                PrintDeckApp(sharedUri = intent.documentUri())
            }
        }
    }

    /**
     * The document another app shared with us, if any.
     *
     * Accepts both SEND and VIEW: "share to PrintDeck" and "open with PrintDeck"
     * are the same intent as far as the user is concerned, and supporting only
     * one of them makes the app look broken from whichever entry point was left
     * out.
     */
    private fun Intent.documentUri(): Uri? = when (action) {
        Intent.ACTION_SEND -> extraStream()
        Intent.ACTION_VIEW -> data
        else -> null
    }

    @Suppress("DEPRECATION")
    private fun Intent.extraStream(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
