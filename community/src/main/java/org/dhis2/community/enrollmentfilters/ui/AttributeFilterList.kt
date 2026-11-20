@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package org.dhis2.community.enrollmentfilters.ui

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dhis2.community.enrollmentfilters.AttributeFilterState
import org.dhis2.community.enrollmentfilters.models.AttributeConstraint
import org.dhis2.community.enrollmentfilters.models.FilterWidget
import org.dhis2.community.enrollmentfilters.models.FilterableAttribute
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import java.util.Calendar

private val OnPrimary = Color.White
private val OnPrimaryMuted = Color.White.copy(alpha = 0.7f)

/**
 * Renders the enrollment-list attribute filters on the (primary-coloured) filter backdrop, matching
 * the look of the core commons filter rows: a 48dp header per attribute (icon, count badge, bold
 * uppercase title, subtitle, expand arrow) with a type-aware expandable body.
 *
 * Selections are written to the [AttributeFilterState] singleton; [onChanged] fires after each edit
 * so the host can re-run the search.
 */
@Composable
fun AttributeFilterList(
    attributes: List<FilterableAttribute>,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attributes.isEmpty()) return
    val version by AttributeFilterState.version.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        attributes.forEach { attribute ->
            AttributeFilterRow(
                attribute = attribute,
                version = version,
                onChanged = onChanged,
            )
        }
    }
}

@Composable
private fun AttributeFilterRow(
    attribute: FilterableAttribute,
    version: Int,
    onChanged: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // `version` is read so the header (badge/subtitle) recomposes when the singleton changes.
    val active = remember(version) { AttributeFilterState.isActive(attribute.uid) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = attribute.widget.icon(),
                contentDescription = null,
                tint = OnPrimary,
                modifier = Modifier.size(20.dp),
            )
            if (active) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(8.dp)
                        .background(OnPrimary, CircleShape),
                )
            }
            Text(
                text = attribute.label.uppercase(),
                color = OnPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = OnPrimary,
                modifier = Modifier.size(24.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AttributeFilterBody(attribute) { onChanged() }
            }
        }
    }
}

@Composable
private fun AttributeFilterBody(
    attribute: FilterableAttribute,
    onChanged: () -> Unit,
) {
    when (attribute.widget) {
        FilterWidget.KEYWORD -> KeywordBody(attribute, onChanged)
        FilterWidget.OPTIONS -> OptionsBody(attribute, onChanged)
        FilterWidget.BOOLEAN -> BooleanBody(attribute, onChanged)
        FilterWidget.NUMBER_RANGE -> NumberRangeBody(attribute, onChanged)
        FilterWidget.AGE_RANGE -> AgeRangeBody(attribute, onChanged)
        FilterWidget.DATE_RANGE -> DateRangeBody(attribute, onChanged)
    }
}

@Composable
private fun KeywordBody(attribute: FilterableAttribute, onChanged: () -> Unit) {
    val current = (AttributeFilterState.get(attribute.uid) as? AttributeConstraint.Keyword)?.text.orEmpty()
    var text by remember(attribute.uid) { mutableStateOf(current) }
    FilterTextField(
        value = text,
        label = "Contains",
        keyboardType = KeyboardType.Text,
        onValueChange = {
            text = it
            AttributeFilterState.set(attribute.uid, AttributeConstraint.Keyword(it))
            onChanged()
        },
    )
}

@Composable
private fun OptionsBody(attribute: FilterableAttribute, onChanged: () -> Unit) {
    val selected = (AttributeFilterState.get(attribute.uid) as? AttributeConstraint.Options)?.codes.orEmpty()
    val current = remember(attribute.uid) {
        mutableStateListOf<String>().apply { addAll(selected) }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attribute.options.forEach { option ->
            val isSelected = current.contains(option.code)
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (isSelected) current.remove(option.code) else current.add(option.code)
                    AttributeFilterState.set(attribute.uid, AttributeConstraint.Options(current.toSet()))
                    onChanged()
                },
                label = { Text(option.label) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = OnPrimary,
                    selectedContainerColor = OnPrimary,
                    selectedLabelColor = SurfaceColor.Primary,
                ),
            )
        }
    }
}

@Composable
private fun BooleanBody(attribute: FilterableAttribute, onChanged: () -> Unit) {
    val current = (AttributeFilterState.get(attribute.uid) as? AttributeConstraint.Bool)?.value
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BooleanChip("Yes", current == true) { toggleBoolean(attribute.uid, true, current, onChanged) }
        BooleanChip("No", current == false) { toggleBoolean(attribute.uid, false, current, onChanged) }
    }
}

@Composable
private fun BooleanChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = OnPrimary,
            selectedContainerColor = OnPrimary,
            selectedLabelColor = SurfaceColor.Primary,
        ),
    )
}

