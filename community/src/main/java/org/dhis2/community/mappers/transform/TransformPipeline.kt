package org.dhis2.community.mappers.transform

import org.dhis2.community.mappers.models.AgeUnit
import org.dhis2.community.mappers.models.Binding
import org.dhis2.community.mappers.models.Canonical
import org.dhis2.community.mappers.models.CanonicalType
import org.dhis2.community.mappers.models.DerivationOp
import org.dhis2.community.mappers.models.DhisValueType
import org.dhis2.community.mappers.models.FieldMeta
import org.dhis2.community.mappers.models.TransformSpec
import org.dhis2.community.mappers.models.UnmatchedPolicy
import org.dhis2.community.mappers.models.OptionAction

/** Either a usable conversion pipeline, or the reason no safe one exists. */
sealed interface PipelineResult {
    data class Ok(val steps: List<TransformSpec>) : PipelineResult

    /**
     * No conversion exists that is guaranteed correct. The mapping is refused rather than
     * approximated — the whole point of the design.
     */
    data class Unsupported(val reason: String) : PipelineResult
}

/**
 * Derives conversion pipelines from what a binding *declares about itself* — its unit, its option
 * coding, its derivation — combined with the field's real value type from metadata.
 *
 * This is the "config declares meaning, engine derives mechanism" principle in code. Authors never
 * write a conversion; they state that their program records haemoglobin in `g/L` and the scaling
 * follows. That is what keeps failure modes F1–F3 from being one typo away.
 *
 * A transfer's pipeline is the composition `decode(source) + encode(target)`: source value into the
 * concept's canonical form, then canonical form into the target's encoding.
 */
object TransformPipeline {

    private const val ISO_DATE = "yyyy-MM-dd"

    /** Binding's stored form -> the concept's canonical form. */
    fun decode(binding: Binding, canonical: Canonical, meta: FieldMeta): PipelineResult {
        if (!meta.valueType.isMappable) {
            return PipelineResult.Unsupported(
                "${meta.name} has value type ${meta.valueType}, which cannot be carried between programs",
            )
        }

        // A derived binding computes its value *from* the canonical, so reading it back means
        // inverting a lossy function — fabricating precision that was never captured (F6).
        binding.derive?.let {
            return PipelineResult.Unsupported(
                "binding is derived via ${it.op} and cannot be read as a source; " +
                    "deriving in reverse would invent data",
            )
        }

        val steps = mutableListOf<TransformSpec>(TransformSpec.Trim)

        when (canonical.type) {
            CanonicalType.OPTION -> {
                when (val translation = optionTranslation(binding.options, meta.optionCodes, canonical.codes)) {
                    is OptionTranslation.Identity -> Unit
                    is OptionTranslation.Mapped ->
                        steps += TransformSpec.TranslateOptions(translation.map, UnmatchedPolicy.FAIL)
                    is OptionTranslation.Impossible ->
                        return PipelineResult.Unsupported(translation.reason)
                }
            }

            CanonicalType.NUMBER, CanonicalType.INTEGER -> {
                steps += TransformSpec.ToNumber
                val factor = UnitRegistry.factor(binding.unit, canonical.unit)
                    ?: return PipelineResult.Unsupported(unitError(binding.unit, canonical.unit))
                if (factor != 1.0) steps += TransformSpec.ScaleUnit(factor)
                if (canonical.type == CanonicalType.INTEGER) steps += TransformSpec.ToInteger
            }

            CanonicalType.DATE -> {
                if (!meta.valueType.isDateLike && !meta.valueType.isTextLike) {
                    return PipelineResult.Unsupported(
                        "${meta.name} is ${meta.valueType}; a DATE concept needs a date or text field",
                    )
                }
                steps += TransformSpec.FormatDate(ISO_DATE)
            }

            CanonicalType.DATETIME -> {
                if (!meta.valueType.isDateLike && !meta.valueType.isTextLike) {
                    return PipelineResult.Unsupported(
                        "${meta.name} is ${meta.valueType}; a DATETIME concept needs a date or text field",
                    )
                }
            }

            CanonicalType.BOOLEAN -> steps += TransformSpec.BooleanAs("true", "false")

            CanonicalType.TEXT -> Unit
        }

        return PipelineResult.Ok(steps)
    }

