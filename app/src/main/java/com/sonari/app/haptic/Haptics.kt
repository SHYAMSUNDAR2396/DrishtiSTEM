package com.sonari.app.haptic

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(private val vibrator: Vibrator) {

    private enum class Tier { A, B, C }

    private val tier: Tier = when {
        Build.VERSION.SDK_INT >= 31 && vibrator.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_CLICK
        ) -> Tier.A
        Build.VERSION.SDK_INT >= 26 && vibrator.hasAmplitudeControl() -> Tier.B
        else -> Tier.C
    }

    // Short pulse confirming finger is on the feature (call at ~3 Hz while onFeature).
    fun contact() = when (tier) {
        Tier.A -> vibrator.vibrate(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.25f)
                .compose()
        )
        Tier.B -> vibrator.vibrate(VibrationEffect.createOneShot(14, 48))
        Tier.C -> @Suppress("DEPRECATION") vibrator.vibrate(14)
    }

    // Distinct double-pulse on landmark entry.
    fun landmark() = when (tier) {
        Tier.A -> vibrator.vibrate(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 70)
                .compose()
        )
        Tier.B -> vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 45, 65, 45), -1)
        )
        Tier.C -> @Suppress("DEPRECATION")
        vibrator.vibrate(longArrayOf(0, 45, 65, 45), -1)
    }

    fun cancel() = vibrator.cancel()

    companion object {
        fun from(context: android.content.Context): Haptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
            }
            return Haptics(vibrator)
        }
    }
}
