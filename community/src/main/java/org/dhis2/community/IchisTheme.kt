package org.dhis2.community

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2LightColorScheme
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme

// iCHIS brand colors
val IchisPrimary = Color(0xFF218C51)      // #218C51
val IchisPrimaryDark = Color(0xFF14714D)  // #14714D
val IchisPrimaryLight = Color(0xFF8CDD8E) // #8CDD8E

/**
 * Copy DHIS2's color scheme and override only the primary set.
 * All other tokens remain exactly as DHIS2 defines.
 */
val IchisLightColorScheme = DHIS2LightColorScheme.copy(
    primary = IchisPrimary,
    primaryContainer = IchisPrimaryLight,
    onPrimary = DHIS2LightColorScheme.onPrimary,
    onPrimaryContainer = DHIS2LightColorScheme.onPrimaryContainer,
)

/**
 * Wrap DHIS2Theme and swap only the primary colors.
 * Typography and shapes stay the same as DHIS2.
 */
@Composable
fun IchisTheme(content: @Composable () -> Unit) {
    DHIS2Theme {
        MaterialTheme(
            colorScheme = IchisLightColorScheme,
            content = content,
        )
    }
}
