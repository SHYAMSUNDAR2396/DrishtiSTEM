package com.sonari.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BG_S = Color(0xFF1A1B1E)
private val ACCENT_S = Color(0xFF4FC3F7)
private val SECTION = Color(0xFF2C2D31)

data class SonariSettings(
    val pitchRangeLow: Float = 200f,
    val pitchRangeHigh: Float = 1000f,
    val sweepDurationSec: Float = 5f,
    val hapticIntensity: Float = 1f,
    val stereoPanEnabled: Boolean = true
)

@Composable
fun SettingsScreen(
    settings: SonariSettings,
    onSettingsChange: (SonariSettings) -> Unit,
    onTutorial: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pitchLow by remember { mutableFloatStateOf(settings.pitchRangeLow) }
    var pitchHigh by remember { mutableFloatStateOf(settings.pitchRangeHigh) }
    var sweepDur by remember { mutableFloatStateOf(settings.sweepDurationSec) }
    var hapticInt by remember { mutableFloatStateOf(settings.hapticIntensity) }
    var stereo by remember { mutableStateOf(settings.stereoPanEnabled) }

    fun emit() = onSettingsChange(
        SonariSettings(pitchLow, pitchHigh, sweepDur, hapticInt, stereo)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BG_S)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", color = Color.White, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(20.dp))

        SectionLabel("Pitch range")
        SettingSlider(
            label = "Low pitch",
            value = pitchLow,
            valueRange = 100f..500f,
            display = "%.0f Hz".format(pitchLow),
            description = "Low pitch frequency %.0f Hz".format(pitchLow)
        ) { pitchLow = it; emit() }

        SettingSlider(
            label = "High pitch",
            value = pitchHigh,
            valueRange = 500f..2000f,
            display = "%.0f Hz".format(pitchHigh),
            description = "High pitch frequency %.0f Hz".format(pitchHigh)
        ) { pitchHigh = it; emit() }

        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("Overview sweep")
        SettingSlider(
            label = "Sweep duration",
            value = sweepDur,
            valueRange = 2f..15f,
            display = "%.0f s".format(sweepDur),
            description = "Sweep duration %.0f seconds".format(sweepDur)
        ) { sweepDur = it; emit() }

        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("Haptics")
        SettingSlider(
            label = "Haptic intensity",
            value = hapticInt,
            valueRange = 0f..1f,
            display = "%.0f%%".format(hapticInt * 100),
            description = "Haptic intensity %.0f percent".format(hapticInt * 100)
        ) { hapticInt = it; emit() }

        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("Audio")
        ToggleSetting(
            label = "Stereo pan (headphones)",
            description = "Stereo pan: x-position maps to left/right channel. Best with headphones.",
            checked = stereo
        ) { stereo = it; emit() }

        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("Help")
        Button(
            onClick = onTutorial,
            colors = ButtonDefaults.buttonColors(containerColor = SECTION),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Open tutorial and gesture guide" }
        ) { Text("Open tutorial & gesture guide", color = ACCENT_S) }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Accepted limitations:",
            color = Color(0xFF888888),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        listOf(
            "Sonification has a learning curve — the tutorial helps.",
            "Haptics confirm contact and mark landmarks; they do not render texture.",
            "Molecules are 2D connectivity, not 3D geometry or stereochemistry.",
            "Stereo pan requires headphones; phone speaker plays in mono."
        ).forEach { limitation ->
            Text("• $limitation", color = Color(0xFF666666), fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = ACCENT_S, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    description: String,
    onChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, color = Color(0xFFCCCCCC), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(display, color = Color.White, fontSize = 14.sp)
    }
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = ACCENT_S,
            activeTrackColor = ACCENT_S,
            inactiveTrackColor = SECTION
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ToggleSetting(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(label, color = Color(0xFFCCCCCC), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ACCENT_S,
                uncheckedTrackColor = SECTION
            ),
            modifier = Modifier.semantics { contentDescription = description }
        )
    }
}
