package com.lifxcontrol.ui.screens

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifxcontrol.ui.UiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    uiState: UiState,
    onBack: () -> Unit,
    onToggleLights: () -> Unit,
    onStartDisco: () -> Unit,
    onStopDisco: () -> Unit,
    onRefresh: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "disco_bg")
    val discoButtonColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFE91E63),
        targetValue = Color(0xFF9C27B0),
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                Color(0xFFE91E63) at 0 using LinearEasing
                Color(0xFF2196F3) at 500 using LinearEasing
                Color(0xFF4CAF50) at 1000 using LinearEasing
                Color(0xFFFFEB3B) at 1500 using LinearEasing
                Color(0xFFFF5722) at 2000 using LinearEasing
                Color(0xFF00BCD4) at 2500 using LinearEasing
                Color(0xFF9C27B0) at 3000 using LinearEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "disco_color"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.selectedLocationName ?: "Home") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !uiState.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onToggleLights,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.anyLightsOn)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                ),
                enabled = !uiState.isLoading && !uiState.isDiscoMode
            ) {
                Text(
                    text = if (uiState.anyLightsOn) "Turn All Off" else "Turn All On",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Button(
                onClick = if (uiState.isDiscoMode) onStopDisco else onStartDisco,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isDiscoMode) discoButtonColor
                                     else MaterialTheme.colorScheme.secondary
                ),
                enabled = !uiState.isLoading
            ) {
                Text(
                    text = if (uiState.isDiscoMode) "Stop Disco Mode" else "Start Disco Mode",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.lights.isNotEmpty()) {
                Text(
                    text = "${uiState.lights.size} light${if (uiState.lights.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.lights, key = { it.id }) { light ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            color = if (light.power == "on") {
                                                if (light.color.saturation < 0.1) {
                                                    Color(1f, 1f, 0.85f + 0.15f * (light.brightness.toFloat()))
                                                } else {
                                                    Color.hsv(
                                                        hue = light.color.hue.toFloat(),
                                                        saturation = light.color.saturation.toFloat().coerceIn(0f, 1f),
                                                        value = light.brightness.toFloat().coerceIn(0f, 1f)
                                                    )
                                                }
                                            } else {
                                                Color.DarkGray
                                            },
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = light.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = buildString {
                                            append(light.group.name)
                                            if (light.power == "on") {
                                                append(" · ${(light.brightness * 100).roundToInt()}%")
                                            }
                                            if (!light.connected) append(" · Offline")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Badge(
                                    containerColor = if (light.power == "on")
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (light.power == "on") "ON" else "OFF",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
