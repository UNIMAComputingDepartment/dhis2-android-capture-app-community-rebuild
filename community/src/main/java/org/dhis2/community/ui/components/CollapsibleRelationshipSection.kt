package org.dhis2.community.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dhis2.community.R
import org.dhis2.community.relationships.CmtRelationshipTypeViewModel
import org.dhis2.community.relationships.CmtRelationshipViewModel
import org.dhis2.community.ui.Dhis2CmtTheme
import androidx.compose.material3.Icon // Correct import for Material 3
import androidx.compose.material3.MaterialTheme


import androidx.compose.material3.IconButton // <<<< MAKE SURE THIS IS MATERIAL3
import androidx.compose.material3.Text // <<<< MAKE SURE THIS IS MATERIAL3


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
    relationshipTypeView: CmtRelationshipTypeViewModel,
    availableEntities: CmtRelationshipTypeViewModel?,
    onRelationshipClick: (CmtRelationshipViewModel) -> Unit = { },
    onEntitySelect: (CmtRelationshipViewModel, String) -> Unit = { _, _ ->},
    onCreateEntity: (String, String) -> Unit = { _, _ -> },
    onSearchTEIs: (String, String) -> Unit = { _, _ -> }
) {
    val title = relationshipTypeView.description
    val tieTypeIcon = relationshipTypeView
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
                    painter = painterResource(R.drawable.ic_people_outline),
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
                IconButton(onClick = { coroutineScope.launch { showAddSheet = true } }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Entity",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded relationship list with search
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    if (existingRelationships.size > 10) {
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

                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .fillMaxWidth()
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
    item: CmtRelationshipViewModel,
    onClick: () -> Unit,
    isSelection: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_people_outline),
            contentDescription = null,
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
