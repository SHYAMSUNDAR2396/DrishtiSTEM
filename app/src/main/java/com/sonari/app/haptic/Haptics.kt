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
    fun feel() {
        when (tier) {
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

    fun atomSignature(element: String) {
        val e = element.trim()
        val a = VibrationEffect.Composition.PRIMITIVE_TICK  // short/sharp
        val b = VibrationEffect.Composition.PRIMITIVE_CLICK // longer/stronger
        val effect = when (tier) {
            Tier.A -> {
                val c = VibrationEffect.startComposition()
                when (e) {
                    "H"  -> c.addPrimitive(a, 0.15f, 0)                   // "dit" — lightest single tick
                    "C"  -> { c.addPrimitive(a, 0.4f, 0); c.addPrimitive(a, 0.3f, 80) } // "dit...dit" — two slow ticks
                    "N"  -> { c.addPrimitive(a, 0.3f, 0); c.addPrimitive(a, 0.3f, 40); c.addPrimitive(a, 0.3f, 80) } // "dit-dit-dit" — triplet
                    "O"  -> c.addPrimitive(b, 0.8f, 0)                    // "DAH!" — single strong click
                    "S"  -> { c.addPrimitive(b, 0.5f, 0); c.addPrimitive(b, 0.4f, 70) } // "dah...dah" — two clicks
                    "P"  -> { c.addPrimitive(a, 0.3f, 0); c.addPrimitive(b, 0.6f, 60) } // "dit-DAH" — ascending
                    "F"  -> c.addPrimitive(b, 0.95f, 0)                   // "DAH!!" — strongest single
                    "Cl" -> { c.addPrimitive(b, 0.6f, 0); c.addPrimitive(a, 0.2f, 50) } // "DAH-dit" — descending
                    "Br" -> { c.addPrimitive(b, 0.5f, 0); c.addPrimitive(a, 0.3f, 40); c.addPrimitive(b, 0.5f, 80) } // "dah-dit-dah"
                    "Na" -> { c.addPrimitive(b, 0.8f, 0); c.addPrimitive(b, 0.6f, 120) } // "DAH...dah" — slow double strong
                    "K"  -> { c.addPrimitive(b, 0.7f, 0); c.addPrimitive(b, 0.5f, 100); c.addPrimitive(a, 0.2f, 180) } // "dah-dah-dit"
                    else -> c.addPrimitive(b, 0.5f, 0)  // generic single click
                }
                c.compose()
            }
            Tier.B -> {
                val (timings, amps) = when (e) {
                    "H"  -> longArrayOf(0, 15)         to intArrayOf(0, 40)
                    "C"  -> longArrayOf(0, 30, 50, 30) to intArrayOf(0, 100, 0, 90)
                    "N"  -> longArrayOf(0, 20, 20, 20, 20, 20) to intArrayOf(0, 80, 0, 80, 0, 80)
                    "O"  -> longArrayOf(0, 50)         to intArrayOf(0, 190)
                    "S"  -> longArrayOf(0, 40, 50, 40) to intArrayOf(0, 140, 0, 120)
                    "P"  -> longArrayOf(0, 25, 40, 40) to intArrayOf(0, 80, 0, 160)
                    "F"  -> longArrayOf(0, 60)         to intArrayOf(0, 255)
                    "Cl" -> longArrayOf(0, 40, 30, 25) to intArrayOf(0, 170, 0, 70)
                    "Br" -> longArrayOf(0, 40, 20, 20, 20, 40) to intArrayOf(0, 140, 0, 80, 0, 140)
                    "Na" -> longArrayOf(0, 80, 60, 60) to intArrayOf(0, 210, 0, 160)
                    "K"  -> longArrayOf(0, 70, 50, 50, 30, 20) to intArrayOf(0, 190, 0, 130, 0, 70)
                    else -> longArrayOf(0, 40)         to intArrayOf(0, 120)
                }
                VibrationEffect.createWaveform(timings, amps, -1)
            }
            Tier.C -> {
                val timings = when (e) {
                    "H"  -> longArrayOf(0, 15)
                    "C"  -> longArrayOf(0, 30, 50, 30)
                    "N"  -> longArrayOf(0, 20, 20, 20, 20, 20)
                    "O"  -> longArrayOf(0, 50)
                    "S"  -> longArrayOf(0, 40, 50, 40)
                    "P"  -> longArrayOf(0, 25, 40, 40)
                    "F"  -> longArrayOf(0, 60)
                    "Cl" -> longArrayOf(0, 40, 30, 25)
                    "Br" -> longArrayOf(0, 40, 20, 20, 20, 40)
                    "Na" -> longArrayOf(0, 80, 60, 60)
                    "K"  -> longArrayOf(0, 70, 50, 50, 30, 20)
                    else -> longArrayOf(0, 40)
                }
                @Suppress("DEPRECATION")
                VibrationEffect.createWaveform(timings, -1)
            }
        }
        vibrator.vibrate(effect)
    }

    fun bondSignature(order: Int) {
        // Bonds use a distinct "buzz" texture clearly different from atom patterns.
        // Single = short buzz, double = two buzz pulses, triple = three buzz pulses.
        val c = VibrationEffect.Composition.PRIMITIVE_CLICK
        val effect = when (tier) {
            Tier.A -> {
                val builder = VibrationEffect.startComposition()
                when (order) {
                    1 -> builder.addPrimitive(c, 0.35f, 0)                 // single buzz — one medium click
                    2 -> { builder.addPrimitive(c, 0.5f, 0); builder.addPrimitive(c, 0.5f, 60) } // double buzz — two clicks
                    else -> { builder.addPrimitive(c, 0.5f, 0); builder.addPrimitive(c, 0.5f, 40); builder.addPrimitive(c, 0.5f, 80) } // triple buzz
                }
                builder.compose()
            }
            Tier.B -> {
                val (timings, amps) = when (order) {
                    1 -> longArrayOf(0, 35)         to intArrayOf(0, 90)
                    2 -> longArrayOf(0, 20, 30, 20) to intArrayOf(0, 110, 0, 110)
                    else -> longArrayOf(0, 18, 15, 18, 15, 18) to intArrayOf(0, 120, 0, 120, 0, 120)
                }
                VibrationEffect.createWaveform(timings, amps, -1)
            }
            Tier.C -> {
                val timings = when (order) {
                    1 -> longArrayOf(0, 35)
                    2 -> longArrayOf(0, 20, 30, 20)
                    else -> longArrayOf(0, 18, 15, 18, 15, 18)
                }
                @Suppress("DEPRECATION")
                VibrationEffect.createWaveform(timings, -1)
            }
        }
        vibrator.vibrate(effect)
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
