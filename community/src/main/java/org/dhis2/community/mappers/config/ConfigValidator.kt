package org.dhis2.community.mappers.config

import org.dhis2.community.mappers.models.Binding
import org.dhis2.community.mappers.models.Canonical
import org.dhis2.community.mappers.models.CanonicalType
import org.dhis2.community.mappers.models.Concept
import org.dhis2.community.mappers.models.ConfigError
import org.dhis2.community.mappers.models.Direction
import org.dhis2.community.mappers.models.ErrorSeverity
import org.dhis2.community.mappers.models.FieldMeta
import org.dhis2.community.mappers.models.MappingSettings
import org.dhis2.community.mappers.models.ResolvedMapping
import org.dhis2.community.mappers.models.TransformSpec
import org.dhis2.community.mappers.models.Trigger
import org.dhis2.community.mappers.models.UnmatchedPolicy
import org.dhis2.community.mappers.models.ValidatedConfig
import org.dhis2.community.mappers.models.ValidatedTransfer
import org.dhis2.community.mappers.models.ValueAddress
import org.dhis2.community.mappers.models.WritePolicy
import org.dhis2.community.mappers.models.DhisValueType
import org.dhis2.community.mappers.models.OptionAction
import org.dhis2.community.mappers.transform.PipelineResult
import org.dhis2.community.mappers.transform.TransformPipeline

/**
 * Compiles a [ParsedConfig] into executable [ResolvedMapping]s, checking every one against live
 * metadata first.
 *
 * This is where the design's promise is kept: a mapping only reaches the engine if a provably correct
 * conversion for it exists. Anything doubtful becomes a [ConfigError] and is dropped, because for
 * clinical data "carry nothing" is always a safe outcome and "carry something approximate" never is.
 *
 * Errors are returned, never thrown — one bad concept must not take down the rest of the config.
 */
class ConfigValidator(private val metadata: MetadataLookup) {

    fun validate(parsed: ParsedConfig): ValidatedConfig {
        val errors = parsed.errors.toMutableList()
        val conceptsById = parsed.concepts.associateBy { it.id }

        val transfers = parsed.transfers.mapNotNull { transfer ->
            if (!metadata.programExists(transfer.fromProgramUid)) {
                errors += ConfigError(
                    ErrorSeverity.ERROR,
                    "Transfer '${transfer.id}' source program ${transfer.fromProgramUid} is not in local metadata",
                    transfer.id,
                )
                return@mapNotNull null
            }
            if (!metadata.programExists(transfer.toProgramUid)) {
                errors += ConfigError(
                    ErrorSeverity.ERROR,
                    "Transfer '${transfer.id}' target program ${transfer.toProgramUid} is not in local metadata",
                    transfer.id,
                )
                return@mapNotNull null
            }
            if (transfer.fromProgramUid == transfer.toProgramUid) {
                errors += ConfigError(
                    ErrorSeverity.ERROR,
                    "Transfer '${transfer.id}' has the same source and target program",
                    transfer.id,
                )
                return@mapNotNull null
            }

            // Declared in the model but not yet dispatched by any call site. Warned about rather than
            // ignored, so a transfer relying on it is not left looking configured while never firing.
            if (Trigger.TARGET_ENROLLMENT_FORM_OPEN in transfer.triggers) {
                errors += ConfigError(
                    ErrorSeverity.WARNING,
                    "Transfer '${transfer.id}' uses TARGET_ENROLLMENT_FORM_OPEN, which is not wired yet; " +
                        "add TARGET_ENROLLMENT_CREATED or SOURCE_EVENT_SAVED for it to run",
                    transfer.id,
                )
            }

            val mappings = mutableListOf<ResolvedMapping>()

            transfer.conceptIds.forEach { conceptId ->
                val concept = conceptsById[conceptId]
                if (concept == null) {
                    errors += ConfigError(
                        ErrorSeverity.ERROR,
                        "Transfer '${transfer.id}' references unknown concept '$conceptId'",
                        transfer.id,
                    )
                    return@forEach
                }
                resolveConceptMapping(transfer, concept, parsed.settings, errors)?.let { mappings += it }
            }

            ValidatedTransfer(
                id = transfer.id,
                fromProgramUid = transfer.fromProgramUid,
                toProgramUid = transfer.toProgramUid,
                triggers = transfer.triggers,
                resolvedMappings = dropAmbiguousTargets(transfer.id, mappings, errors),
            )
        }

        warnOnUnusedBindings(parsed.concepts, parsed.transfers, errors)

        return ValidatedConfig(parsed.settings, parsed.concepts, transfers, errors)
    }

