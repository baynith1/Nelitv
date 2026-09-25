package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.model.LiveChannel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.NeliTVTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeliTVTheme {
                NeliTvApp()
            }
        }
    }
}

@Composable
fun NeliTvApp() {
    var activeChannel by remember { mutableStateOf<LiveChannel?>(null) }

    val currentChannel = activeChannel
    if (currentChannel != null) {
        PlayerScreen(
            channel = currentChannel,
            onBack = {
                activeChannel = null
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        HomeScreen(
            onChannelSelected = { selected ->
                activeChannel = selected
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
