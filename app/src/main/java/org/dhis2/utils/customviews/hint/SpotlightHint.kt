package org.dhis2.utils.customviews.hint

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.dhis2.commons.prefs.PreferenceProvider

/**
 * Reusable "show a [SpotlightHintOverlay] at most every [minIntervalMillis], until the user
 * either performs the target action or explicitly dismisses it" state machine, backed by
 * [PreferenceProvider] and keyed per [hintId] so any number of independent hints - on any
 * screen - can coexist without colliding.
 *
 * Typical use from an Activity/Fragment:
 * ```
 * val hint = SpotlightHint(preferences)
 * if (hint.isDue(HINT_ID)) {
 *     anchorView.post {
 *         val bounds = SpotlightHint.computeTargetBounds(anchorView, overlayComposeView)
 *         overlayComposeView.visibility = View.VISIBLE
 *         overlayComposeView.setContent {
 *             SpotlightHintOverlay(
 *                 targetBounds = bounds,
 *                 message = "...",
 *                 onDismiss = { overlayComposeView.visibility = View.GONE },
 *                 onDontShowAgain = {
 *                     hint.markDismissed(HINT_ID)
 *                     overlayComposeView.visibility = View.GONE
 *                 },
 *             )
 *         }
 *         hint.markShown(HINT_ID)
 *     }
 * }
 * // ...and whenever the target action itself succeeds, regardless of whether the hint
 * // was showing: hint.markDismissed(HINT_ID)
 * ```
 */
class SpotlightHint(private val preferences: PreferenceProvider) {

    fun isDue(hintId: String, minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS): Boolean {
        if (preferences.getBoolean(dismissedKey(hintId), false)) return false
        val lastShownAt = preferences.getLong(lastShownKey(hintId), 0L) ?: 0L
        return System.currentTimeMillis() - lastShownAt >= minIntervalMillis
    }

    fun markShown(hintId: String) {
        preferences.setValue(lastShownKey(hintId), System.currentTimeMillis())
    }

    fun markDismissed(hintId: String) {
        preferences.setValue(dismissedKey(hintId), true)
    }

    private fun dismissedKey(hintId: String) = "SPOTLIGHT_HINT_${hintId}_DISMISSED"

    private fun lastShownKey(hintId: String) = "SPOTLIGHT_HINT_${hintId}_LAST_SHOWN_AT"

    companion object {
        const val DEFAULT_MIN_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L

        /**
         * [anchorView]'s bounds, expressed in [overlayView]'s own coordinate space - what
         * [SpotlightHintOverlay] expects as targetBounds.
         */
        fun computeTargetBounds(anchorView: View, overlayView: View): Rect {
            val anchorLocation = IntArray(2)
            anchorView.getLocationOnScreen(anchorLocation)
            val overlayLocation = IntArray(2)
            overlayView.getLocationOnScreen(overlayLocation)

            return Rect(
                offset = Offset(
                    x = (anchorLocation[0] - overlayLocation[0]).toFloat(),
                    y = (anchorLocation[1] - overlayLocation[1]).toFloat(),
                ),
                size = Size(anchorView.width.toFloat(), anchorView.height.toFloat()),
            )
        }
    }
}
