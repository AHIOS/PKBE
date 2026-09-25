package com.uci.pkbe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                var tab by remember { mutableIntStateOf(0) }
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Native") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("WebView") })
                    }
                    when (tab) {
                        0 -> NativeScreen(this@MainActivity, hiltViewModel())
                        else -> WebDemoScreen()
                    }
                }
            }
        }
    }
}