    /** The concept's canonical form -> binding's stored form. */
    fun encode(binding: Binding, canonical: Canonical, meta: FieldMeta): PipelineResult {
        if (!meta.valueType.isMappable) {
            return PipelineResult.Unsupported(
                "${meta.name} has value type ${meta.valueType}, which cannot be written by a mapping",
            )
        }

        val steps = mutableListOf<TransformSpec>()

        // A derivation replaces the normal canonical->binding encoding: the binding stores a computed
        // form (e.g. an age) rather than the canonical value itself.
        binding.derive?.let { derivation ->
            return when (derivation.op) {
                DerivationOp.AGE_FROM -> {
                    if (canonical.type != CanonicalType.DATE && canonical.type != CanonicalType.DATETIME) {
                        PipelineResult.Unsupported(
                            "AGE_FROM needs a DATE or DATETIME concept but ${canonical.type} was given",
                        )
                    } else if (!meta.valueType.isNumeric && !meta.valueType.isTextLike) {
                        PipelineResult.Unsupported(
                            "AGE_FROM produces a number but ${meta.name} is ${meta.valueType}",
                        )
                    } else {
                        val unit = parseAgeUnit(derivation.unit)
                            ?: return PipelineResult.Unsupported(
                                "AGE_FROM unit '${derivation.unit}' is not one of YEARS, MONTHS, DAYS",
                            )
                        PipelineResult.Ok(
                            listOf(TransformSpec.AgeFrom(unit, derivation.asOf ?: ASOF_TODAY)),
                        )
                    }
                }

                DerivationOp.PRESENCE -> when {
                    // The true code has to be the target's own encoding: "true" for a boolean field,
                    // the matching option code where the flag is an option set.
                    meta.valueType.isBooleanLike -> PipelineResult.Ok(
                        listOf(TransformSpec.Presence("true")),
                    )

                    meta.hasOptionSet -> {
                        val trueCode = binding.options
                            ?.entries
                            ?.firstOrNull { (_, canonicalCode) -> canonicalCode.equals("true", ignoreCase = true) }
                            ?.key
                            ?: return PipelineResult.Unsupported(
                                "${meta.name} uses an option set, so PRESENCE needs an option map entry " +
                                    "saying which code means true",
                            )
                        PipelineResult.Ok(listOf(TransformSpec.Presence(trueCode)))
                    }

                    meta.valueType.isTextLike -> PipelineResult.Ok(
                        listOf(TransformSpec.Presence("true")),
                    )

                    else -> PipelineResult.Unsupported(
                        "PRESENCE records a yes/no but ${meta.name} is ${meta.valueType}",
                    )
                }
            }
        }

        when (canonical.type) {
            CanonicalType.OPTION -> {
                // An explicit write map wins outright. It is the author saying, in so many words,
                // how this program records each canonical value — including collapsing several
                // onto one code, which no inversion could ever justify on its own.
                val explicit = binding.writeOptions
                    ?.filterKeys { it.isNotBlank() }
                    ?.filterValues { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }

                if (explicit != null) {
                    val canonicalCodes = canonical.codes?.filter { it.isNotBlank() }.orEmpty()
                    val notCanonical = explicit.keys.filterNot { it in canonicalCodes }
                    if (notCanonical.isNotEmpty()) {
                        return PipelineResult.Unsupported(
                            "write map is keyed by ${notCanonical.sorted().joinToString()}, which " +
                                "are not canonical codes; it maps canonical code -> this " +
                                "program's code, not the other way round",
                        )
                    }
                    if (meta.hasOptionSet) {
                        val unknown = explicit.values.distinct()
                            // Actions name an outcome rather than a code, so they are not expected
                            // to appear in the option set.
                            .filterNot { OptionAction.isAction(it) }
                            .filterNot { it in meta.optionCodes }
                        if (unknown.isNotEmpty()) {
                            return PipelineResult.Unsupported(
                                "write map targets ${unknown.sorted().joinToString()}, which are not " +
                                    "codes of ${meta.name}'s option set",
                            )
                        }
                    }
                    steps += TransformSpec.TranslateOptions(
                        explicit,
                        binding.onUnmapped ?: UnmatchedPolicy.FAIL,
                    )
                } else {
                    when (val forward = optionTranslation(binding.options, meta.optionCodes, canonical.codes)) {
                        is OptionTranslation.Identity -> Unit
                        is OptionTranslation.Impossible -> return PipelineResult.Unsupported(forward.reason)
                        is OptionTranslation.Mapped -> {
                            // Writing needs canonical -> binding, the inverse of the declared map. A
                            // many-to-one map has no well-defined inverse, so rather than picking one
                            // of the candidate codes we refuse. Declaring `writeOptions` is how an
                            // author overrides that deliberately.
                            val inverse = invert(forward.map)
                                ?: return PipelineResult.Unsupported(
                                    "option map is many-to-one, so there is no unambiguous canonical " +
                                        "-> program code direction. Declare writeOptions to say " +
                                        "which code each canonical value should be written as",
                                )
                            steps += TransformSpec.TranslateOptions(
                                inverse,
                                binding.onUnmapped ?: UnmatchedPolicy.FAIL,
                            )
                        }
                    }
                }
            }

            CanonicalType.NUMBER, CanonicalType.INTEGER -> {
                val factor = UnitRegistry.factor(canonical.unit, binding.unit)
                    ?: return PipelineResult.Unsupported(unitError(canonical.unit, binding.unit))
                if (factor != 1.0) steps += TransformSpec.ScaleUnit(factor)
                if (meta.valueType.isInteger) {
                    steps += TransformSpec.ToInteger
                } else if (!meta.valueType.isNumeric && !meta.valueType.isTextLike) {
                    return PipelineResult.Unsupported(
                        "${meta.name} is ${meta.valueType}; a numeric concept needs a numeric or text field",
                    )
                }
            }

            CanonicalType.DATE -> when {
                meta.valueType == DhisValueType.DATE || meta.valueType == DhisValueType.AGE ->
                    steps += TransformSpec.FormatDate(ISO_DATE)

                meta.valueType.isTextLike -> steps += TransformSpec.FormatDate(ISO_DATE)

                // A DATE concept has no time component. Writing it into a DATETIME field would mean
                // inventing a time, so the author must model that explicitly.
                meta.valueType == DhisValueType.DATETIME -> return PipelineResult.Unsupported(
                    "${meta.name} is DATETIME but the concept is DATE; a time of day would have to be " +
                        "invented — use a DATETIME concept instead",
                )

                else -> return PipelineResult.Unsupported(
                    "${meta.name} is ${meta.valueType}; a DATE concept needs a date or text field",
                )
            }

            CanonicalType.DATETIME -> if (!meta.valueType.isDateLike && !meta.valueType.isTextLike) {
                return PipelineResult.Unsupported(
                    "${meta.name} is ${meta.valueType}; a DATETIME concept needs a date or text field",
                )
            }

            CanonicalType.BOOLEAN -> when {
                // TRUE_ONLY holds a tick or nothing; there is no "false" to write. Storing the literal
                // string would be rejected by the server, and leaving the tick in place would state
                // the opposite of the source. Removing it is the only encoding of "no" that exists.
                meta.valueType == DhisValueType.TRUE_ONLY ->
                    steps += TransformSpec.BooleanAs("true", OptionAction.CLEAR)

                meta.valueType.isBooleanLike -> steps += TransformSpec.BooleanAs("true", "false")
                meta.hasOptionSet -> return PipelineResult.Unsupported(
                    "${meta.name} uses an option set, so a BOOLEAN concept needs an explicit option map",
                )
                meta.valueType.isTextLike -> steps += TransformSpec.BooleanAs("true", "false")
                else -> return PipelineResult.Unsupported(
                    "${meta.name} is ${meta.valueType}; cannot store a BOOLEAN concept",
                )
            }

            CanonicalType.TEXT -> when {
                meta.valueType.isTextLike -> Unit
                meta.valueType.isInteger -> steps += TransformSpec.ToInteger
                meta.valueType.isNumeric -> steps += TransformSpec.ToNumber
                meta.valueType.isDateLike -> steps += TransformSpec.FormatDate(ISO_DATE)
                else -> return PipelineResult.Unsupported(
                    "${meta.name} is ${meta.valueType}; cannot store a TEXT concept",
                )
            }
        }

        return PipelineResult.Ok(steps)
    }

