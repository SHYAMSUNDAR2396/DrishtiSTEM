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

    // Continuous vibration while finger is on a curve feature.
    // Call once when entering the curve, then cancel() when leaving.
    fun contact() = when (tier) {
        Tier.A -> vibrator.vibrate(
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.25f)
                .compose()
        )
        Tier.B -> vibrator.vibrate(VibrationEffect.createOneShot(14, 48))
        Tier.C -> @Suppress("DEPRECATION") vibrator.vibrate(14)
    }

    // Pulsing "beeped" vibration while finger slides along a curve.
    // Short buzz + gap = sonar-ping rhythm that confirms the user is on the graph.
    fun feel() = when (tier) {
        Tier.A -> vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 60, 140),
                intArrayOf(0, 130, 0),
                0
            )
        )
        Tier.B -> vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 60, 140),
                intArrayOf(0, 130, 0),
                0
            )
        )
        Tier.C -> @Suppress("DEPRECATION")
        vibrator.vibrate(longArrayOf(0, 60, 140), 0)
    }

    // Steady continuous vibration — used at landmarks (max, min, intersection, etc.)
    // so the user feels a clear contrast from the pulsing background.
    fun steady() = when (tier) {
        Tier.A -> vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 300),
                intArrayOf(0, 150),
                0
            )
        )
        Tier.B -> vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 300),
                intArrayOf(0, 150),
                0
            )
        )
        Tier.C -> @Suppress("DEPRECATION")
        vibrator.vibrate(longArrayOf(0, 300), 0)
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

    fun atomSignature(element: String) = when (element.trim()) {
        "H" -> when (tier) {
            Tier.A -> vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f)
                    .compose()
            )
            Tier.B -> vibrator.vibrate(VibrationEffect.createOneShot(20, 80))
            Tier.C -> @Suppress("DEPRECATION") vibrator.vibrate(20)
        }
        "O" -> when (tier) {
            Tier.A -> vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 100)
                    .compose()
            )
            Tier.B -> vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 60), intArrayOf(0, 160, 0, 120), -1)
            )
            Tier.C -> @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 80, 60, 60), -1)
        }
        "C" -> when (tier) {
            Tier.A -> vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, 60)
                    .compose()
            )
            Tier.B -> vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 30, 30, 30), intArrayOf(0, 100, 0, 100), -1)
            )
            Tier.C -> @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 30, 30, 30), -1)
        }
        else -> landmark()
    }

    fun bondSignature(order: Int) = when (order) {
        1 -> contact()
        2 -> when (tier) {
            Tier.A -> vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 50)
                    .compose()
            )
            Tier.B -> vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 20, 20, 20, 20, 20), intArrayOf(0, 100, 0, 100, 0, 100), -1)
            )
            Tier.C -> @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 20, 20, 20, 20, 20), -1)
        }
        else -> when (tier) {
            Tier.A -> vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 40)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 80)
                    .compose()
            )
            Tier.B -> vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 25, 15, 25, 15, 25), intArrayOf(0, 130, 0, 130, 0, 130), -1
                )
            )
            Tier.C -> @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 25, 15, 25, 15, 25), -1)
        }
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
