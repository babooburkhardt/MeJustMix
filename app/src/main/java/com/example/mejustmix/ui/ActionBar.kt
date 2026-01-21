package com.example.mejustmix.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ActionBar() {
    Row(modifier = Modifier.padding(top = 16.dp)) {
        OutlinedButton(onClick = { /*TODO*/ }) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Save")
        }
        Button(onClick = { /*TODO*/ }, modifier = Modifier.padding(start = 16.dp)) {
            Icon(imageVector = Icons.Default.Done, contentDescription = "Mix")
        }
    }
}
