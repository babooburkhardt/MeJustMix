package com.example.mejustmix.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A modern, pill-shaped TabRow with a sliding indicator.
 * Consistent with the MeJustMix premium aesthetic.
 * 
 * @param compact When true, uses custom Row layout where selected tab expands.
 *                When false, uses standard TabRow with indicator.
 */
@Composable
fun ModernPillTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    compact: Boolean = false,
    tabs: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        modifier = modifier
    ) {
        if (compact) {
            // Custom Row layout for compact mode
            // Tabs fill the width using weight, selected tab gets more space
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                tabs()
            }
        } else {
            // Standard TabRow with sliding indicator
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
                tabs = { Row(Modifier.fillMaxWidth()) { tabs() } }
            )
        }
    }
}

/**
 * An individual tab for use within ModernPillTabRow.
 * Handles dynamic tinting of icons and text based on selection.
 * 
 * @param compact When true, only shows icon for unselected tabs. 
 *                Selected tab expands with animated weight to show both icon and text.
 */
@Composable
fun RowScope.ModernPillTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    icon: ImageVector? = null,
    compact: Boolean = false,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val contentColor = if (selected) selectedContentColor else unselectedContentColor
    val showText = !compact || selected
    
    if (compact) {
        // Compact mode: animated weight with bouncy spring
        val backgroundColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
        
        // Animate the weight - selected gets 2.5x, unselected gets 1x
        val targetWeight = if (selected) 2.5f else 1f
        val animatedWeight by animateFloatAsState(
            targetValue = targetWeight,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "tabWeight"
        )
        
        Box(
            modifier = Modifier
                .zIndex(if (selected) 10f else 0f)
                .weight(animatedWeight)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (!showText) text else null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor
                    )
                    if (showText) {
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
                if (showText) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    } else {
        // Standard mode: use Tab composable with equal weight
        Box(modifier = Modifier.weight(1f)) {
            Tab(
                selected = selected,
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
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
    }
}