    /**
     * Reports concepts and bindings that no transfer will ever use.
     *
     * A transfer names its concepts explicitly, so adding a binding to a concept does nothing until
     * some transfer is also updated to carry it between that binding's program and another. Without
     * this check that omission is completely silent — the config looks complete and the value simply
     * never arrives, which is the same "configured but never fires" failure the engine guards against
     * everywhere else.
     *
     * Deliberately evaluated against *declared* transfers rather than the surviving validated ones: a
     * binding orphaned because its transfer was rejected is already explained by that rejection, and
     * repeating it here would bury the real cause in noise.
     */
    private fun warnOnUnusedBindings(
        concepts: List<Concept>,
        transfers: List<ParsedTransfer>,
        errors: MutableList<ConfigError>,
    ) {
        concepts.forEach { concept ->
            val carrying = transfers.filter { concept.id in it.conceptIds }

            if (carrying.isEmpty()) {
                errors += ConfigError(
                    ErrorSeverity.WARNING,
                    "Concept '${concept.id}' is not carried by any transfer, so it will never move any " +
                        "data. Add it to a transfer's concept list",
                    concept.id,
                )
                return@forEach
            }

            val wiredPrograms = carrying
                .flatMap { listOf(it.fromProgramUid, it.toProgramUid) }
                .toSet()

            concept.bindings
                .map { it.programUid }
                .distinct()
                .filterNot { it in wiredPrograms }
                .forEach { programUid ->
                    val programLabel = metadata.programName(programUid) ?: programUid
                    errors += ConfigError(
                        ErrorSeverity.WARNING,
                        "Concept '${concept.id}' has a binding for $programLabel ($programUid) that no " +
                            "transfer carrying this concept references, so that binding will never " +
                            "transfer. Add a transfer to or from that program, or remove the binding",
                        concept.id,
                    )
                }
        }
    }

    // ─── Concept-derived mappings ────────────────────────────────────────────

