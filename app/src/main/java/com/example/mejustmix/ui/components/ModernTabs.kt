package com.example.mejustmix.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A modern, pill-shaped TabRow with a sliding indicator.
 * Consistent with the MeJustMix premium aesthetic.
 */
@Composable
fun ModernPillTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    tabs: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        modifier = modifier
    ) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            divider = {},
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedTabIndex])
                            .fillMaxHeight()
                            .padding(4.dp)
                            .background(
                                color = indicatorColor,
                                shape = RoundedCornerShape(20.dp)
                            )
                    )
                }
            },
            modifier = Modifier.height(48.dp),
            tabs = tabs
        )
    }
}

/**
 * An individual tab for use within ModernPillTabRow.
 * Handles dynamic tinting of icons and text based on selection.
 */
@Composable
fun ModernPillTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    icon: ImageVector? = null,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val contentColor = if (selected) selectedContentColor else unselectedContentColor
    
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .zIndex(2f),
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor
                )
            }
        }
    )
}
