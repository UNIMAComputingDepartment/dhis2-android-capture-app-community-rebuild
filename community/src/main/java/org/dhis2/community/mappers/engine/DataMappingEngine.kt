package org.dhis2.community.mappers.engine

import org.dhis2.community.mappers.config.MetadataLookup
import org.dhis2.community.mappers.models.DhisValueType
import org.dhis2.community.mappers.models.FailReason
import org.dhis2.community.mappers.models.FieldMeta
import org.dhis2.community.mappers.models.MappingOutcome
import org.dhis2.community.mappers.models.MappingPlan
import org.dhis2.community.mappers.models.MappingReport
import org.dhis2.community.mappers.models.Pick
import org.dhis2.community.mappers.models.PlanEntry
import org.dhis2.community.mappers.models.ResolvedMapping
import org.dhis2.community.mappers.models.SkipReason
import org.dhis2.community.mappers.models.TargetEventPolicy
import org.dhis2.community.mappers.models.TriggerContext
import org.dhis2.community.mappers.models.ValidatedConfig
import org.dhis2.community.mappers.models.ValueAddress
import org.dhis2.community.mappers.resolve.EventPicker
import org.dhis2.community.mappers.resolve.ResolvedValue
import org.dhis2.community.mappers.resolve.StoredValue
import org.dhis2.community.mappers.resolve.TrackerDataSource
import org.dhis2.community.mappers.resolve.ValueResolver
import org.dhis2.community.mappers.transform.TransformContext
import org.dhis2.community.mappers.transform.TransformPipeline
import org.dhis2.community.mappers.transform.TransformResult
import org.dhis2.community.mappers.transform.Transforms
import org.dhis2.community.mappers.write.Decision
import org.dhis2.community.mappers.write.LedgerEntry
import org.dhis2.community.mappers.write.MappingLedger
import org.dhis2.community.mappers.write.WriteGate
import timber.log.Timber
import java.time.LocalDate

/**
 * Runs cross-program data mapping.
 *
 * The [plan]/[apply] split is the design's central safety property: [plan] performs every read,
 * conversion and policy decision and produces a complete description of what *would* happen, with no
 * side effects at all. [apply] does nothing but execute a plan's approved writes. One code path
 * therefore serves dry-run previews, unit tests and production, so what a diagnostics screen shows is
 * exactly what will run — not a reimplementation that can drift from it.
 */
