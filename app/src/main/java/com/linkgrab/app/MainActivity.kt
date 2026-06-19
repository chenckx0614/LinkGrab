package com.linkgrab.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkgrab.app.ui.navigation.AppNavigation
import com.linkgrab.app.ui.theme.LinkGrabTheme
import com.linkgrab.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = viewModel()
            val colorMode by viewModel.colorMode.collectAsState()

            handleShareIntent(intent, viewModel)

            LinkGrabTheme(colorMode = colorMode) {
                AppNavigation(
                    viewModel = viewModel,
                    initialShareText = getShareText(intent),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?, viewModel: MainViewModel) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            if (sharedText.isNotBlank()) {
                viewModel.parseUrl(sharedText)
            }
        }
    }

    private fun getShareText(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        return null
    }
}