    /** Runs a pipeline, returning the final value plus the value after each step for diagnostics. */
    fun run(
        steps: List<TransformSpec>,
        input: String,
        context: TransformContext,
    ): Pair<TransformResult, List<String>> {
        val trace = mutableListOf<String>()
        var current = input
        for (step in steps) {
            when (val result = Transforms.apply(step, current, context)) {
                is TransformResult.Success -> {
                    current = result.value
                    trace += "${step.describe()} -> $current"
                }

                TransformResult.Empty -> {
                    trace += "${step.describe()} -> (empty)"
                    return TransformResult.Empty to trace
                }

                TransformResult.Clear -> {
                    trace += "${step.describe()} -> (clear the target)"
                    return TransformResult.Clear to trace
                }

                is TransformResult.Failure -> {
                    trace += "${step.describe()} -> FAILED: ${result.detail}"
                    return result to trace
                }
            }
        }
        return TransformResult.Success(current) to trace
    }

    const val ASOF_TODAY = "TODAY"

    // ─── Option translation ──────────────────────────────────────────────────

    private sealed interface OptionTranslation {
        /** The field already speaks the canonical vocabulary; no translation needed. */
        object Identity : OptionTranslation
        data class Mapped(val map: Map<String, String>) : OptionTranslation
        data class Impossible(val reason: String) : OptionTranslation
    }

