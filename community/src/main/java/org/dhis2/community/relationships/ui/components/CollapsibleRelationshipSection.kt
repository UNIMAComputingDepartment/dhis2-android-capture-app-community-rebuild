package org.dhis2.community.relationships.ui.components


import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dhis2.commons.resources.ColorUtils
import org.dhis2.commons.resources.ResourceManager
import org.dhis2.community.R
import org.dhis2.community.relationships.CmtRelationshipTypeViewModel
import org.dhis2.community.relationships.CmtRelationshipViewModel
import org.dhis2.community.relationships.ui.Dhis2CmtTheme
import timber.log.Timber


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleRelationshipSection(

    relationshipTypeView: CmtRelationshipTypeViewModel,
    availableEntities: CmtRelationshipTypeViewModel?,
    onRelationshipClick: (CmtRelationshipViewModel) -> Unit = { },
    onEntitySelect: (CmtRelationshipViewModel, String) -> Unit = { _, _ ->},
    onCreateEntity: (String, String) -> Unit = { _, _ -> },
    onSearchTEIs: (String, String) -> Unit = { _, _ -> }
) {
    Dhis2CmtTheme {
        CollapsibleRelationshipSectionContent(
            relationshipTypeView = relationshipTypeView,
            availableEntities = availableEntities,
            onRelationshipClick = onRelationshipClick,
            onEntitySelect = onEntitySelect,
            onCreateEntity = onCreateEntity,
            onSearchTEIs = onSearchTEIs
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsibleRelationshipSectionContent(
    context : Context = LocalContext.current,
    colorUtils: ColorUtils = ColorUtils(),
    res: ResourceManager = ResourceManager(context,colorUtils),
    relationshipTypeView: CmtRelationshipTypeViewModel,
    availableEntities: CmtRelationshipTypeViewModel?,
    onRelationshipClick: (CmtRelationshipViewModel) -> Unit = { },
    onEntitySelect: (CmtRelationshipViewModel, String) -> Unit = { _, _ ->},
    onCreateEntity: (String, String) -> Unit = { _, _ -> },
    onSearchTEIs: (String, String) -> Unit = { _, _ -> }
) {
    val title = relationshipTypeView.description
    val existingRelationships = relationshipTypeView.relatedTeis
    var expanded by remember { mutableStateOf(false) }
    var listSearch by remember { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }

    val displayedRelationships = remember(listSearch, existingRelationships) {
        existingRelationships.filter {
            it.primaryAttribute.contains(listSearch, ignoreCase = true)
        }
    }

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coroutineScope = rememberCoroutineScope()
    var sheetSearch by remember { mutableStateOf("") }


    val relationship = existingRelationships.firstOrNull()
    val tieTypeIcon = relationship?.let{
        res.getObjectStyleDrawableResource(it.iconName, R.drawable.ic_tei_default)
    } ?: run {
        R.drawable.ic_tei_default
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column {
            // Header row with title, expand, and add icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp)
            ) {
                Icon(
                    painter = painterResource(tieTypeIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(onClick = { coroutineScope.launch { showAddSheet = true } }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Entity",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

            }

            // Expanded relationship list with search
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {

                    if (existingRelationships.size > 5) {
                        TextField(
                            value = listSearch,
                            onValueChange = { listSearch = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("Search relationships...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    val childListState = rememberLazyListState()
                    val childScope = rememberCoroutineScope()
                    val childMaxHeight = 400.dp

                    val interceptScrollModifier = Modifier.pointerInput(displayedRelationships, childListState) {
                        awaitEachGesture {
                            var pointerActive = true
                            var eventsCount = 0
                            while (pointerActive) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    val dy = change.positionChange().y
                                    if ((!childListState.canScrollForward && dy < 0f) ||
                                        (!childListState.canScrollBackward && dy > 0f)
                                    ) {
                                        // let parent consume the event
                                        return@forEach
                                    }
                                    Timber.d("Intercepted scroll dy: $dy")
                                    if (dy > 0.1f || eventsCount < 20000) {
                                        change.consume()
                                    }
                                    eventsCount ++
                                    childScope.launch {
                                        try {
                                            childListState.scrollBy(-dy)
                                        } catch (_: Exception) {}
                                    }
                                }

                                // stop if all pointers are up
                                pointerActive = event.changes.any { it.pressed }
                            }
                        }
                    }

                    LazyColumn(
                        state = childListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = 0.dp, max = childMaxHeight)
                            .then(interceptScrollModifier)
                    ) {
                        items(displayedRelationships) { rel ->
                            RelationshipItem(
                                item = rel,
                                onClick = { onRelationshipClick(rel) }
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet listing available entities
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { coroutineScope.launch { showAddSheet = false } },
            sheetState = sheetState,
            modifier = Modifier.fillMaxHeight(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add ${relationshipTypeView.description}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onCreateEntity(relationshipTypeView.relatedProgramUid, relationshipTypeView.uid)
                            coroutineScope.launch { showAddSheet = false }
                        },
                        modifier = Modifier
                            .padding(start = 8.dp)
                    ) {
                        Text(text = "Register New ${relationshipTypeView.relatedProgramName}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = sheetSearch,
                    onValueChange = {
                        sheetSearch = it
                        onSearchTEIs(it, relationshipTypeView.uid)
                    },
                    placeholder = { Text("Search entities...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Spacer(Modifier.height(12.dp))

                // Filter only entities not already in relationships
                val options = (availableEntities?.relatedTeis ?: listOf())
                    .filterNot { existingRelationships.any { ex -> ex.uid == it.uid } }
                    .filter { it.primaryAttribute.contains(sheetSearch, ignoreCase = true) }

                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(options) { entity ->
                        RelationshipItem(
                            item = entity,
                            onClick = {
                                onEntitySelect(entity, relationshipTypeView.uid)
                                coroutineScope.launch { showAddSheet = false }
                            },
                            isSelection = true
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationshipItem(
    context : Context = LocalContext.current,
    colorUtils: ColorUtils = ColorUtils(),
    res: ResourceManager = ResourceManager(context,colorUtils),
    item: CmtRelationshipViewModel,
    onClick: () -> Unit,
    isSelection: Boolean = false

) {
    val tieTypeIcon = res.getObjectStyleDrawableResource(item.iconName, R.drawable.ic_tei_default)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(tieTypeIcon),
            contentDescription = "Tei Icon",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.primaryAttribute,
                style = MaterialTheme.typography.bodyLarge
            )
            item.secondaryAttribute?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item.tertiaryAttribute?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(
                if (isSelection) R.drawable.ic_add_primary else R.drawable.ic_navigate_next
            ),
            contentDescription = if (isSelection) "Select" else "Navigate",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}