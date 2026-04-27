package org.dhis2.usescases.eventsWithoutRegistration.eventDetails.ui

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.databinding.BindingAdapter
import org.dhis2.community.IchisTheme

@ExperimentalAnimationApi
@BindingAdapter("setReopen")
fun ComposeView.setReopenButton(viewModel: EventDetailsViewModel) {
    setContent {
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        IchisTheme {
            val eventDetail by viewModel.eventDetails.collectAsState()
            ReopenButton(eventDetail.canReopen) { viewModel.onReopenClick() }
        }
    }
}
