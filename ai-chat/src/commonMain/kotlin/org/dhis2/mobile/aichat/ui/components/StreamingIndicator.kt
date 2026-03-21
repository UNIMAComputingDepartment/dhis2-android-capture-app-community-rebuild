package org.dhis2.mobile.aichat.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor

@Composable
fun StreamingIndicator(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha =
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(550), repeatMode = RepeatMode.Reverse),
            label = "alpha",
        )

    Row(modifier = modifier) {
        repeat(3) {
            Surface(
                modifier = Modifier.size(8.dp).alpha(alpha.value),
                shape = CircleShape,
                color = SurfaceColor.Container,
            ) {}
            Spacer(modifier = Modifier.size(4.dp))
        }
    }
}
