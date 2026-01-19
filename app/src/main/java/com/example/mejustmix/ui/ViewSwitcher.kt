package com.example.mejustmix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ViewSwitcher(selected: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color.LightGray.copy(alpha = 0.5f))
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(if (selected == "Wheel") Color.White else Color.Transparent)
                .clickable { onSelected("Wheel") }
        ) {
            Text("Wheel", modifier = Modifier.padding(16.dp, 8.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(if (selected == "Photo") Color.White else Color.Transparent)
                .clickable { onSelected("Photo") }
        ) {
            Text("Photo", modifier = Modifier.padding(16.dp, 8.dp))
        }
    }
}
