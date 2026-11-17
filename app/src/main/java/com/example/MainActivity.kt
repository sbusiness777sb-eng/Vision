package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alfred.configureAppCheck
import com.example.ui.VisionAppShell
import com.example.ui.viewmodel.VisionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge display and render VISION UI
        enableEdgeToEdge()
        setContent {
            val viewModel: VisionViewModel = viewModel()
            VisionAppShell(viewModel = viewModel)
        }
    }
}
