package org.dhis2.community.enrollmentfilters

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.enrollmentfilters.models.AttributeConstraint
import java.time.LocalDate
import java.time.Period

/**
 * Pure evaluation of range constraints against a raw attribute value string. Kept free of D2 and
 * paging types so it can be unit-tested directly. A missing or unparseable value never matches
 * (returns false) — consistent with "this item has no value for the range you asked for".
 */
object AttributeRangeEvaluator {

    fun numberInRange(value: String?, range: AttributeConstraint.NumberRange): Boolean {
        val number = value?.trim()?.toDoubleOrNull() ?: return false
        if (range.min != null && number < range.min) return false
        if (range.max != null && number > range.max) return false
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun ageInRange(
        value: String?,
        range: AttributeConstraint.AgeRange,
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        val dob = parseDate(value) ?: return false
        val age = Period.between(dob, today).years
        if (age < 0) return false
        if (range.minYears != null && age < range.minYears) return false
        if (range.maxYears != null && age > range.maxYears) return false
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun dateInRange(value: String?, range: AttributeConstraint.DateRange): Boolean {
        val date = parseDate(value) ?: return false
        val from = parseDate(range.from)
        val to = parseDate(range.to)
        if (from != null && date.isBefore(from)) return false
        if (to != null && date.isAfter(to)) return false
        return true
    }

    /** Parses an ISO date, tolerating a datetime by taking the leading yyyy-MM-dd. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun parseDate(value: String?): LocalDate? {
        val trimmed = value?.trim()?.takeIf { it.length >= 10 } ?: return null
        return try {
            LocalDate.parse(trimmed.substring(0, 10))
        } catch (e: Exception) {
            null
        }
    }
}
