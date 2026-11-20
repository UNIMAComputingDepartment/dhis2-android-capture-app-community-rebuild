package org.dhis2.utils.customviews.hint

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Reusable full-screen spotlight coachmark: dims everything except a circular cutout around
 * [targetBounds] (expressed in this composable's own coordinate space - see
 * [SpotlightHint.computeTargetBounds] for turning an arbitrary anchor View's screen position
 * into that space), with a pulsing ring around the cutout and a tooltip explaining the gesture.
 *
 * Not tied to any specific feature - any screen can render this over a full-screen ComposeView
 * to spotlight one of its own Views. Pair with [SpotlightHint] to decide *when* to show it.
 */
@Composable
fun SpotlightHintOverlay(
    targetBounds: Rect,
    message: String,
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit,
    dismissLabel: String = "Got it",
    dontShowAgainLabel: String = "Don't show again",
) {
    val density = LocalDensity.current
    val spotlightPaddingPx = with(density) { 12.dp.toPx() }
    val spotlightCenter = targetBounds.center
    val spotlightRadius = (maxOf(targetBounds.width, targetBounds.height) / 2f) + spotlightPaddingPx

    val infiniteTransition = rememberInfiniteTransition(label = "spotlightHintPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spotlightHintPulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spotlightHintPulseAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        // Scrim with a circular hole punched out around the target. BlendMode.Clear only
        // erases correctly when composited into its own offscreen layer first - without
        // graphicsLayer(Offscreen) it would just draw transparent-on-transparent and the
        // whole screen behind would stay dimmed with no visible cutout.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.68f))
            drawCircle(
                color = Color.Transparent,
                radius = spotlightRadius,
                center = spotlightCenter,
                blendMode = BlendMode.Clear,
            )
        }

        // Pulsing ring + static boundary ring, drawn normally on top of the scrim.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White,
                radius = spotlightRadius * pulseScale,
                center = spotlightCenter,
                alpha = pulseAlpha,
                style = Stroke(width = 4.dp.toPx()),
            )
            drawCircle(
                color = Color.White,
                radius = spotlightRadius,
                center = spotlightCenter,
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        val tooltipOffsetY = with(density) { (targetBounds.bottom + spotlightRadius * 0.3f).toDp() }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = tooltipOffsetY, x = 16.dp)
                .width(260.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = message, color = Color.Black, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDontShowAgain) {
                            Text(dontShowAgainLabel)
                        }
                        TextButton(onClick = onDismiss) {
                            Text(dismissLabel)
                        }
                    }
                }
            }
        }
    }
}
