package org.dhis2.community.enrollmentfilters

import org.dhis2.community.enrollmentfilters.models.AttributeConstraint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AttributeRangeEvaluatorTest {

    // ---- number ----

    @Test
    fun `number within inclusive bounds matches`() {
        val range = AttributeConstraint.NumberRange(min = 10.0, max = 20.0)
        assertTrue(AttributeRangeEvaluator.numberInRange("10", range))
        assertTrue(AttributeRangeEvaluator.numberInRange("15.5", range))
        assertTrue(AttributeRangeEvaluator.numberInRange("20", range))
    }

    @Test
    fun `number outside bounds does not match`() {
        val range = AttributeConstraint.NumberRange(min = 10.0, max = 20.0)
        assertFalse(AttributeRangeEvaluator.numberInRange("9.99", range))
        assertFalse(AttributeRangeEvaluator.numberInRange("21", range))
    }

    @Test
    fun `open ended number range only checks the present bound`() {
        assertTrue(AttributeRangeEvaluator.numberInRange("1000", AttributeConstraint.NumberRange(min = 10.0, max = null)))
        assertTrue(AttributeRangeEvaluator.numberInRange("-5", AttributeConstraint.NumberRange(min = null, max = 0.0)))
    }

    @Test
    fun `missing or unparseable number does not match`() {
        val range = AttributeConstraint.NumberRange(min = 10.0, max = 20.0)
        assertFalse(AttributeRangeEvaluator.numberInRange(null, range))
        assertFalse(AttributeRangeEvaluator.numberInRange("", range))
        assertFalse(AttributeRangeEvaluator.numberInRange("abc", range))
    }

    // ---- date ----

    @Test
    fun `date within inclusive bounds matches`() {
        val range = AttributeConstraint.DateRange(from = "2020-01-01", to = "2020-12-31")
        assertTrue(AttributeRangeEvaluator.dateInRange("2020-01-01", range))
        assertTrue(AttributeRangeEvaluator.dateInRange("2020-06-15", range))
        assertTrue(AttributeRangeEvaluator.dateInRange("2020-12-31", range))
    }

    @Test
    fun `date outside bounds does not match`() {
        val range = AttributeConstraint.DateRange(from = "2020-01-01", to = "2020-12-31")
        assertFalse(AttributeRangeEvaluator.dateInRange("2019-12-31", range))
        assertFalse(AttributeRangeEvaluator.dateInRange("2021-01-01", range))
    }

    @Test
    fun `datetime value is tolerated by taking the date part`() {
        val range = AttributeConstraint.DateRange(from = "2020-01-01", to = "2020-12-31")
        assertTrue(AttributeRangeEvaluator.dateInRange("2020-06-15T09:30:00.000", range))
    }

    @Test
    fun `missing or unparseable date does not match`() {
        val range = AttributeConstraint.DateRange(from = "2020-01-01", to = null)
        assertFalse(AttributeRangeEvaluator.dateInRange(null, range))
        assertFalse(AttributeRangeEvaluator.dateInRange("not-a-date", range))
    }

    // ---- age ----

    @Test
    fun `age boundaries are inclusive`() {
        val today = LocalDate.of(2026, 7, 24)
        val range = AttributeConstraint.AgeRange(minYears = 10, maxYears = 20)
        // Exactly 10 today.
        assertTrue(AttributeRangeEvaluator.ageInRange("2016-07-24", range, today))
        // Exactly 20 today.
        assertTrue(AttributeRangeEvaluator.ageInRange("2006-07-24", range, today))
    }

    @Test
    fun `age just outside the window does not match`() {
        val today = LocalDate.of(2026, 7, 24)
        val range = AttributeConstraint.AgeRange(minYears = 10, maxYears = 20)
        // 9 years old (birthday tomorrow).
        assertFalse(AttributeRangeEvaluator.ageInRange("2016-07-25", range, today))
        // 21 years old.
        assertFalse(AttributeRangeEvaluator.ageInRange("2005-07-24", range, today))
    }

    @Test
    fun `future date of birth never matches`() {
        val today = LocalDate.of(2026, 7, 24)
        val range = AttributeConstraint.AgeRange(minYears = 0, maxYears = 120)
        assertFalse(AttributeRangeEvaluator.ageInRange("2030-01-01", range, today))
    }

    @Test
    fun `missing date of birth does not match age range`() {
        val today = LocalDate.of(2026, 7, 24)
        assertFalse(AttributeRangeEvaluator.ageInRange(null, AttributeConstraint.AgeRange(0, 120), today))
    }
}
