package com.kokoro.tts.reader.ui

import android.content.Context
import android.util.DisplayMetrics
import androidx.recyclerview.widget.LinearSmoothScroller

/**
 * Custom smooth scroller that centers and anchors the active reading sentence
 * at ~35% from the top of the viewport (the natural ergonomic reading eye-line),
 * providing an ElevenReader-like reading flow.
 */
class CenterSmoothScroller(context: Context) : LinearSmoothScroller(context) {

    override fun calculateDtToFit(
        viewStart: Int,
        viewEnd: Int,
        boxStart: Int,
        boxEnd: Int,
        snapPreference: Int
    ): Int {
        val boxHeight = boxEnd - boxStart
        // Target positioning: active item top aligns with ~35% down the viewport
        val targetTop = boxStart + (boxHeight * 0.35f).toInt()
        return targetTop - viewStart
    }

    override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
        // Smooth, fluid animation (~120ms per screen transition)
        return 120f / displayMetrics.densityDpi
    }
}