    /**
     * Resolves how a field's option codes relate to the canonical vocabulary.
     *
     * When no map is declared, identity is allowed *only* if the field's own codes are already a
     * subset of the canonical codes. Otherwise translation is refused rather than guessed: two option
     * sets whose codes happen to overlap numerically (`1`, `2`) mean entirely different things, and
     * assuming otherwise is failure mode F2.
     */
    private fun optionTranslation(
        declared: Map<String, String>?,
        fieldCodes: List<String>,
        canonicalCodes: List<String>?,
    ): OptionTranslation {
        val canonical = canonicalCodes?.filter { it.isNotBlank() }.orEmpty()
        if (canonical.isEmpty()) {
            return OptionTranslation.Impossible(
                "an OPTION concept must declare its canonical codes",
            )
        }

        // Reading turns a program's code into a canonical one, and every canonical code is a real
        // value. "Write nothing" is only meaningful in the other direction, so an action here is a
        // map filled in the wrong way round rather than a valid instruction.
        declared?.entries?.firstOrNull { OptionAction.isAction(it.value) }?.let { (code, action) ->
            return OptionTranslation.Impossible(
                "read map sends '$code' to $action, which is an outcome rather than a canonical " +
                    "code; outcomes belong on the write map",
            )
        }

        val map = declared?.filterKeys { it.isNotBlank() }?.filterValues { it.isNotBlank() }.orEmpty()
        if (map.isEmpty()) {
            if (fieldCodes.isEmpty()) {
                return OptionTranslation.Impossible(
                    "field has no option set and no option map was declared, so its codes cannot be " +
                        "related to the canonical vocabulary",
                )
            }
            val unknown = fieldCodes.filterNot { it in canonical }
            return if (unknown.isEmpty()) {
                OptionTranslation.Identity
            } else {
                OptionTranslation.Impossible(
                    "option codes ${unknown.sorted().joinToString()} are not in the canonical " +
                        "vocabulary and no option map was declared",
                )
            }
        }

        val notCanonical = map.values.distinct().filterNot { it in canonical }
        if (notCanonical.isNotEmpty()) {
            return OptionTranslation.Impossible(
                "option map targets ${notCanonical.sorted().joinToString()}, which are not canonical codes",
            )
        }
        return OptionTranslation.Mapped(map)
    }

    /** Inverts a code map, or returns null when it is many-to-one and therefore has no inverse. */
    private fun invert(map: Map<String, String>): Map<String, String>? {
        val inverse = mutableMapOf<String, String>()
        for ((from, to) in map) {
            if (inverse.containsKey(to)) return null
            inverse[to] = from
        }
        return inverse
    }

    private fun parseAgeUnit(raw: String?): AgeUnit? = when (raw?.trim()?.uppercase()) {
        null, "", "YEARS", "YEAR" -> AgeUnit.YEARS
        "MONTHS", "MONTH" -> AgeUnit.MONTHS
        "DAYS", "DAY" -> AgeUnit.DAYS
        else -> null
    }

    private fun unitError(from: String?, to: String?): String {
        val a = from?.takeIf { it.isNotBlank() } ?: "(none)"
        val b = to?.takeIf { it.isNotBlank() } ?: "(none)"
        return "cannot convert unit '$a' to '$b': unknown unit or different dimensions. " +
            "Use a registered unit, or declare the factor explicitly — the engine will not assume 1.0"
    }
}

/** Short human-readable form used in diagnostics traces. */
fun TransformSpec.describe(): String = when (this) {
    TransformSpec.Trim -> "trim"
    is TransformSpec.ScaleUnit -> "scale(x$factor${if (offset != 0.0) " +$offset" else ""})"
    is TransformSpec.TranslateOptions -> "translateOptions(${map.size} codes)"
    TransformSpec.ToNumber -> "toNumber"
    TransformSpec.ToInteger -> "toInteger"
    is TransformSpec.Round -> "round($decimals, $mode)"
    is TransformSpec.AgeFrom -> "ageFrom($unit, asOf=$asOf)"
    is TransformSpec.FormatDate -> "formatDate($pattern)"
    is TransformSpec.Truncate -> "truncate($maxLength)"
    is TransformSpec.BooleanAs -> "booleanAs($trueCode/$falseCode)"
    is TransformSpec.Presence -> "presence(-> $trueCode)"
}
