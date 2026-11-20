package org.dhis2.community.enrollmentfilters.ui

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.recyclerview.widget.RecyclerView
import org.dhis2.community.IchisTheme
import org.dhis2.community.enrollmentfilters.models.FilterableAttribute

/**
 * Single-item adapter that renders the enrollment-list attribute filters as a Compose block, so it
 * can be appended to the core commons filter list via a `ConcatAdapter` — the attribute filters then
 * appear below the built-in filters in the same scrolling backdrop, with a matching look.
 *
 * Shows nothing when there are no filterable attributes for the program.
 */
class AttributeFilterAdapter(
    private val onChanged: () -> Unit = {},
) : RecyclerView.Adapter<AttributeFilterAdapter.ViewHolder>() {

    private var attributes: List<FilterableAttribute> = emptyList()

    /** Replaces the rendered attributes (e.g. once resolved off the main thread). */
    fun submit(newAttributes: List<FilterableAttribute>) {
        attributes = newAttributes
        notifyDataSetChanged()
    }

    class ViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val composeView = ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        return ViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.composeView.setContent {
            IchisTheme {
                AttributeFilterList(
                    attributes = attributes,
                    onChanged = onChanged,
                )
            }
        }
    }

    override fun getItemCount(): Int = if (attributes.isEmpty()) 0 else 1
}