    private fun resolveConceptMapping(
        transfer: ParsedTransfer,
        concept: Concept,
        settings: MappingSettings,
        errors: MutableList<ConfigError>,
    ): ResolvedMapping? {
        val context = "${transfer.id}/${concept.id}"

        val sourceBinding = concept.bindings.firstOrNull { it.programUid == transfer.fromProgramUid }
        val targetBinding = concept.bindings.firstOrNull { it.programUid == transfer.toProgramUid }

        if (sourceBinding == null) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '${concept.id}' has no binding for source program ${transfer.fromProgramUid}",
                context,
            )
            return null
        }
        if (targetBinding == null) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '${concept.id}' has no binding for target program ${transfer.toProgramUid}",
                context,
            )
            return null
        }

        if (sourceBinding.direction == Direction.WRITE_ONLY) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '${concept.id}' binding for ${transfer.fromProgramUid} is WRITE_ONLY so it cannot be " +
                    "a source. If reading it really is safe, set direction to BOTH deliberately",
                context,
            )
            return null
        }
        if (targetBinding.direction == Direction.READ_ONLY) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '${concept.id}' binding for ${transfer.toProgramUid} is READ_ONLY so it cannot be a target",
                context,
            )
            return null
        }

        if (concept.canonical.type == CanonicalType.OPTION && concept.canonical.codes.isNullOrEmpty()) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '${concept.id}' is an OPTION concept but declares no canonical codes",
                context,
            )
            return null
        }

        // An event date is a property of a clinical record, not a field within it. Writing one would
        // move the event in time, which a value mapping has no business doing silently.
        if (targetBinding.at is ValueAddress.EventProperty) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '${concept.id}' binding for ${transfer.toProgramUid} targets an event property, " +
                    "which is source-only — writing it would change when the event happened",
                context,
            )
            return null
        }

        val sourceMeta = fieldMeta(sourceBinding.at, context, "source", errors) ?: return null
        val targetMeta = fieldMeta(targetBinding.at, context, "target", errors) ?: return null

        if (!checkAddress(sourceBinding.at, sourceBinding.programUid, context, "source", errors)) return null
        if (!checkAddress(targetBinding.at, targetBinding.programUid, context, "target", errors)) return null
        if (!checkNotAliased(sourceBinding.at, targetBinding.at, context, errors)) return null

        warnOnUnmappedOptionCodes(concept, sourceBinding, sourceMeta, context, errors)
        warnOnUnreachableCanonicalCodes(concept, targetBinding, targetMeta, context, errors)
        warnOnUnreachableClear(concept, targetBinding, targetMeta, settings, context, errors)

        val decode = TransformPipeline.decode(sourceBinding, concept.canonical, sourceMeta)
        if (decode is PipelineResult.Unsupported) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '${concept.id}' cannot be read from ${transfer.fromProgramUid}: ${decode.reason}",
                context,
            )
            return null
        }

        val encode = TransformPipeline.encode(targetBinding, concept.canonical, targetMeta)
        if (encode is PipelineResult.Unsupported) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '${concept.id}' cannot be written to ${transfer.toProgramUid}: ${encode.reason}",
                context,
            )
            return null
        }

        return ResolvedMapping(
            id = "${transfer.id}/${concept.id}",
            from = sourceBinding.at,
            to = targetBinding.at,
            pipeline = (decode as PipelineResult.Ok).steps + (encode as PipelineResult.Ok).steps,
            // The binding is the specific answer, settings the general one. Falling through to a
            // hardcoded SKIP_IF_PRESENT here — as this did — meant settings.defaultOnConflict was
            // parsed, surfaced in the config app, and then never consulted by anything.
            policy = targetBinding.onConflict ?: settings.defaultOnConflict,
            conceptId = concept.id,
        )
    }

    // ─── Individual checks ───────────────────────────────────────────────────

    private fun fieldMeta(
        address: ValueAddress,
        context: String,
        role: String,
        errors: MutableList<ConfigError>,
    ): FieldMeta? = when (address) {
        is ValueAddress.Attribute -> metadata.attribute(address.uid) ?: run {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "$context $role attribute ${address.uid} is not in local metadata",
                context,
            )
            null
        }

        is ValueAddress.DataElement -> metadata.dataElement(address.uid) ?: run {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "$context $role data element ${address.uid} is not in local metadata",
                context,
            )
            null
        }

        // Enrollment and event properties are engine-defined dates rather than metadata fields.
        is ValueAddress.EnrollmentProperty -> FieldMeta(
            uid = address.property,
            name = address.property,
            valueType = org.dhis2.community.mappers.models.DhisValueType.DATE,
        )

        is ValueAddress.EventProperty -> FieldMeta(
            uid = address.property,
            name = address.property,
            valueType = org.dhis2.community.mappers.models.DhisValueType.DATE,
        )

        is ValueAddress.Constant -> FieldMeta(
            uid = "constant",
            name = "constant",
            valueType = org.dhis2.community.mappers.models.DhisValueType.TEXT,
        )
    }

    /** Confirms an address actually belongs to the program it claims — catches uids pasted from elsewhere. */
    private fun checkAddress(
        address: ValueAddress,
        programUid: String,
        context: String,
        role: String,
        errors: MutableList<ConfigError>,
    ): Boolean = when (address) {
        is ValueAddress.Attribute -> {
            if (!metadata.attributeInProgram(address.uid, programUid)) {
                errors += ConfigError(
                    ErrorSeverity.WARNING,
                    "$context $role attribute ${address.uid} is not assigned to program $programUid; " +
                        "it will still be read/written at TEI level",
                    context,
                )
            }
            true
        }

        is ValueAddress.DataElement -> {
            var ok = true
            if (!metadata.stageBelongsToProgram(address.stageUid, programUid)) {
                errors += ConfigError(
                    ErrorSeverity.ERROR,
                    "$context $role stage ${address.stageUid} does not belong to program $programUid",
                    context,
                )
                ok = false
            }
            if (ok && !metadata.dataElementInStage(address.uid, address.stageUid)) {
                errors += ConfigError(
                    ErrorSeverity.ERROR,
                    "$context $role data element ${address.uid} is not collected by stage ${address.stageUid}",
                    context,
                )
                ok = false
            }
            if (ok && address.event.select == org.dhis2.community.mappers.models.Pick.NTH &&
                !metadata.isStageRepeatable(address.stageUid)
            ) {
                errors += ConfigError(
                    ErrorSeverity.WARNING,
                    "$context $role uses NTH event selection but stage ${address.stageUid} is not repeatable",
                    context,
                )
            }
            ok
        }

        is ValueAddress.EventProperty -> {
            if (!metadata.stageBelongsToProgram(address.stageUid, programUid)) {
                errors += ConfigError(
                    ErrorSeverity.ERROR,
                    "$context $role stage ${address.stageUid} does not belong to program $programUid",
                    context,
                )
                false
            } else {
                // Every condition on the selector reads a data element of that same stage.
                address.event.where.forEach { condition ->
                    if (!metadata.dataElementInStage(condition.dataElement, address.stageUid)) {
                        errors += ConfigError(
                            ErrorSeverity.ERROR,
                            "$context $role condition reads ${condition.dataElement}, which is not " +
                                "collected by stage ${address.stageUid}",
                            context,
                        )
                    }
                }
                true
            }
        }

        is ValueAddress.EnrollmentProperty, is ValueAddress.Constant -> true
    }

    /**
     * Rejects a mapping whose two ends are the same TEI-level attribute (failure mode F9).
     *
     * Attribute values are stored per-TEI in the SDK, not per-enrollment, so "copying" an attribute
     * onto itself does nothing — and if a transform were attached it would mutate the value both
     * programs read.
     */
    private fun checkNotAliased(
        from: ValueAddress,
        to: ValueAddress,
        context: String,
        errors: MutableList<ConfigError>,
    ): Boolean {
        if (from is ValueAddress.Attribute && to is ValueAddress.Attribute && from.uid == to.uid) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "$context maps attribute ${from.uid} onto itself. Attribute values are stored per-TEI, " +
                    "so both programs already share this value and the mapping would do nothing",
                context,
            )
            return false
        }
        return true
    }

    /**
     * Warns when the target has no way to record some canonical code.
     *
     * Programmes rarely agree on the full vocabulary: one records positive/negative/unknown, the next
     * only positive/negative. Such a mapping is perfectly valid and compiles — the gap is per *value*,
     * and `TranslateOptions` fails only when an unknown result actually turns up, which may be weeks
     * later and looks like an intermittent fault rather than a modelling decision.
     *
     * A warning, not an error: refusing the whole mapping would throw away the positive and negative
     * results that transfer correctly, which is worse than carrying most of the data and saying which
     * part cannot follow.
     */
    private fun warnOnUnreachableCanonicalCodes(
        concept: Concept,
        binding: Binding,
        meta: FieldMeta,
        context: String,
        errors: MutableList<ConfigError>,
    ) {
        if (concept.canonical.type != CanonicalType.OPTION) return
        val canonicalCodes = concept.canonical.codes?.filter { it.isNotBlank() }.orEmpty()
        if (canonicalCodes.isEmpty()) return

        val writeMap = binding.writeOptions?.filterValues { it.isNotBlank() }.orEmpty()
        val declared = binding.options?.filterValues { it.isNotBlank() }.orEmpty()
        val storable = when {
            // An explicit write map is the complete answer for this direction. A code the author
            // deliberately routed to an outcome is *decided*, not overlooked, so it counts as handled
            // and is not reported — warning about a choice already made is noise.
            writeMap.isNotEmpty() -> writeMap.keys
            declared.isNotEmpty() -> declared.values.toSet()
            // No map means identity, which the pipeline only permits when this field's own codes are
            // already canonical ones.
            meta.hasOptionSet -> meta.optionCodes.filter { it in canonicalCodes }.toSet()
            else -> return
        }

        val unreachable = canonicalCodes.filterNot { it in storable }
        if (unreachable.isNotEmpty()) {
            val consequence = if (binding.onUnmapped == UnmatchedPolicy.SKIP) {
                "will be left blank in ${meta.name}"
            } else {
                "will fail rather than transfer into ${meta.name}"
            }
            errors += ConfigError(
                ErrorSeverity.WARNING,
                "Concept '${concept.id}' binding for ${binding.programUid} has no code for " +
                    "${unreachable.sorted().joinToString()}; values of " +
                    "${if (unreachable.size == 1) "that code" else "those codes"} $consequence. " +
                    "Declare writeOptions to record them as another code",
                context,
            )
        }
    }

    /**
     * Warns when a mapping that removes values can never actually do so.
     *
     * Clearing only matters when the target holds something, and SKIP_IF_PRESENT declines to touch a
     * target that holds something — so the two together are a contradiction that produces no writes
     * and no errors, the "configured but never fires" shape this validator exists to catch.
     */
    private fun warnOnUnreachableClear(
        concept: Concept,
        binding: Binding,
        meta: FieldMeta,
        settings: MappingSettings,
        context: String,
        errors: MutableList<ConfigError>,
    ) {
        val policy = binding.onConflict ?: settings.defaultOnConflict
        if (policy != WritePolicy.SKIP_IF_PRESENT) return

        val clearsExplicitly = binding.writeOptions
            ?.values
            ?.any { OptionAction.normalise(it) == OptionAction.CLEAR } == true
        // A boolean concept written into TRUE_ONLY clears on false, whether or not it was asked to.
        val clearsImplicitly = concept.canonical.type == CanonicalType.BOOLEAN &&
            meta.valueType == DhisValueType.TRUE_ONLY

        if (!clearsExplicitly && !clearsImplicitly) return

        errors += ConfigError(
            ErrorSeverity.WARNING,
            "Concept '${concept.id}' binding for ${binding.programUid} removes the value in " +
                "${meta.name} when the source says no, but its conflict policy is SKIP_IF_PRESENT, " +
                "which never touches a target that holds a value — so the field would stay set. Use " +
                "OVERWRITE on this binding for the removal to take effect",
            context,
        )
    }

    /** Surfaces option-set codes the author never accounted for, before they fail silently in the field. */
    private fun warnOnUnmappedOptionCodes(
        concept: Concept,
        binding: Binding,
        meta: FieldMeta,
        context: String,
        errors: MutableList<ConfigError>,
    ) {
        if (concept.canonical.type != CanonicalType.OPTION) return
        val declared = binding.options ?: return
        if (!meta.hasOptionSet) return

        val unmapped = meta.optionCodes.filterNot { declared.containsKey(it) }
        if (unmapped.isNotEmpty()) {
            errors += ConfigError(
                ErrorSeverity.WARNING,
                "Concept '${concept.id}' binding for ${binding.programUid} does not map option code(s) " +
                    "${unmapped.sorted().joinToString()}; values using them will fail rather than transfer",
                context,
            )
        }

        val notInOptionSet = declared.keys.filterNot { it in meta.optionCodes }
        if (notInOptionSet.isNotEmpty()) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '${concept.id}' binding for ${binding.programUid} maps code(s) " +
                    "${notInOptionSet.sorted().joinToString()} that do not exist in option set " +
                    "${meta.optionSetUid}",
                context,
            )
        }
    }

    /**
     * Drops mappings that would write the same field as another (failure mode F8).
     *
     * Two sources competing for one target is resolved by luck at runtime, so it is rejected at load
     * instead. Both are dropped rather than picking the first: if the author did not decide, the
     * engine has no basis to.
     */
    private fun dropAmbiguousTargets(
        transferId: String,
        mappings: List<ResolvedMapping>,
        errors: MutableList<ConfigError>,
    ): List<ResolvedMapping> {
        val byTarget = mappings.groupBy { targetKey(it.to) }
        val ambiguous = byTarget.filterValues { it.size > 1 }

        ambiguous.forEach { (key, competing) ->
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Transfer '$transferId' has ${competing.size} mappings writing the same target ($key): " +
                    "${competing.map { it.id }.sorted().joinToString()}. All are dropped — pick one",
                transferId,
            )
        }

        return mappings.filterNot { targetKey(it.to) in ambiguous.keys }
    }

    /**
     * Identity of a write target. Excludes the event selector on purpose: a target event is chosen by
     * the engine's target-event policy, not by a source selector, so two mappings differing only in
     * their selector still collide.
     */
    private fun targetKey(address: ValueAddress): String = when (address) {
        is ValueAddress.Attribute -> "attr:${address.uid}"
        is ValueAddress.DataElement -> "de:${address.programUid}:${address.stageUid}:${address.uid}"
        is ValueAddress.EnrollmentProperty -> "enrollment:${address.programUid}:${address.property}"
        is ValueAddress.EventProperty -> "event:${address.programUid}:${address.stageUid}:${address.property}"
        is ValueAddress.Constant -> "const"
    }
}
