package com.example.mejustmix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun Terminal(mixViewModel: MixViewModel) {
    val gcodeHistory by mixViewModel.gcodeHistory.collectAsState()
    val listState = rememberLazyListState()

    // Scroll to the top whenever the history changes
    LaunchedEffect(gcodeHistory) {
        listState.animateScrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(8.dp)
    ) {
        Text("G-Code Terminal", style = MaterialTheme.typography.titleSmall, color = Color.White)
        LazyColumn(state = listState, reverseLayout = true) { // Newest at the top
            items(gcodeHistory.reversed()) { gcode -> // Reversed list
                Text("> $gcode", color = Color.Green, fontFamily = FontFamily.Monospace)
            }
        }
    }
}