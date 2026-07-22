package org.dhis2.community.tasking.engine

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.dhis2.community.tasking.utils.Constants
import timber.log.Timber
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

abstract class TaskingEvaluator(
    private val repository: TaskingRepository
) {

    private val ISO_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * Resolves a task's due date.
     *
     * [taskConfig.period.rules] are evaluated in order; the [dueDate][TaskingConfig.ProgramTasks.TaskConfig.DueDateRule.dueDate]
     * of the first rule whose condition matches is used. If no rule matches (or none are configured),
     * [taskConfig.period.default] is used.
     *
     * [eventUid], when known, pins anchor resolution to the specific event that triggered this
     * evaluation (rather than "the latest matching event"), so recalculating the due date on a later
     * re-evaluation (see [org.dhis2.community.tasking.engine.UpdateEvaluator]) yields the same result.
     *
     * As a final safeguard, the resolved due date is never allowed to fall before the triggering
     * event's date (or today, if there is none) — a misconfigured or unanticipated condition can at
     * worst produce a due date of "now", never one already in the past.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    internal fun calculateDueDate(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        teiUid: String,
        programUid: String,
        eventUid: String? = null,
    ): String? {

        val today = Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

        val matchedRule = taskConfig.period.rules.orEmpty().firstOrNull { rule ->
            val results = evaluateConditions(
                conditions = rule,
                teiUid = teiUid,
                programUid = programUid,
                eventUid = eventUid
            )
            resolveCombination(rule.combination, results)
        }

        val spec = matchedRule?.dueDate ?: taskConfig.period.default

        val resolvedDate = resolveDueDate(spec, teiUid, programUid, eventUid, today)
            ?: return null

        val referenceDate = eventUid
            ?.let { repository.getEventDate(it) }
            ?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
            ?: today

        return if (resolvedDate.isBefore(referenceDate)) {
            Timber.tag(this::class.java.simpleName).w(
                "calculateDueDate: computed due date $resolvedDate for task " +
                    "'${taskConfig.name}' is before reference date $referenceDate — clamping"
            )
            referenceDate.toString()
        } else {
            resolvedDate.toString()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun resolveDueDate(
        spec: TaskingConfig.ProgramTasks.TaskConfig.DueDateSpec,
        teiUid: String,
        programUid: String,
        eventUid: String?,
        today: LocalDate,
    ): LocalDate? {

        if (spec.anchor.ref == Constants.QUARTERLY_SCHEDULE) {
            return quarterDatesCalculator().second.minusDays(spec.dueInDays.toLong())
        }

        if (spec.anchor.ref == Constants.TRIGGER_EVENT_DATE) {
            val eventDate = eventUid
                ?.let { repository.getEventDate(it) }
                ?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
                ?: today
            return eventDate.plusDays(spec.dueInDays.toLong())
        }

        if (spec.anchor.uid.isNullOrBlank() || teiUid.isBlank()) {
            return today.plusDays(spec.dueInDays.toLong())
        }

        val enrollmentUid = repository.getLatestEnrollment(teiUid, programUid)?.uid().orEmpty()

        // Anchors (e.g. EDD) are enrollment-level facts, not tied to whichever event
        // triggered this evaluation — search the whole enrollment.
        val periodAnchor = repository.getLatestEvent(
            programUid,
            spec.anchor.uid,
            enrollmentUid,
            eventUid = null
        )
            ?.trackedEntityDataValues()
            ?.firstOrNull { it.dataElement() == spec.anchor.uid }
            ?.value()

        if (periodAnchor.isNullOrBlank()) {
            return today.plusDays(spec.dueInDays.toLong())
        }

        val anchorDate = parseDate(periodAnchor) ?: return null

        return when (spec.anchor.ref) {
            Constants.PAST -> anchorDate.minusDays(spec.dueInDays.toLong())
            else -> anchorDate.plusDays(spec.dueInDays.toLong())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseDate(value: String): LocalDate? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.parse(value)
            ?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
    }

    /**
     * True if any due-date spec in [period] (the default or any rule) resolves from live data
     * (a data element/attribute anchor, a trigger-event date, or a quarterly schedule) rather than
     * always falling back to "today + N days". Callers use this to decide whether recomputing the
     * due date on re-evaluation is meaningful — an anchor-less spec always yields the same
     * creation-time-relative result recomputed from a new "today", which would just drift forward.
     */
    internal fun hasDynamicAnchor(period: TaskingConfig.ProgramTasks.TaskConfig.Period): Boolean {
        fun isDynamic(spec: TaskingConfig.ProgramTasks.TaskConfig.DueDateSpec) =
            !spec.anchor.uid.isNullOrBlank() ||
                spec.anchor.ref == Constants.QUARTERLY_SCHEDULE ||
                spec.anchor.ref == Constants.TRIGGER_EVENT_DATE

        return isDynamic(period.default) || period.rules.orEmpty().any { isDynamic(it.dueDate) }
    }

    private fun resolveCombination(combination: String?, results: List<Boolean>): Boolean {
        return when (combination) {
            Constants.AND -> results.all { it }
            Constants.OR -> results.any { it }
            else -> results.any { it }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    internal fun evaluateConditions(
        conditions: TaskingConfig.ProgramTasks.TaskConfig.HasConditions,
        teiUid: String,
        programUid: String,
        eventUid: String? = null,
        secondaryProgramUid: String? = null
    ): List<Boolean> {
        return conditions.condition.map { cond ->
            val lhsValue =
                this.resolvedReference(cond.lhs, teiUid, programUid, eventUid, secondaryProgramUid)
            val rhsValue =
                this.resolvedReference(cond.rhs, teiUid, programUid, eventUid, secondaryProgramUid)

            when (cond.op) {
                Constants.EQUALS -> rhsValue == lhsValue
                Constants.NUM_EQUALS -> rhsValue?.toDoubleOrNull() == lhsValue?.toDoubleOrNull()
                Constants.NOT_EQUAL -> rhsValue != lhsValue
                Constants.NOT_NULL -> !lhsValue.isNullOrEmpty()
                Constants.NULL -> lhsValue.isNullOrEmpty()
                Constants.GREATER_THAN -> compareNumericOrDate(lhsValue, rhsValue) { l, r -> l > r }
                Constants.LESS_THAN -> compareNumericOrDate(lhsValue, rhsValue) { l, r -> l < r }
                Constants.GREATER_THAN_OR_EQUALS -> compareNumericOrDate(lhsValue, rhsValue) { l, r -> l >= r }
                Constants.LESS_THAN_OR_EQUALS -> compareNumericOrDate(lhsValue, rhsValue) { l, r -> l <= r }

                Constants.QUARTERLY_SCHEDULE -> {
                    this.resolvedQuarterly(
                        ref = cond.lhs,
                        teiUid = teiUid,
                        programUid = programUid
                    ) ?: false
                }

                else -> false
            }
        }
    }

    /**
     * Numeric comparison, unless both sides look like ISO (`yyyy-MM-dd`) dates, in which case they
     * are compared as dates. Needed so conditions like "today is after `RzVVXJ3lCdS`" work — date
     * strings are not parseable as [Double].
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun compareNumericOrDate(
        lhsValue: String?,
        rhsValue: String?,
        compare: (Double, Double) -> Boolean
    ): Boolean {
        if (lhsValue == null || rhsValue == null) return false

        val bothDates = ISO_DATE_REGEX.matches(lhsValue) && ISO_DATE_REGEX.matches(rhsValue)
        return if (bothDates) {
            val lhs = LocalDate.parse(lhsValue).toEpochDay().toDouble()
            val rhs = LocalDate.parse(rhsValue).toEpochDay().toDouble()
            compare(lhs, rhs)
        } else {
            val lhs = lhsValue.toDoubleOrNull()
            val rhs = rhsValue.toDoubleOrNull()
            lhs != null && rhs != null && compare(lhs, rhs)
        }
    }

    internal fun getTeiAttributes(
        tieUid: String,
        tieView: TaskingConfig.ProgramTasks.TeiView
    ): Triple<String, String, String> {
        fun getAttr(uid: String) =
            repository.d2.trackedEntityModule().trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(tieUid)
                .byTrackedEntityAttribute().eq(uid)
                .one().blockingGet()?.value() ?: ""

        return Triple(
            getAttr(tieView.teiPrimaryAttribute),
            getAttr(tieView.teiSecondaryAttribute),
            getAttr(tieView.teiTertiaryAttribute)
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun resolvedReference(
        reference: TaskingConfig.ProgramTasks.TaskConfig.Reference,
        teiUid: String,
        programUid: String,
        eventUid: String? = null,
        secondaryProgramUid: String? = null
    ): String? {

        if (reference.ref == Constants.TODAY)
            return LocalDate.now().toString()

        val enrollment = repository.getLatestEnrollment(teiUid, programUid)
            ?: secondaryProgramUid?.let { repository.getLatestEnrollment(teiUid, it) }
            ?: return null

        if (reference.uid.isNullOrBlank())
            return reference.value.toString()

        return when (reference.ref) {
            Constants.TEI_ATTRIBUTE -> repository.d2.trackedEntityModule()
                .trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(teiUid)
                .byTrackedEntityAttribute().eq(reference.uid)
                .one().blockingGet()
                ?.value()

            Constants.EVENT_DATA -> {
                val latestEvent =
                    repository.getLatestEvent(programUid, reference.uid, enrollment.uid(), eventUid)
                latestEvent
                    ?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == reference.uid }
                    ?.value()
            }

            Constants.ALL_EVENTS_DATA -> {
                // Unlike EVENT_DATA, this searches the whole enrollment rather than the
                // specific triggering event — the value may live on a different event than
                // the one that triggered this evaluation.
                val latestEvent =
                    repository.getLatestEvent(programUid, reference.uid, enrollment.uid(), eventUid = null)
                latestEvent
                    ?.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == reference.uid }
                    ?.value()
            }

            Constants.STATIC -> reference.uid

            Constants.EVENTS_COUNT -> {
                // reference.value can be used to filter by a specific data value
                val stageUid = reference.type
                val expectedValue = reference.value?.toString()
                val count = repository.countEventsByDataValue(
                    programUid,
                    reference.uid,
                    enrollment.uid(),
                    stageUid,
                    expectedValue
                )
                count.toString()
            }

            //"static" -> reference.uid
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun quarterDatesCalculator(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()

        val adjustMonth = (today.monthValue - 1 + 12) % 12
        val currentQuarter = (adjustMonth / 3) + 1

        val quarterStartMonth = (currentQuarter - 1) * 3 + 1

        val quarterStart = LocalDate.of(today.year, quarterStartMonth, 1)
        val quarterEnd = quarterStart.plusMonths(3).minusDays(1)

        return quarterStart to quarterEnd
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun resolvedQuarterly(
        ref: TaskingConfig.ProgramTasks.TaskConfig.Reference,
        teiUid: String,
        programUid: String,
    ): Boolean? {

        val startDate = Date.from(
            quarterDatesCalculator().first.atStartOfDay(ZoneId.systemDefault()).toInstant()
        )
        val endDate = Date.from(
            quarterDatesCalculator().second.atStartOfDay(ZoneId.systemDefault()).toInstant()
        )

        val events = repository.getPeriodEventsForProgramStage(
            programUid = programUid,
            stageUid = ref.ref,
            teiUid = teiUid,
            startDate = startDate,
            endDate = endDate,
        )

        return events.isEmpty()
    }
}
