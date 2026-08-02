package org.dhis2.community.mappers

import org.dhis2.community.mappers.models.ConditionOp
import org.dhis2.community.mappers.models.EventCondition
import org.dhis2.community.mappers.models.EventSelector
import org.dhis2.community.mappers.models.Pick
import org.dhis2.community.mappers.resolve.EventPicker
import org.dhis2.community.mappers.resolve.EventRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val HB = "hbDataElem1"
private const val VISIT_TYPE = "visitTypeDe"

/**
 * Tests for event selection — the "which incident does this value belong to" question.
 *
 * Getting this wrong is uniquely dangerous because the result is always a real value from a real
 * visit; it is simply the wrong visit, and nothing downstream looks suspicious.
 */
class EventPickerTest {

    private fun event(
        uid: String,
        day: Int,
        values: Map<String, String> = emptyMap(),
        lastUpdated: Long? = null,
    ) = EventRef(
        uid = uid,
        enrollmentUid = "enr1",
        programStageUid = "stage1",
        eventDate = day * 86_400_000L,
        dueDate = null,
        created = day * 86_400_000L,
        lastUpdated = lastUpdated,
        status = "ACTIVE",
        values = values,
    )

    // ─── The blank-latest trap (F5) ──────────────────────────────────────────

    @Test
    fun `latest with value skips a newer blank event`() {
        val events = listOf(
            event("booking", 1, mapOf(HB to "11.2")),
            event("followup", 5, mapOf(HB to "")),
        )

        val picked = EventPicker.pick(events, EventSelector(select = Pick.LATEST_WITH_VALUE), HB)

        // The newest event exists but holds nothing; carrying its blank forward would erase 11.2.
        assertEquals("booking", picked?.uid)
    }

    @Test
    fun `latest takes the newest event even when it is blank`() {
        val events = listOf(
            event("booking", 1, mapOf(HB to "11.2")),
            event("followup", 5, mapOf(HB to "")),
        )

        val picked = EventPicker.pick(events, EventSelector(select = Pick.LATEST), HB)

        assertEquals("followup", picked?.uid)
    }

    // ─── Naming a specific incident (F4) ─────────────────────────────────────

    @Test
    fun `a where condition selects a named visit rather than an ordinal position`() {
        val events = listOf(
            event("v2", 10, mapOf(VISIT_TYPE to "ANC2", HB to "10.0")),
            event("v1", 20, mapOf(VISIT_TYPE to "ANC1", HB to "12.0")),
        )

        val picked = EventPicker.pick(
            events,
            EventSelector(
                select = Pick.FIRST,
                where = listOf(EventCondition(VISIT_TYPE, ConditionOp.EQUALS, "ANC1")),
            ),
            HB,
        )

        // ANC1 was recorded later than ANC2 here, so an ordinal "first" would have picked the wrong
        // one. The condition is what makes the intent survive messy data entry.
        assertEquals("v1", picked?.uid)
    }

    @Test
    fun `not equals does not match an event that never recorded the field`() {
        val events = listOf(
            event("noType", 1, mapOf(HB to "9.0")),
            event("anc2", 2, mapOf(VISIT_TYPE to "ANC2", HB to "10.0")),
        )

        val picked = EventPicker.pick(
            events,
            EventSelector(
                select = Pick.FIRST,
                where = listOf(EventCondition(VISIT_TYPE, ConditionOp.NOT_EQUALS, "ANC1")),
            ),
            HB,
        )

        // Absence is unknown, not "different from ANC1".
        assertEquals("anc2", picked?.uid)
    }

    // ─── Determinism ─────────────────────────────────────────────────────────

    @Test
    fun `ties on event date are broken deterministically and not by input order`() {
        val a = event("aaa", 5, mapOf(HB to "1"), lastUpdated = 100L)
        val b = event("bbb", 5, mapOf(HB to "2"), lastUpdated = 100L)

        val forwards = EventPicker.pick(listOf(a, b), EventSelector(select = Pick.LATEST), HB)
        val backwards = EventPicker.pick(listOf(b, a), EventSelector(select = Pick.LATEST), HB)

        // Same data, different query order, same answer — otherwise the value carried over would
        // depend on the device.
        assertEquals(forwards?.uid, backwards?.uid)
        assertEquals("bbb", forwards?.uid)
    }

