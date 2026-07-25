package org.dhis2.community.enrollmentfilters

import org.dhis2.community.enrollmentfilters.models.FilterWidget
import org.hisp.dhis.android.core.common.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilterWidgetTest {

    @Test
    fun `option set backed attribute is always OPTIONS regardless of value type`() {
        assertEquals(FilterWidget.OPTIONS, FilterWidget.forAttribute(ValueType.TEXT, hasOptionSet = true))
        assertEquals(FilterWidget.OPTIONS, FilterWidget.forAttribute(ValueType.INTEGER, hasOptionSet = true))
    }

    @Test
    fun `numeric value types map to a number range`() {
        listOf(
            ValueType.NUMBER,
            ValueType.PERCENTAGE,
            ValueType.INTEGER,
            ValueType.INTEGER_POSITIVE,
            ValueType.INTEGER_NEGATIVE,
            ValueType.INTEGER_ZERO_OR_POSITIVE,
            ValueType.UNIT_INTERVAL,
        ).forEach {
            assertEquals("$it", FilterWidget.NUMBER_RANGE, FilterWidget.forAttribute(it, hasOptionSet = false))
        }
    }

    @Test
    fun `age date and text types map to their widgets`() {
        assertEquals(FilterWidget.AGE_RANGE, FilterWidget.forAttribute(ValueType.AGE, hasOptionSet = false))
        assertEquals(FilterWidget.DATE_RANGE, FilterWidget.forAttribute(ValueType.DATE, hasOptionSet = false))
        assertEquals(FilterWidget.DATE_RANGE, FilterWidget.forAttribute(ValueType.DATETIME, hasOptionSet = false))
        assertEquals(FilterWidget.KEYWORD, FilterWidget.forAttribute(ValueType.TEXT, hasOptionSet = false))
        assertEquals(FilterWidget.KEYWORD, FilterWidget.forAttribute(ValueType.PHONE_NUMBER, hasOptionSet = false))
        assertEquals(FilterWidget.BOOLEAN, FilterWidget.forAttribute(ValueType.BOOLEAN, hasOptionSet = false))
        assertEquals(FilterWidget.BOOLEAN, FilterWidget.forAttribute(ValueType.TRUE_ONLY, hasOptionSet = false))
    }

    @Test
    fun `unsupported value types are not filterable`() {
        listOf(
            ValueType.COORDINATE,
            ValueType.IMAGE,
            ValueType.FILE_RESOURCE,
            ValueType.ORGANISATION_UNIT,
            ValueType.GEOJSON,
            ValueType.TIME,
        ).forEach {
            assertNull("$it", FilterWidget.forAttribute(it, hasOptionSet = false))
        }
    }

    @Test
    fun `overrides parse case-insensitively and reject junk`() {
        assertEquals(FilterWidget.AGE_RANGE, FilterWidget.fromOverride("age_range"))
        assertEquals(FilterWidget.KEYWORD, FilterWidget.fromOverride("KEYWORD"))
        assertNull(FilterWidget.fromOverride("RANGE"))
        assertNull(FilterWidget.fromOverride(null))
    }
}
