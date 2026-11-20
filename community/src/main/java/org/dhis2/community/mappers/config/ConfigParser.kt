package org.dhis2.community.mappers.config

import org.dhis2.community.mappers.models.AddressKind
import org.dhis2.community.mappers.models.AgeUnit
import org.dhis2.community.mappers.models.Binding
import org.dhis2.community.mappers.models.Canonical
import org.dhis2.community.mappers.models.CanonicalType
import org.dhis2.community.mappers.models.Concept
import org.dhis2.community.mappers.models.ConceptDto
import org.dhis2.community.mappers.models.ConditionOp
import org.dhis2.community.mappers.models.ConfigError
import org.dhis2.community.mappers.models.DataMapperConfigDto
import org.dhis2.community.mappers.models.Derivation
import org.dhis2.community.mappers.models.DerivationOp
import org.dhis2.community.mappers.models.Direction
import org.dhis2.community.mappers.models.EnrollmentScope
import org.dhis2.community.mappers.models.ErrorSeverity
import org.dhis2.community.mappers.models.EventCondition
import org.dhis2.community.mappers.models.EventPropertyName
import org.dhis2.community.mappers.models.EventSelector
import org.dhis2.community.mappers.models.MappingSettings
import org.dhis2.community.mappers.models.Pick
import org.dhis2.community.mappers.models.TargetEventPolicy
import org.dhis2.community.mappers.models.Trigger
import org.dhis2.community.mappers.models.UnmatchedPolicy
import org.dhis2.community.mappers.models.ValueAddress
import org.dhis2.community.mappers.models.ValueAddressDto
import org.dhis2.community.mappers.models.WritePolicy

/** Config after structural parsing, before metadata has been consulted. */
data class ParsedConfig(
    val settings: MappingSettings,
    val concepts: List<Concept>,
    val transfers: List<ParsedTransfer>,
    val errors: List<ConfigError>,
)

data class ParsedTransfer(
    val id: String,
    val fromProgramUid: String,
    val toProgramUid: String,
    val triggers: List<Trigger>,
    val conceptIds: List<String>,
)

/**
 * Turns the lenient Gson DTOs into the strict domain model.
 *
 * The two-stage parse (DTO -> domain -> validated) is deliberate. Gson deserialises via reflection
 * and bypasses Kotlin constructors, so every DTO field can be null regardless of its declared
 * default — the lesson already recorded on `AttributeFilterConfig`. Rather than let those nulls leak
 * inward and detonate several layers deeper, every one becomes a [ConfigError] attributable to the
 * concept or transfer that caused it, and the rest of the config still runs.
 */
object ConfigParser {

    fun parse(dto: DataMapperConfigDto?): ParsedConfig {
        val errors = mutableListOf<ConfigError>()

        if (dto == null) {
            return ParsedConfig(MappingSettings(), emptyList(), emptyList(), errors)
        }

        val settings = parseSettings(dto, errors)
        val concepts = dto.concepts.orEmpty().mapIndexedNotNull { index, conceptDto ->
            parseConcept(conceptDto, index, errors)
        }

        val seenConceptIds = mutableSetOf<String>()
        val uniqueConcepts = concepts.filter { concept ->
            if (!seenConceptIds.add(concept.id)) {
                errors += ConfigError(
                    ErrorSeverity.ERROR,
                    "Duplicate concept id '${concept.id}'; only the first is used",
                    concept.id,
                )
                false
            } else {
                true
            }
        }

        val transfers = dto.transfers.orEmpty().mapIndexedNotNull { index, transferDto ->
            parseTransfer(transferDto, index, errors)
        }

        return ParsedConfig(settings, uniqueConcepts, transfers, errors)
    }

    private fun parseSettings(dto: DataMapperConfigDto, errors: MutableList<ConfigError>): MappingSettings {
        val settings = dto.settings ?: return MappingSettings()
        return MappingSettings(
            defaultOnConflict = enumOrDefault(
                settings.defaultOnConflict, WritePolicy.SKIP_IF_PRESENT, "settings.defaultOnConflict", errors,
            ),
            targetEventPolicy = enumOrDefault(
                settings.targetEventPolicy, TargetEventPolicy.REQUIRE_EXISTING, "settings.targetEventPolicy", errors,
            ),
            failFast = settings.failFast ?: false,
        )
    }

    private fun parseConcept(
        dto: ConceptDto,
        index: Int,
        errors: MutableList<ConfigError>,
    ): Concept? {
        val id = dto.id?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            errors += ConfigError(ErrorSeverity.ERROR, "Concept #${index + 1} has no id and is ignored")
            return null
        }

        val canonicalDto = dto.canonical ?: run {
            errors += ConfigError(ErrorSeverity.ERROR, "Concept '$id' has no canonical representation", id)
            return null
        }

