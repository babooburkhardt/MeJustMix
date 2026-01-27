package com.example.mejustmix.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ControlPanel(
    mixViewModel: MixViewModel, 
    onSettingsClick: () -> Unit,
    onPumpLongClick: (Int) -> Unit
) {
    val paintMix by mixViewModel.paintMix.collectAsState()
    val totalVolume by mixViewModel.totalVolume.collectAsState()
    val manualTransparency = mixViewModel.manualTransparency.value

    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsState()

    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var showSaveSuccess by remember { mutableStateOf(false) }
    var savedFolderName by remember { mutableStateOf("") }

    // Auto-dismiss success message after 2 seconds
    LaunchedEffect(showSaveSuccess) {
        if (showSaveSuccess) {
            delay(2000)
            showSaveSuccess = false
        }
    }

    if (showSaveDialog) {
        SaveColorDialog(
            mixViewModel = mixViewModel,
            onDismissRequest = { showSaveDialog = false },
            onSave = { folderName ->
                mixViewModel.saveColorToLibrary(folderName)
                savedFolderName = folderName
                showSaveDialog = false
                showSaveSuccess = true
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val isOutOfGamut by mixViewModel.isOutOfGamut.collectAsState()
        val currentColor by mixViewModel.color.collectAsState()

        if (isOutOfGamut && settingsState.showRealityCheck) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF4E5), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFFCC80), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = "Out of Gamut Warning",
                    tint = Color(0xFFEF6C00),
                    modifier = Modifier.padding(end = 12.dp).size(24.dp)
                )
                Column {
                    Text(
                        text = "This color is very bright. Actual paint may appear slightly less vibrant than your screen.",
                        style = TextStyle(fontSize = 12.sp, color = Color(0xFFEF6C00))
                    )
                }
            }
        }

        // Animated Smart Save Button
        // Replaces both the old button and the success banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(24.dp)) // Matched to new VisualizerCard style
                .clickable { if (!showSaveSuccess) showSaveDialog = true },
            color = if (showSaveSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = showSaveSuccess,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 2 }).togetherWith(fadeOut() + slideOutVertically { -it / 2 })
                },
                label = "SaveButtonAnim"
            ) { success ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (success) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Saved to $savedFolderName!", 
                            color = MaterialTheme.colorScheme.primary, 
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        // Color Preview Dot
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(currentColor, androidx.compose.foundation.shape.CircleShape)
                                .border(1.dp, Color.Gray.copy(alpha=0.5f), androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(Icons.Outlined.Save, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Save to Library", 
                            color = MaterialTheme.colorScheme.primary, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val paintRatio = 1f - manualTransparency
        val displayVolume = totalVolume * paintRatio

        val cyanMl = paintMix.cyan * displayVolume
        val magentaMl = paintMix.magenta * displayVolume
        val yellowMl = paintMix.yellow * displayVolume
        val blackMl = paintMix.black * displayVolume
        val whiteMl = paintMix.white * displayVolume

        val percentages = if (displayVolume > 0) {
            mapOf(
                "CYAN" to (cyanMl / displayVolume) * 100,
                "MAG" to (magentaMl / displayVolume) * 100,
                "YEL" to (yellowMl / displayVolume) * 100,
                "BLK" to (blackMl / displayVolume) * 100,
                "WHT" to (whiteMl / displayVolume) * 100
            )
        } else {
            mapOf("CYAN" to 0f, "MAG" to 0f, "YEL" to 0f, "BLK" to 0f, "WHT" to 0f)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MixRatioCard(
                label = "CYAN",
                percent = percentages["CYAN"]!!.roundToInt(),
                volume = cyanMl,
                remainingVolume = settingsState.pumps[0].currentVolumeMl,
                tileColor = Color(0xFF22D3EE),
                textColor = Color(0xFF083344),
                onClick = { mixViewModel.setColor(Color.Cyan) },
                onLongClick = { onPumpLongClick(0) }
            )
            MixRatioCard(
                label = "MAG",
                percent = percentages["MAG"]!!.roundToInt(),
                volume = magentaMl,
                remainingVolume = settingsState.pumps[1].currentVolumeMl,
                tileColor = Color(0xFFD946EF),
                textColor = Color.White,
                onClick = { mixViewModel.setColor(Color.Magenta) },
                onLongClick = { onPumpLongClick(1) }
            )
            MixRatioCard(
                label = "YEL",
                percent = percentages["YEL"]!!.roundToInt(),
                volume = yellowMl,
                remainingVolume = settingsState.pumps[2].currentVolumeMl,
                tileColor = Color(0xFFFACC15),
                textColor = Color(0xFF422006),
                onClick = { mixViewModel.setColor(Color.Yellow) },
                onLongClick = { onPumpLongClick(2) }
            )
            MixRatioCard(
                label = "BLK",
                percent = percentages["BLK"]!!.roundToInt(),
                volume = blackMl,
                remainingVolume = settingsState.pumps[3].currentVolumeMl,
                tileColor = Color(0xFF111827),
                textColor = Color.White,
                onClick = { mixViewModel.setColor(Color.Black) },
                onLongClick = { onPumpLongClick(3) }
            )
            MixRatioCard(
                label = "WHT",
                percent = percentages["WHT"]!!.roundToInt(),
                volume = whiteMl,
                remainingVolume = settingsState.pumps[4].currentVolumeMl,
                tileColor = Color.White,
                textColor = Color.Gray,
                hasBorder = true,
                onClick = { mixViewModel.setColor(Color.White) },
                onLongClick = { onPumpLongClick(4) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.MixRatioCard(
    label: String,
    percent: Int,
    volume: Float,
    remainingVolume: Float,
    tileColor: Color,
    textColor: Color,
    hasBorder: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(65.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tileColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(if (hasBorder) Modifier.border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp)) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black),
            color = textColor.copy(alpha = 0.6f)
        )
        Text(
            text = "$percent%",
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = textColor
        )
        Text(
            text = volume.toMlString(2),
            style = TextStyle(fontSize = 10.sp),
            color = textColor.copy(alpha = 0.8f)
        )
        Text(
            text = "${remainingVolume.toMlString(0)} left",
            style = TextStyle(fontSize = 8.sp),
            color = if (remainingVolume < 5f) Color.Red else textColor.copy(alpha = 0.5f)
        )
    }
}