    @Test
    fun `lastUpdated breaks a tie before uid does`() {
        val older = event("aaa", 5, mapOf(HB to "1"), lastUpdated = 100L)
        val newer = event("zzz", 5, mapOf(HB to "2"), lastUpdated = 500L)

        val picked = EventPicker.pick(listOf(newer, older), EventSelector(select = Pick.LATEST), HB)

        assertEquals("zzz", picked?.uid)
    }

    // ─── Other picks ─────────────────────────────────────────────────────────

    @Test
    fun `triggering picks the event that fired the run and nothing else`() {
        val events = listOf(event("e1", 1, mapOf(HB to "1")), event("e2", 9, mapOf(HB to "2")))

        val picked = EventPicker.pick(
            events,
            EventSelector(select = Pick.TRIGGERING),
            HB,
            triggeringEventUid = "e1",
        )

        assertEquals("e1", picked?.uid)
    }

    @Test
    fun `triggering resolves to nothing when the run supplied no event`() {
        val events = listOf(event("e1", 1, mapOf(HB to "1")))

        assertNull(EventPicker.pick(events, EventSelector(select = Pick.TRIGGERING), HB, null))
    }

    @Test
    fun `nth indexes qualifying events chronologically`() {
        val events = listOf(
            event("third", 30, mapOf(HB to "3")),
            event("first", 10, mapOf(HB to "1")),
            event("second", 20, mapOf(HB to "2")),
        )

        val picked = EventPicker.pick(events, EventSelector(select = Pick.NTH, index = 1), HB)

        assertEquals("second", picked?.uid)
    }

    @Test
    fun `nth out of range yields nothing rather than the nearest event`() {
        val events = listOf(event("only", 1, mapOf(HB to "1")))

        assertNull(EventPicker.pick(events, EventSelector(select = Pick.NTH, index = 4), HB))
    }

    // ─── Boolean conditions ──────────────────────────────────────────────────

    @Test
    fun `is true matches every encoding DHIS2 uses for a boolean`() {
        val given = "bcgGivenDe1"
        listOf("true", "1", "yes", "TRUE").forEach { encoding ->
            val events = listOf(event("visit", 5, mapOf(given to encoding)))

            val picked = EventPicker.pick(
                events,
                EventSelector(
                    select = Pick.FIRST,
                    where = listOf(EventCondition(given, ConditionOp.IS_TRUE, "")),
                ),
                null,
            )

            // A strict `EQUALS "true"` would match nothing for "1" — a mapping that silently never
            // fires rather than one that reports a problem.
            assertEquals("failed for encoding '$encoding'", "visit", picked?.uid)
        }
    }

    @Test
    fun `is true does not match a false or absent value`() {
        val given = "bcgGivenDe1"
        val selector = EventSelector(
            select = Pick.FIRST,
            where = listOf(EventCondition(given, ConditionOp.IS_TRUE, "")),
        )

        assertNull(EventPicker.pick(listOf(event("a", 1, mapOf(given to "false"))), selector, null))
        assertNull(EventPicker.pick(listOf(event("b", 1, mapOf(given to "0"))), selector, null))
        assertNull(EventPicker.pick(listOf(event("c", 1, emptyMap())), selector, null))
    }

    @Test
    fun `the date of the visit where a flag was set is selectable`() {
        // The real shape of "BCG date given": the date is the event's own, and the flag picks which
        // event. FIRST, because the first recorded administration is the date it was given.
        val given = "bcgGivenDe1"
        val events = listOf(
            event("screening", 10, mapOf(given to "false")),
            event("immunisation", 20, mapOf(given to "true")),
            event("followup", 30, mapOf(given to "true")),
        )

        val picked = EventPicker.pick(
            events,
            EventSelector(
                select = Pick.FIRST,
                where = listOf(EventCondition(given, ConditionOp.IS_TRUE, "")),
            ),
            null,
        )

        assertEquals("immunisation", picked?.uid)
        assertEquals(20 * 86_400_000L, picked?.eventDate)
    }

    @Test
    fun `no qualifying event yields nothing`() {
        val events = listOf(event("v1", 1, mapOf(VISIT_TYPE to "ANC2")))

        assertNull(
            EventPicker.pick(
                events,
                EventSelector(where = listOf(EventCondition(VISIT_TYPE, ConditionOp.EQUALS, "ANC1"))),
                HB,
            ),
        )
    }
}