        val type = parseEnum<CanonicalType>(canonicalDto.type) ?: run {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '$id' has unknown canonical type '${canonicalDto.type}'",
                id,
            )
            return null
        }

        val canonical = Canonical(
            type = type,
            unit = canonicalDto.unit?.trim()?.takeIf { it.isNotEmpty() },
            codes = canonicalDto.codes?.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) },
        )

        val bindings = dto.bindings.orEmpty().mapIndexedNotNull { bindingIndex, bindingDto ->
            val programUid = bindingDto.programUid?.trim()?.takeIf { it.isNotEmpty() } ?: run {
                errors += ConfigError(
                    ErrorSeverity.ERROR,
                    "Concept '$id' binding #${bindingIndex + 1} has no programUid and is ignored",
                    id,
                )
                return@mapIndexedNotNull null
            }

            val address = parseAddress(bindingDto.at, programUid, "concept '$id' binding for $programUid", errors)
                ?: return@mapIndexedNotNull null

            val derive = parseDerivation(bindingDto.derive, id, errors)

            Binding(
                programUid = programUid,
                at = address,
                unit = bindingDto.unit?.trim()?.takeIf { it.isNotEmpty() },
                options = bindingDto.options
                    ?.mapNotNull { (key, value) ->
                        val k = key?.trim(); val v = value?.trim()
                        if (!k.isNullOrEmpty() && !v.isNullOrEmpty()) k to v else null
                    }
                    ?.toMap()
                    ?.takeIf { it.isNotEmpty() },
                writeOptions = bindingDto.writeOptions
                    ?.mapNotNull { (key, value) ->
                        val k = key?.trim(); val v = value?.trim()
                        if (!k.isNullOrEmpty() && !v.isNullOrEmpty()) k to v else null
                    }
                    ?.toMap()
                    ?.takeIf { it.isNotEmpty() },
                onUnmapped = parseEnum<UnmatchedPolicy>(bindingDto.onUnmapped),
                derive = derive,
                // A lossy derivation defaults to WRITE_ONLY so it can receive but never be a source
                // (F6). Making it BOTH has to be typed out deliberately.
                direction = parseEnum<Direction>(bindingDto.direction)
                    ?: if (derive != null) Direction.WRITE_ONLY else Direction.BOTH,
                onConflict = parseEnum<WritePolicy>(bindingDto.onConflict),
            )
        }

        if (bindings.size < 2) {
            errors += ConfigError(
                ErrorSeverity.WARNING,
                "Concept '$id' has ${bindings.size} binding(s); at least two are needed to transfer anything",
                id,
            )
        }

        return Concept(id = id, label = dto.label?.trim(), canonical = canonical, bindings = bindings)
    }

    private fun parseDerivation(
        dto: org.dhis2.community.mappers.models.DerivationDto?,
        conceptId: String,
        errors: MutableList<ConfigError>,
    ): Derivation? {
        val op = dto?.op?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = parseEnum<DerivationOp>(op) ?: run {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Concept '$conceptId' has unknown derivation op '$op'; the binding is treated as undertived",
                conceptId,
            )
            return null
        }
        return Derivation(
            op = parsed,
            unit = dto.unit?.trim()?.takeIf { it.isNotEmpty() } ?: AgeUnit.YEARS.name,
            asOf = dto.asOf?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun parseTransfer(
        dto: org.dhis2.community.mappers.models.TransferDto,
        index: Int,
        errors: MutableList<ConfigError>,
    ): ParsedTransfer? {
        val id = dto.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: "transfer#${index + 1}"

        val from = dto.from?.programUid?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            errors += ConfigError(ErrorSeverity.ERROR, "Transfer '$id' has no source program", id)
            return null
        }
        val to = dto.to?.programUid?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            errors += ConfigError(ErrorSeverity.ERROR, "Transfer '$id' has no target program", id)
            return null
        }

        val triggers = dto.`when`.orEmpty().mapNotNull { raw ->
            parseEnum<Trigger>(raw) ?: run {
                errors += ConfigError(ErrorSeverity.ERROR, "Transfer '$id' has unknown trigger '$raw'", id)
                null
            }
        }.ifEmpty {
            errors += ConfigError(
                ErrorSeverity.WARNING,
                "Transfer '$id' declares no triggers; defaulting to TARGET_ENROLLMENT_CREATED",
                id,
            )
            listOf(Trigger.TARGET_ENROLLMENT_CREATED)
        }

        // Both were removed from the schema. Reported rather than ignored: a transfer carrying one is
        // a config whose author expects behaviour that no longer exists, and saying nothing would
        // turn that into a mapping which silently stops working.
        if (!dto.overrides.isNullOrEmpty()) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Transfer '$id' uses 'overrides', which has been removed. Set the conflict policy on " +
                    "the target program's binding (onConflict), or globally in settings",
                id,
            )
        }
        if (!dto.explicit.isNullOrEmpty()) {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "Transfer '$id' uses 'explicit', which has been removed. Model the pair as a concept " +
                    "with a binding for each program — it gets the same conversion plus option-code " +
                    "checking and a configurable conflict policy",
                id,
            )
        }

        return ParsedTransfer(
            id = id,
            fromProgramUid = from,
            toProgramUid = to,
            triggers = triggers,
            conceptIds = dto.concepts.orEmpty().mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) },
        )
    }

    fun parseAddress(
        dto: ValueAddressDto?,
        programUid: String,
        context: String,
        errors: MutableList<ConfigError>,
    ): ValueAddress? {
        if (dto == null) {
            errors += ConfigError(ErrorSeverity.ERROR, "$context has no address")
            return null
        }

        val kind = parseEnum<AddressKind>(dto.kind) ?: run {
            errors += ConfigError(ErrorSeverity.ERROR, "$context has unknown address kind '${dto.kind}'")
            return null
        }

        return when (kind) {
            AddressKind.ATTRIBUTE -> {
                val uid = dto.uid?.trim()?.takeIf { it.isNotEmpty() } ?: run {
                    errors += ConfigError(ErrorSeverity.ERROR, "$context is an ATTRIBUTE with no uid")
                    return null
                }
                ValueAddress.Attribute(uid)
            }

            AddressKind.DATA_ELEMENT -> {
                val uid = dto.uid?.trim()?.takeIf { it.isNotEmpty() } ?: run {
                    errors += ConfigError(ErrorSeverity.ERROR, "$context is a DATA_ELEMENT with no uid")
                    return null
                }
                val stage = dto.stageUid?.trim()?.takeIf { it.isNotEmpty() } ?: run {
                    errors += ConfigError(ErrorSeverity.ERROR, "$context is a DATA_ELEMENT with no stageUid")
                    return null
                }
                ValueAddress.DataElement(
                    programUid = programUid,
                    stageUid = stage,
                    uid = uid,
                    event = parseEventSelector(dto.event, context, errors),
                )
            }

            AddressKind.ENROLLMENT_PROPERTY -> {
                val property = dto.uid?.trim()?.takeIf { it.isNotEmpty() } ?: run {
                    errors += ConfigError(
                        ErrorSeverity.ERROR,
                        "$context is an ENROLLMENT_PROPERTY with no property name in 'uid'",
                    )
                    return null
                }
                ValueAddress.EnrollmentProperty(programUid, property.uppercase())
            }

            AddressKind.EVENT_PROPERTY -> {
                val property = dto.uid?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: run {
                    errors += ConfigError(
                        ErrorSeverity.ERROR,
                        "$context is an EVENT_PROPERTY with no property name in 'uid'",
                    )
                    return null
                }
                if (property !in EventPropertyName.all) {
                    errors += ConfigError(
                        ErrorSeverity.ERROR,
                        "$context has unknown event property '$property'; expected one of " +
                            EventPropertyName.all.joinToString(),
                    )
                    return null
                }
                val stage = dto.stageUid?.trim()?.takeIf { it.isNotEmpty() } ?: run {
                    errors += ConfigError(
                        ErrorSeverity.ERROR,
                        "$context is an EVENT_PROPERTY with no stageUid; the property belongs to an " +
                            "event of a specific stage",
                    )
                    return null
                }
                ValueAddress.EventProperty(
                    programUid = programUid,
                    stageUid = stage,
                    property = property,
                    event = parseEventSelector(dto.event, context, errors),
                )
            }

            AddressKind.CONSTANT -> {
                val value = dto.uid ?: run {
                    errors += ConfigError(ErrorSeverity.ERROR, "$context is a CONSTANT with no value in 'uid'")
                    return null
                }
                ValueAddress.Constant(value)
            }
        }
    }

    private fun parseEventSelector(
        dto: org.dhis2.community.mappers.models.EventSelectorDto?,
        context: String,
        errors: MutableList<ConfigError>,
    ): EventSelector {
        if (dto == null) return EventSelector()
        return EventSelector(
            select = enumOrDefault(dto.select, Pick.LATEST_WITH_VALUE, "$context event.select", errors),
            where = dto.where.orEmpty().mapNotNull { condition ->
                val dataElement = condition.dataElement?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val op = parseEnum<ConditionOp>(condition.op) ?: run {
                    errors += ConfigError(
                        ErrorSeverity.ERROR,
                        "$context has unknown event condition op '${condition.op}'; condition ignored",
                    )
                    return@mapNotNull null
                }
                EventCondition(dataElement, op, condition.value?.trim().orEmpty())
            },
            enrollment = enumOrDefault(
                dto.enrollment, EnrollmentScope.MOST_RECENT, "$context event.enrollment", errors,
            ),
            index = dto.index?.coerceAtLeast(0) ?: 0,
        )
    }

    // ─── Enum helpers ────────────────────────────────────────────────────────

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?): T? {
        val name = raw?.trim()?.uppercase()?.replace('-', '_')?.takeIf { it.isNotEmpty() } ?: return null
        return enumValues<T>().firstOrNull { it.name == name }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(
        raw: String?,
        default: T,
        context: String,
        errors: MutableList<ConfigError>,
    ): T {
        if (raw.isNullOrBlank()) return default
        return parseEnum<T>(raw) ?: run {
            errors += ConfigError(
                ErrorSeverity.ERROR,
                "$context has unknown value '$raw'; using $default",
                context,
            )
            default
        }
    }
}