private fun toggleBoolean(uid: String, value: Boolean, current: Boolean?, onChanged: () -> Unit) {
    // Tapping the active choice clears it.
    val next = if (current == value) null else value
    AttributeFilterState.set(uid, next?.let { AttributeConstraint.Bool(it) })
    onChanged()
}

@Composable
private fun NumberRangeBody(attribute: FilterableAttribute, onChanged: () -> Unit) {
    val existing = AttributeFilterState.get(attribute.uid) as? AttributeConstraint.NumberRange
    var min by remember(attribute.uid) { mutableStateOf(existing?.min?.toCleanString().orEmpty()) }
    var max by remember(attribute.uid) { mutableStateOf(existing?.max?.toCleanString().orEmpty()) }
    fun push() {
        AttributeFilterState.set(
            attribute.uid,
            AttributeConstraint.NumberRange(min.toDoubleOrNull(), max.toDoubleOrNull()),
        )
        onChanged()
    }
    RangeFields(
        minValue = min,
        maxValue = max,
        keyboardType = KeyboardType.Number,
        onMinChange = { min = it; push() },
        onMaxChange = { max = it; push() },
    )
}

@Composable
private fun AgeRangeBody(attribute: FilterableAttribute, onChanged: () -> Unit) {
    val existing = AttributeFilterState.get(attribute.uid) as? AttributeConstraint.AgeRange
    var min by remember(attribute.uid) { mutableStateOf(existing?.minYears?.toString().orEmpty()) }
    var max by remember(attribute.uid) { mutableStateOf(existing?.maxYears?.toString().orEmpty()) }
    fun push() {
        AttributeFilterState.set(
            attribute.uid,
            AttributeConstraint.AgeRange(min.toIntOrNull(), max.toIntOrNull()),
        )
        onChanged()
    }
    RangeFields(
        minValue = min,
        maxValue = max,
        keyboardType = KeyboardType.Number,
        minLabel = "Min age",
        maxLabel = "Max age",
        onMinChange = { min = it; push() },
        onMaxChange = { max = it; push() },
    )
}

@Composable
private fun DateRangeBody(attribute: FilterableAttribute, onChanged: () -> Unit) {
    val existing = AttributeFilterState.get(attribute.uid) as? AttributeConstraint.DateRange
    var from by remember(attribute.uid) { mutableStateOf(existing?.from.orEmpty()) }
    var to by remember(attribute.uid) { mutableStateOf(existing?.to.orEmpty()) }
    fun push() {
        AttributeFilterState.set(
            attribute.uid,
            AttributeConstraint.DateRange(from.ifBlank { null }, to.ifBlank { null }),
        )
        onChanged()
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DatePickerField(
            value = from,
            label = "From",
            modifier = Modifier.weight(1f),
            onDatePicked = { from = it; push() },
        )
        DatePickerField(
            value = to,
            label = "To",
            modifier = Modifier.weight(1f),
            onDatePicked = { to = it; push() },
        )
    }
}

@Composable
private fun RangeFields(
    minValue: String,
    maxValue: String,
    keyboardType: KeyboardType,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    minLabel: String = "Min",
    maxLabel: String = "Max",
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterTextField(
            value = minValue,
            label = minLabel,
            keyboardType = keyboardType,
            modifier = Modifier.weight(1f),
            onValueChange = onMinChange,
        )
        FilterTextField(
            value = maxValue,
            label = maxLabel,
            keyboardType = keyboardType,
            modifier = Modifier.weight(1f),
            onValueChange = onMaxChange,
        )
    }
}

@Composable
private fun FilterTextField(
    value: String,
    label: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        readOnly = readOnly,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = OnPrimary,
            unfocusedTextColor = OnPrimary,
            focusedBorderColor = OnPrimary,
            unfocusedBorderColor = OnPrimaryMuted,
            focusedLabelColor = OnPrimary,
            unfocusedLabelColor = OnPrimaryMuted,
            cursorColor = OnPrimary,
            disabledTextColor = OnPrimary,
            disabledBorderColor = OnPrimaryMuted,
            disabledLabelColor = OnPrimaryMuted,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun DatePickerField(
    value: String,
    label: String,
    onDatePicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    Box(
        modifier = modifier.clickable {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    onDatePicked("%04d-%02d-%02d".format(year, month + 1, day))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        },
    ) {
        FilterTextField(
            value = value,
            label = label,
            keyboardType = KeyboardType.Text,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = OnPrimaryMuted)
            },
        )
    }
}

private fun Double.toCleanString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun FilterWidget.icon(): ImageVector =
    when (this) {
        FilterWidget.KEYWORD -> Icons.Default.Search
        FilterWidget.OPTIONS -> Icons.Default.Checklist
        FilterWidget.BOOLEAN -> Icons.Default.ToggleOn
        FilterWidget.NUMBER_RANGE -> Icons.Default.Numbers
        FilterWidget.AGE_RANGE -> Icons.Default.Cake
        FilterWidget.DATE_RANGE -> Icons.Default.DateRange
    }