class DataMappingEngine(
    private val configProvider: () -> ValidatedConfig,
    private val resolver: ValueResolver,
    private val data: TrackerDataSource,
    private val metadata: MetadataLookup,
    private val ledger: MappingLedger,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    // ─── Planning (read-only) ────────────────────────────────────────────────

    fun plan(context: TriggerContext): MappingPlan {
        val config = configProvider()

        val applicable = config.transfers.filter { transfer ->
            context.trigger in transfer.triggers &&
                (context.targetProgramUid == null || transfer.toProgramUid == context.targetProgramUid) &&
                (context.sourceProgramUid == null || transfer.fromProgramUid == context.sourceProgramUid)
        }

        val entries = applicable.flatMap { transfer ->
            val targetEnrollmentUid = targetEnrollment(transfer.toProgramUid, context)
            transfer.resolvedMappings.map { mapping ->
                planOne(mapping, context, transfer.toProgramUid, targetEnrollmentUid, config)
            }
        }

        return MappingPlan(context, entries, config.errors)
    }

    private fun planOne(
        mapping: ResolvedMapping,
        context: TriggerContext,
        targetProgramUid: String,
        targetEnrollmentUid: String?,
        config: ValidatedConfig,
    ): PlanEntry {
        val source = resolver.resolve(mapping.from, context)

        if (!source.isPresent) {
            val reason = if (mapping.from is ValueAddress.DataElement && source.eventUid == null) {
                SkipReason.NO_SOURCE_EVENT
            } else {
                SkipReason.NO_SOURCE_VALUE
            }
            return PlanEntry(
                mapping = mapping,
                outcome = MappingOutcome.Skipped(reason),
                sourceEventUid = source.eventUid,
            )
        }

        val transformContext = TransformContext(asOf = resolveAsOf(mapping, context))
        val (result, trace) = TransformPipeline.run(mapping.pipeline, source.value!!, transformContext)

        val converted = when (result) {
            is TransformResult.Failure -> return PlanEntry(
                mapping = mapping,
                outcome = MappingOutcome.Failed(result.reason, result.detail),
                sourceRaw = source.value,
                sourceEventUid = source.eventUid,
                transformTrace = trace,
            )

            TransformResult.Empty -> return PlanEntry(
                mapping = mapping,
                outcome = MappingOutcome.Skipped(
                    SkipReason.NO_SOURCE_VALUE,
                    "conversion produced no value",
                ),
                sourceRaw = source.value,
                sourceEventUid = source.eventUid,
                transformTrace = trace,
            )

            // Clearing carries no value of its own; it is an instruction about the target.
            TransformResult.Clear -> ""

            is TransformResult.Success -> result.value
        }

        val clearing = result is TransformResult.Clear

        // Final gate before the value is considered writable: the server would reject an out-of-range
        // or over-long value, and a rejected payload blocks the whole sync rather than just this field.
        // A clear has no value to range-check.
        if (!clearing) targetFieldMeta(mapping.to)?.let { meta ->
            valueTypeViolation(converted, meta)?.let { detail ->
                return PlanEntry(
                    mapping = mapping,
                    outcome = MappingOutcome.Failed(FailReason.TYPE_MISMATCH, detail),
                    sourceRaw = source.value,
                    sourceEventUid = source.eventUid,
                    transformTrace = trace,
                )
            }
        }

        val target = resolveTarget(mapping.to, context, targetProgramUid, targetEnrollmentUid, config)
        if (target is TargetResolution.Unavailable) {
            return PlanEntry(
                mapping = mapping,
                outcome = MappingOutcome.Skipped(target.reason, target.detail),
                sourceRaw = source.value,
                sourceEventUid = source.eventUid,
                transformTrace = trace,
            )
        }

        val located = target as TargetResolution.Located
        val decision = WriteGate.decide(
            newValue = converted,
            current = located.current,
            ledger = ledger.lastWrite(context.teiUid, targetKey(mapping, located.eventUid)),
            policy = mapping.policy,
            sourceLastUpdated = source.lastUpdated,
            clearing = clearing,
        )

        val outcome = when (decision) {
            Decision.Write ->
                MappingOutcome.Applied(mapping.to, converted, located.current.value, clears = clearing)
            is Decision.Skip -> MappingOutcome.Skipped(decision.reason, decision.detail)
            is Decision.Fail -> MappingOutcome.Failed(decision.reason, decision.detail)
        }

        return PlanEntry(
            mapping = mapping,
            outcome = outcome,
            sourceRaw = source.value,
            sourceEventUid = source.eventUid,
            transformTrace = trace,
            targetEventUid = located.eventUid,
            sourceLastUpdated = source.lastUpdated,
        )
    }

    // ─── Applying (the only writes) ──────────────────────────────────────────

    fun apply(plan: MappingPlan): MappingReport {
        val settings = configProvider().settings
        val results = mutableListOf<PlanEntry>()

        for (entry in plan.entries) {
            val outcome = entry.outcome
            if (outcome !is MappingOutcome.Applied) {
                results += entry
                if (outcome is MappingOutcome.Failed && settings.failFast) {
                    Timber.w("Mapping %s failed and failFast is on; stopping", entry.mapping.id)
                    break
                }
                continue
            }

            results += try {
                write(entry, outcome, plan.context)
                entry
            } catch (e: Exception) {
                Timber.e(e, "Mapping %s failed to write", entry.mapping.id)
                val failed = entry.copy(
                    outcome = MappingOutcome.Failed(
                        FailReason.WRITE_FAILED,
                        e.message ?: e::class.java.simpleName,
                    ),
                )
                if (settings.failFast) {
                    results[results.lastIndex] = failed
                    break
                }
                failed
            }
        }

        val report = MappingReport.from(results)
        report.entries.filter { it.outcome is MappingOutcome.Failed }.forEach { entry ->
            val failure = entry.outcome as MappingOutcome.Failed
            // Failures are never silent: a value that did not arrive always has a recorded reason.
            Timber.w("Mapping %s: %s — %s", entry.mapping.id, failure.reason, failure.detail)
        }
        return report
    }

    /** Plan then apply in one step, for callers that do not need to inspect the plan. */
    fun run(context: TriggerContext): MappingReport = apply(plan(context))

    private fun write(entry: PlanEntry, outcome: MappingOutcome.Applied, context: TriggerContext) {
        // Null removes the stored value; anything else sets it.
        val payload = if (outcome.clears) null else outcome.value

        when (val address = entry.mapping.to) {
            is ValueAddress.Attribute ->
                data.setAttributeValue(context.teiUid, address.uid, payload)

            is ValueAddress.DataElement -> {
                val eventUid = entry.targetEventUid
                    ?: error("planned a data element write with no target event")
                data.setDataValue(eventUid, address.uid, payload)
            }

            is ValueAddress.EnrollmentProperty,
            is ValueAddress.EventProperty,
            is ValueAddress.Constant,
            -> error("${address::class.java.simpleName} is not a writable target")
        }

        ledger.record(
            teiUid = context.teiUid,
            targetKey = targetKey(entry.mapping, entry.targetEventUid),
            entry = LedgerEntry(
                mappingId = entry.mapping.id,
                writtenValue = outcome.value,
                writtenAt = System.currentTimeMillis(),
            ),
        )
    }

    // ─── Target resolution ───────────────────────────────────────────────────

    private sealed interface TargetResolution {
        data class Located(val current: StoredValue, val eventUid: String?) : TargetResolution
        data class Unavailable(val reason: SkipReason, val detail: String? = null) : TargetResolution
    }

    private fun resolveTarget(
        address: ValueAddress,
        context: TriggerContext,
        targetProgramUid: String,
        targetEnrollmentUid: String?,
        config: ValidatedConfig,
    ): TargetResolution = when (address) {
        is ValueAddress.Attribute -> TargetResolution.Located(
            data.attributeValue(context.teiUid, address.uid),
            null,
        )

        is ValueAddress.DataElement -> {
            val enrollmentUid = targetEnrollmentUid
                ?: return TargetResolution.Unavailable(
                    SkipReason.NO_TARGET_EVENT,
                    "no enrollment in target program $targetProgramUid",
                )

            val existing = EventPicker.pick(
                candidates = data.events(listOf(enrollmentUid), address.stageUid),
                // A target is an event to write *into*, so "the latest one that already has a value"
                // is the wrong question — it would skip precisely the empty events we want to fill.
                selector = address.event.copy(select = address.event.select.forWriting()),
                dataElementUid = null,
                triggeringEventUid = context.sourceEventUid,
            )

            when {
                existing != null -> TargetResolution.Located(
                    data.dataValue(existing.uid, address.uid),
                    existing.uid,
                )

                config.settings.targetEventPolicy == TargetEventPolicy.CREATE_IF_MISSING -> {
                    val orgUnit = data.enrollments(context.teiUid, targetProgramUid)
                        .firstOrNull { it.uid == enrollmentUid }
                        ?.orgUnitUid
                        ?: return TargetResolution.Unavailable(
                            SkipReason.NO_TARGET_EVENT,
                            "target enrollment has no organisation unit",
                        )
                    val created = data.createEvent(
                        enrollmentUid = enrollmentUid,
                        programUid = targetProgramUid,
                        stageUid = address.stageUid,
                        teiUid = context.teiUid,
                        orgUnitUid = orgUnit,
                    )
                    TargetResolution.Located(StoredValue(null), created)
                }

                else -> TargetResolution.Unavailable(
                    SkipReason.NO_TARGET_EVENT,
                    "stage ${address.stageUid} has no event and targetEventPolicy is REQUIRE_EXISTING",
                )
            }
        }

        is ValueAddress.EnrollmentProperty,
        is ValueAddress.EventProperty,
        is ValueAddress.Constant,
        -> TargetResolution.Unavailable(
            SkipReason.DIRECTION_BLOCKED,
            "${address::class.java.simpleName} cannot be written",
        )
    }

    private fun targetEnrollment(targetProgramUid: String, context: TriggerContext): String? {
        context.targetEnrollmentUid?.let { declared ->
            // Only trust the supplied enrollment when it really belongs to this transfer's target.
            val enrollments = data.enrollments(context.teiUid, targetProgramUid)
            if (enrollments.any { it.uid == declared }) return declared
        }
        return data.enrollments(context.teiUid, targetProgramUid)
            .sortedWith(compareBy({ it.enrollmentDate ?: 0L }, { it.uid }))
            .lastOrNull()
            ?.uid
    }

    // ─── Guards ──────────────────────────────────────────────────────────────

    private fun targetFieldMeta(address: ValueAddress): FieldMeta? = when (address) {
        is ValueAddress.Attribute -> metadata.attribute(address.uid)
        is ValueAddress.DataElement -> metadata.dataElement(address.uid)
        else -> null
    }

    /** Value-level checks the type system cannot express, returning a reason when the value is illegal. */
    private fun valueTypeViolation(value: String, meta: FieldMeta): String? {
        if (meta.valueType.isNumeric) {
            val number = value.toDoubleOrNull()
                ?: return "'$value' is not numeric but ${meta.name} is ${meta.valueType}"
            if (!meta.valueType.permits(number)) {
                return "$number is outside the range allowed by ${meta.valueType} on ${meta.name}"
            }
        }

        if (meta.hasOptionSet && value !in meta.optionCodes) {
            return "'$value' is not a code in ${meta.name}'s option set; the server would reject it"
        }

        val maxLength = when (meta.valueType) {
            DhisValueType.LETTER -> 1
            DhisValueType.TEXT, DhisValueType.PHONE_NUMBER, DhisValueType.EMAIL,
            DhisValueType.URL, DhisValueType.USERNAME,
            -> MAX_TEXT_LENGTH

            else -> null
        }
        if (maxLength != null && value.length > maxLength) {
            return "value is ${value.length} characters but ${meta.name} (${meta.valueType}) allows " +
                "$maxLength; add an explicit TRUNCATE transform if shortening is acceptable"
        }

        return null
    }

    /**
     * Reference date for age derivations. `TODAY` uses the clock; anything else is read as a date
     * address on the source side, so "age at booking" and "age today" stay distinguishable.
     */
    private fun resolveAsOf(mapping: ResolvedMapping, context: TriggerContext): LocalDate {
        val spec = mapping.pipeline
            .filterIsInstance<org.dhis2.community.mappers.models.TransformSpec.AgeFrom>()
            .firstOrNull()
            ?: return today()

        if (spec.asOf.equals(TransformPipeline.ASOF_TODAY, ignoreCase = true)) return today()

        Transforms.parseDate(spec.asOf)?.let { return it }

        // A named reference we cannot resolve falls back to today rather than failing the mapping,
        // but says so, because a silently shifted reference date is a subtle wrong-age bug.
        Timber.w("Mapping %s has unresolvable asOf '%s'; using today", mapping.id, spec.asOf)
        return today()
    }

    private fun targetKey(mapping: ResolvedMapping, targetEventUid: String?): String =
        when (val address = mapping.to) {
            is ValueAddress.Attribute -> "attr:${address.uid}"
            is ValueAddress.DataElement -> "de:${targetEventUid ?: address.stageUid}:${address.uid}"
            is ValueAddress.EnrollmentProperty -> "enrollment:${address.property}"
            is ValueAddress.EventProperty -> "event:${address.stageUid}:${address.property}"
            is ValueAddress.Constant -> "const"
        }

    private companion object {
        const val MAX_TEXT_LENGTH = 50_000
    }
}

/**
 * The write-side equivalent of a read selector.
 *
 * `*_WITH_VALUE` exists to avoid reading a blank; when choosing an event to write into, that
 * preference inverts and would skip the empty events that most need filling.
 */
private fun Pick.forWriting(): Pick = when (this) {
    Pick.LATEST_WITH_VALUE -> Pick.LATEST
    Pick.FIRST_WITH_VALUE -> Pick.FIRST
    else -> this
}
