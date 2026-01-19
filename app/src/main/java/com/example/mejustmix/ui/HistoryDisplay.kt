package com.example.mejustmix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// IMPORT THESE TWO:
import com.example.mejustmix.data.HistoryItem 
import com.example.mejustmix.ui.theme.getBrightness
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryGridSheet(
    historyItems: List<HistoryItem>,
    onSelectMix: (HistoryItem) -> Unit
) {
    if (historyItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text("No mix history yet", color = Color.Gray)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(300.dp)
        ) {
            items(historyItems) { item ->
                HistoryTile(item = item, onClick = { onSelectMix(item) })
            }
        }
    }
}

@Composable
fun HistoryTile(item: HistoryItem, onClick: () -> Unit) {
    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeString = timeFormatter.format(Date(item.timestamp))
    
    // Calculate text contrast
    val textColor = if (item.color.getBrightness() > 0.5f) Color.Black else Color.White

    Box(
        modifier = Modifier
            .aspectRatio(1f) // Makes it a square
            .clip(RoundedCornerShape(12.dp))
            .background(item.color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Semi-transparent background pill for readability
        Surface(
            color = item.color.copy(alpha = 0.0f), 
            modifier = Modifier.wrapContentSize()
        ) {
            Text(
                text = timeString,
                color = textColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}