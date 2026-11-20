package org.dhis2.community.mappers

import org.dhis2.community.mappers.config.ConfigParser
import org.dhis2.community.mappers.config.ConfigValidator
import org.dhis2.community.mappers.config.MetadataLookup
import org.dhis2.community.mappers.models.BindingDto
import org.dhis2.community.mappers.models.CanonicalDto
import org.dhis2.community.mappers.models.ConceptDto
import org.dhis2.community.mappers.models.DataMapperConfigDto
import org.dhis2.community.mappers.models.DhisValueType
import org.dhis2.community.mappers.models.ErrorSeverity
import org.dhis2.community.mappers.models.OptionAction
import org.dhis2.community.mappers.models.FieldMeta
import org.dhis2.community.mappers.models.ProgramRefDto
import org.dhis2.community.mappers.models.SettingsDto
import org.dhis2.community.mappers.models.TransferDto
import org.dhis2.community.mappers.models.TransformSpec
import org.dhis2.community.mappers.models.UnmatchedPolicy
import org.dhis2.community.mappers.models.ValidatedConfig
import org.dhis2.community.mappers.models.ValueAddressDto
import org.dhis2.community.mappers.models.WritePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ANC = "ANCprogram1"
private const val PNC = "PNCprogram1"
private const val ANC_STAGE = "ANCstage001"

/**
 * Tests for config compilation.
 *
 * The validator is the last line of defence before a mapping becomes executable, so most of these
 * assert that a doubtful mapping is *dropped with an explanation* rather than compiled optimistically.
 */
class ConfigValidatorTest {

    /** Fake metadata: a handful of fields, wired by uid. */
    private class FakeMetadata(
        private val fields: Map<String, FieldMeta>,
        private val programs: Set<String> = setOf(ANC, PNC),
        private val stages: Map<String, String> = mapOf(ANC_STAGE to ANC),
        private val stageDataElements: Map<String, Set<String>> = emptyMap(),
        private val programAttributes: Map<String, Set<String>> = emptyMap(),
        private val repeatableStages: Set<String> = setOf(ANC_STAGE),
    ) : MetadataLookup {
        override fun programExists(uid: String) = uid in programs
        override fun attribute(uid: String) = fields[uid]
        override fun dataElement(uid: String) = fields[uid]
        override fun stageBelongsToProgram(stageUid: String, programUid: String) =
            stages[stageUid] == programUid

        override fun dataElementInStage(dataElementUid: String, stageUid: String) =
            stageDataElements[stageUid]?.contains(dataElementUid) ?: true

        override fun attributeInProgram(attributeUid: String, programUid: String) =
            programAttributes[programUid]?.contains(attributeUid) ?: true

        override fun isStageRepeatable(stageUid: String) = stageUid in repeatableStages
        override fun programName(uid: String) = uid
    }

    private fun number(uid: String) = FieldMeta(uid, uid, DhisValueType.NUMBER)
    private fun text(uid: String) = FieldMeta(uid, uid, DhisValueType.TEXT)
    private fun integer(uid: String) = FieldMeta(uid, uid, DhisValueType.INTEGER)
    private fun date(uid: String) = FieldMeta(uid, uid, DhisValueType.DATE)
    private fun options(uid: String, vararg codes: String) =
        FieldMeta(uid, uid, DhisValueType.TEXT, optionSetUid = "os_$uid", optionCodes = codes.toList())

    private fun validate(dto: DataMapperConfigDto, metadata: MetadataLookup): ValidatedConfig =
        ConfigValidator(metadata).validate(ConfigParser.parse(dto))

    private fun errorsOf(config: ValidatedConfig) =
        config.errors.filter { it.severity == ErrorSeverity.ERROR }.map { it.message }

    private fun transfer(vararg conceptIds: String) = TransferDto(
        id = "anc-to-pnc",
        from = ProgramRefDto(ANC),
        to = ProgramRefDto(PNC),
        `when` = listOf("TARGET_ENROLLMENT_CREATED"),
        concepts = conceptIds.toList(),
    )

    private fun concept(
        id: String,
        canonical: CanonicalDto,
        vararg bindings: BindingDto,
    ) = ConceptDto(id = id, label = id, canonical = canonical, bindings = bindings.toList())

    private fun attributeBinding(
        program: String,
        uid: String,
        unit: String? = null,
        options: Map<String, String>? = null,
        writeOptions: Map<String, String>? = null,
        onUnmapped: String? = null,
        derive: org.dhis2.community.mappers.models.DerivationDto? = null,
        direction: String? = null,
    ) = BindingDto(
        programUid = program,
        at = ValueAddressDto(kind = "ATTRIBUTE", uid = uid),
        unit = unit,
        options = options,
        writeOptions = writeOptions,
        onUnmapped = onUnmapped,
        derive = derive,
        direction = direction,
    )

    // ─── The happy path ──────────────────────────────────────────────────────

    @Test
    fun `a unit difference compiles into a scaling step`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "haemoglobin",
                        CanonicalDto(type = "NUMBER", unit = "g/dL"),
                        attributeBinding(ANC, "ancHbAttr01", unit = "g/L"),
                        attributeBinding(PNC, "pncHbAttr01", unit = "g/dL"),
                    ),
                ),
                transfers = listOf(transfer("haemoglobin")),
            ),
            FakeMetadata(mapOf("ancHbAttr01" to number("ancHbAttr01"), "pncHbAttr01" to number("pncHbAttr01"))),
        )

        val mapping = config.transfers.single().resolvedMappings.single()
        val scale = mapping.pipeline.filterIsInstance<TransformSpec.ScaleUnit>().single()
        assertEquals(0.1, scale.factor, 1e-9)
        assertEquals(emptyList<String>(), errorsOf(config))
    }

    @Test
    fun `matching units need no scaling step`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "haemoglobin",
                        CanonicalDto(type = "NUMBER", unit = "g/dL"),
                        attributeBinding(ANC, "a1", unit = "g/dL"),
                        attributeBinding(PNC, "b1", unit = "g/dL"),
                    ),
                ),
                transfers = listOf(transfer("haemoglobin")),
            ),
            FakeMetadata(mapOf("a1" to number("a1"), "b1" to number("b1"))),
        )

        val mapping = config.transfers.single().resolvedMappings.single()
        assertTrue(mapping.pipeline.none { it is TransformSpec.ScaleUnit })
    }

    // ─── Refusals (F3) ───────────────────────────────────────────────────────

    @Test
    fun `an unknown unit is refused rather than assumed to need no conversion`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "haemoglobin",
                        CanonicalDto(type = "NUMBER", unit = "g/dL"),
                        attributeBinding(ANC, "a1", unit = "squiggles"),
                        attributeBinding(PNC, "b1", unit = "g/dL"),
                    ),
                ),
                transfers = listOf(transfer("haemoglobin")),
            ),
            FakeMetadata(mapOf("a1" to number("a1"), "b1" to number("b1"))),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("unit") })
    }

    @Test
    fun `units from different dimensions are refused`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "weight",
                        CanonicalDto(type = "NUMBER", unit = "kg"),
                        attributeBinding(ANC, "a1", unit = "cm"),
                        attributeBinding(PNC, "b1", unit = "kg"),
                    ),
                ),
                transfers = listOf(transfer("weight")),
            ),
            FakeMetadata(mapOf("a1" to number("a1"), "b1" to number("b1"))),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
    }

    // ─── Option sets (F2) ────────────────────────────────────────────────────

    @Test
    fun `option codes are translated through the canonical vocabulary`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "hiv_status",
                        CanonicalDto(type = "OPTION", codes = listOf("POSITIVE", "NEGATIVE")),
                        attributeBinding(ANC, "a1", options = mapOf("1" to "POSITIVE", "2" to "NEGATIVE")),
                        attributeBinding(PNC, "b1", options = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE")),
                    ),
                ),
                transfers = listOf(transfer("hiv_status")),
            ),
            FakeMetadata(mapOf("a1" to options("a1", "1", "2"), "b1" to options("b1", "POS", "NEG"))),
        )

        val mapping = config.transfers.single().resolvedMappings.single()
        val translations = mapping.pipeline.filterIsInstance<TransformSpec.TranslateOptions>()
        // One decode (program code -> canonical) and one encode (canonical -> program code).
        assertEquals(2, translations.size)
        assertEquals("POSITIVE", translations[0].map["1"])
        assertEquals("POS", translations[1].map["POSITIVE"])
    }

    @Test
    fun `overlapping numeric codes are not assumed to mean the same thing`() {
        // Both option sets use "1" and "2". Without a declared map, identity would silently equate
        // Male with Positive — exactly failure mode F2.
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "hiv_status",
                        CanonicalDto(type = "OPTION", codes = listOf("POSITIVE", "NEGATIVE")),
                        attributeBinding(ANC, "a1"),
                        attributeBinding(PNC, "b1"),
                    ),
                ),
                transfers = listOf(transfer("hiv_status")),
            ),
            FakeMetadata(mapOf("a1" to options("a1", "1", "2"), "b1" to options("b1", "1", "2"))),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("canonical") })
    }

    @Test
    fun `an option code the author never mapped is reported as a warning`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "hiv_status",
                        CanonicalDto(type = "OPTION", codes = listOf("POSITIVE", "NEGATIVE")),
                        attributeBinding(ANC, "a1", options = mapOf("1" to "POSITIVE", "2" to "NEGATIVE")),
                        attributeBinding(PNC, "b1", options = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE")),
                    ),
                ),
                transfers = listOf(transfer("hiv_status")),
            ),
            // The real option set has a third code, "9", that the config does not account for.
            FakeMetadata(mapOf("a1" to options("a1", "1", "2", "9"), "b1" to options("b1", "POS", "NEG"))),
        )

        assertTrue(
            config.errors.any { it.severity == ErrorSeverity.WARNING && it.message.contains("9") },
        )
    }

    @Test
    fun `a many-to-one option map cannot be used as a write target`() {
        // Both 2 and 9 decode to NEGATIVE, so encoding NEGATIVE has no single right answer.
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "hiv_status",
                        CanonicalDto(type = "OPTION", codes = listOf("POSITIVE", "NEGATIVE")),
                        attributeBinding(ANC, "a1", options = mapOf("1" to "POSITIVE", "2" to "NEGATIVE")),
                        attributeBinding(
                            PNC, "b1",
                            options = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE", "UNK" to "NEGATIVE"),
                        ),
                    ),
                ),
                transfers = listOf(transfer("hiv_status")),
            ),
            FakeMetadata(
                mapOf("a1" to options("a1", "1", "2"), "b1" to options("b1", "POS", "NEG", "UNK")),
            ),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("many-to-one") })
    }

    // ─── Lossy derivations (F6) ──────────────────────────────────────────────

    @Test
    fun `a derived age binding is write only by default and cannot be a source`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "date_of_birth",
                        CanonicalDto(type = "DATE"),
                        attributeBinding(
                            ANC, "ageAttr001",
                            derive = org.dhis2.community.mappers.models.DerivationDto("AGE_FROM", "YEARS", "TODAY"),
                        ),
                        attributeBinding(PNC, "dobAttr001"),
                    ),
                ),
                // ANC (the derived age) is the source here, which must be refused.
                transfers = listOf(transfer("date_of_birth")),
            ),
            FakeMetadata(mapOf("ageAttr001" to integer("ageAttr001"), "dobAttr001" to date("dobAttr001"))),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("WRITE_ONLY") })
    }

    @Test
    fun `a date can be written into a derived age binding`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "date_of_birth",
                        CanonicalDto(type = "DATE"),
                        attributeBinding(ANC, "dobAttr001"),
                        attributeBinding(
                            PNC, "ageAttr001",
                            derive = org.dhis2.community.mappers.models.DerivationDto("AGE_FROM", "YEARS", "TODAY"),
                        ),
                    ),
                ),
                transfers = listOf(transfer("date_of_birth")),
            ),
            FakeMetadata(mapOf("dobAttr001" to date("dobAttr001"), "ageAttr001" to integer("ageAttr001"))),
        )

        val mapping = config.transfers.single().resolvedMappings.single()
        assertTrue(mapping.pipeline.any { it is TransformSpec.AgeFrom })
    }

    @Test
    fun `a date can set a boolean flag but the flag cannot invent a date`() {
        fun buildConfig(from: String, to: String) = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "bcg_given",
                        CanonicalDto(type = "DATE"),
                        attributeBinding(ANC, "dateGiven1"),
                        attributeBinding(
                            PNC, "givenFlag1",
                            derive = org.dhis2.community.mappers.models.DerivationDto("PRESENCE", null, null),
                        ),
                    ),
                ),
                transfers = listOf(
                    TransferDto(
                        id = "t",
                        from = ProgramRefDto(from),
                        to = ProgramRefDto(to),
                        `when` = listOf("SOURCE_EVENT_SAVED"),
                        concepts = listOf("bcg_given"),
                    ),
                ),
            ),
            FakeMetadata(
                mapOf(
                    "dateGiven1" to date("dateGiven1"),
                    "givenFlag1" to FieldMeta("givenFlag1", "givenFlag1", DhisValueType.BOOLEAN),
                ),
            ),
        )

        // Date -> flag: sound, because a recorded date of administration implies administration.
        val forward = buildConfig(ANC, PNC)
        assertTrue(
            forward.transfers.single().resolvedMappings.single().pipeline
                .any { it is TransformSpec.Presence },
        )

        // Flag -> date: refused, because a ticked box says nothing about when.
        val backward = buildConfig(PNC, ANC)
        assertEquals(emptyList<Any>(), backward.transfers.single().resolvedMappings)
        assertTrue(errorsOf(backward).any { it.contains("WRITE_ONLY") })
    }

    // ─── Structural refusals ─────────────────────────────────────────────────

    @Test
    fun `mapping an attribute onto itself is rejected as a no-op`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "shared",
                        CanonicalDto(type = "TEXT"),
                        attributeBinding(ANC, "sharedAttr1"),
                        attributeBinding(PNC, "sharedAttr1"),
                    ),
                ),
                transfers = listOf(transfer("shared")),
            ),
            FakeMetadata(mapOf("sharedAttr1" to text("sharedAttr1"))),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("per-TEI") })
    }

    @Test
    fun `two mappings competing for one target are both dropped`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "first",
                        CanonicalDto(type = "TEXT"),
                        attributeBinding(ANC, "srcA00001"),
                        attributeBinding(PNC, "targetX001"),
                    ),
                    concept(
                        "second",
                        CanonicalDto(type = "TEXT"),
                        attributeBinding(ANC, "srcB00001"),
                        attributeBinding(PNC, "targetX001"),
                    ),
                ),
                transfers = listOf(transfer("first", "second")),
            ),
            FakeMetadata(
                mapOf(
                    "srcA00001" to text("srcA00001"),
                    "srcB00001" to text("srcB00001"),
                    "targetX001" to text("targetX001"),
                ),
            ),
        )

        // Neither is kept: the author did not decide, so the engine has no basis to.
        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("writing the same target") })
    }

    @Test
    fun `a stage from another program is rejected`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "hb",
                        CanonicalDto(type = "NUMBER", unit = "g/dL"),
                        BindingDto(
                            programUid = ANC,
                            at = ValueAddressDto(
                                kind = "DATA_ELEMENT",
                                uid = "hbDe000001",
                                // This stage belongs to PNC, not ANC.
                                stageUid = "pncStage001",
                            ),
                            unit = "g/dL",
                        ),
                        attributeBinding(PNC, "pncHb00001", unit = "g/dL"),
                    ),
                ),
                transfers = listOf(transfer("hb")),
            ),
            FakeMetadata(
                fields = mapOf("hbDe000001" to number("hbDe000001"), "pncHb00001" to number("pncHb00001")),
                stages = mapOf(ANC_STAGE to ANC, "pncStage001" to PNC),
            ),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("does not belong to program") })
    }

    @Test
    fun `an unknown concept reference is reported without dropping the rest of the transfer`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "known",
                        CanonicalDto(type = "TEXT"),
                        attributeBinding(ANC, "srcA00001"),
                        attributeBinding(PNC, "targetX001"),
                    ),
                ),
                transfers = listOf(transfer("known", "does_not_exist")),
            ),
            FakeMetadata(mapOf("srcA00001" to text("srcA00001"), "targetX001" to text("targetX001"))),
        )

        assertEquals(1, config.transfers.single().resolvedMappings.size)
        assertTrue(errorsOf(config).any { it.contains("does_not_exist") })
    }

    @Test
    fun `a transfer between a program and itself is rejected`() {
        val config = validate(
            DataMapperConfigDto(
                transfers = listOf(
                    TransferDto(
                        id = "self",
                        from = ProgramRefDto(ANC),
                        to = ProgramRefDto(ANC),
                        `when` = listOf("TARGET_ENROLLMENT_CREATED"),
                    ),
                ),
            ),
            FakeMetadata(emptyMap()),
        )

        assertEquals(emptyList<Any>(), config.transfers)
    }

    // ─── Orphaned bindings ───────────────────────────────────────────────────

    @Test
    fun `a binding no transfer references is reported so it is not a silent no-op`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "haemoglobin",
                        CanonicalDto(type = "NUMBER", unit = "g/dL"),
                        attributeBinding(ANC, "a1", unit = "g/dL"),
                        attributeBinding(PNC, "b1", unit = "g/dL"),
                        // A third program was bound, but no transfer mentions it.
                        attributeBinding("U5program01", "c1", unit = "g/dL"),
                    ),
                ),
                transfers = listOf(transfer("haemoglobin")),
            ),
            FakeMetadata(
                fields = mapOf(
                    "a1" to number("a1"),
                    "b1" to number("b1"),
                    "c1" to number("c1"),
                ),
                programs = setOf(ANC, PNC, "U5program01"),
            ),
        )

        // The ANC->PNC mapping still compiles; only the unreachable binding is flagged.
        assertEquals(1, config.transfers.single().resolvedMappings.size)
        assertTrue(
            config.errors.any {
                it.severity == ErrorSeverity.WARNING && it.message.contains("U5program01")
            },
        )
    }

    @Test
    fun `a concept carried by no transfer at all is reported once`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "forgotten",
                        CanonicalDto(type = "TEXT"),
                        attributeBinding(ANC, "a1"),
                        attributeBinding(PNC, "b1"),
                    ),
                ),
                transfers = listOf(transfer()),
            ),
            FakeMetadata(mapOf("a1" to text("a1"), "b1" to text("b1"))),
        )

        val warnings = config.errors.filter {
            it.severity == ErrorSeverity.WARNING && it.message.contains("not carried by any transfer")
        }
        // One warning for the concept, not one per binding.
        assertEquals(1, warnings.size)
    }

    @Test
    fun `fully wired bindings produce no orphan warning`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "haemoglobin",
                        CanonicalDto(type = "NUMBER", unit = "g/dL"),
                        attributeBinding(ANC, "a1", unit = "g/dL"),
                        attributeBinding(PNC, "b1", unit = "g/dL"),
                    ),
                ),
                transfers = listOf(transfer("haemoglobin")),
            ),
            FakeMetadata(mapOf("a1" to number("a1"), "b1" to number("b1"))),
        )

        assertTrue(
            config.errors.none {
                it.message.contains("will never transfer") ||
                    it.message.contains("not carried by any transfer")
            },
        )
    }

    @Test
    fun `an empty config parses to nothing without throwing`() {
        val config = validate(DataMapperConfigDto(), FakeMetadata(emptyMap()))

        assertEquals(emptyList<Any>(), config.transfers)
        assertEquals(emptyList<Any>(), config.concepts)
    }

    // ─── Conflict policy ─────────────────────────────────────────────────────

    private fun policyConfig(settings: SettingsDto?, bindingPolicy: String?) = validate(
        DataMapperConfigDto(
            settings = settings,
            concepts = listOf(
                concept(
                    "hb",
                    CanonicalDto(type = "NUMBER"),
                    attributeBinding(ANC, "ancHbAttr01"),
                    BindingDto(
                        programUid = PNC,
                        at = ValueAddressDto(kind = "ATTRIBUTE", uid = "pncHbAttr01"),
                        onConflict = bindingPolicy,
                    ),
                ),
            ),
            transfers = listOf(transfer("hb")),
        ),
        FakeMetadata(mapOf("ancHbAttr01" to number("ancHbAttr01"), "pncHbAttr01" to number("pncHbAttr01"))),
    )

    /**
     * Regression: the global default was parsed and exposed in the config app, then never consulted —
     * every mapping fell through to a hardcoded SKIP_IF_PRESENT, so setting it did nothing at all.
     */
    @Test
    fun `the global default conflict policy reaches the compiled mapping`() {
        val config = policyConfig(SettingsDto(defaultOnConflict = "OVERWRITE"), bindingPolicy = null)

        assertEquals(WritePolicy.OVERWRITE, config.transfers.single().resolvedMappings.single().policy)
    }

    @Test
    fun `a binding's own policy beats the global default`() {
        val config = policyConfig(
            SettingsDto(defaultOnConflict = "OVERWRITE"),
            bindingPolicy = "FAIL_ON_CONFLICT",
        )

        assertEquals(
            WritePolicy.FAIL_ON_CONFLICT,
            config.transfers.single().resolvedMappings.single().policy,
        )
    }

    @Test
    fun `with nothing declared the policy is the safe default`() {
        val config = policyConfig(settings = null, bindingPolicy = null)

        assertEquals(
            WritePolicy.SKIP_IF_PRESENT,
            config.transfers.single().resolvedMappings.single().policy,
        )
    }

    // ─── Removed schema features ─────────────────────────────────────────────

    /**
     * `overrides` and `explicit` were removed. Gson silently drops unknown keys, so a config still
     * carrying them would lose behaviour with no message anywhere — these assert it is reported.
     */
    @Test
    fun `a transfer still using overrides is rejected rather than silently ignored`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "hb",
                        CanonicalDto(type = "NUMBER"),
                        attributeBinding(ANC, "ancHbAttr01"),
                        attributeBinding(PNC, "pncHbAttr01"),
                    ),
                ),
                transfers = listOf(
                    transfer("hb").copy(
                        overrides = listOf(mapOf("concept" to "hb", "onConflict" to "OVERWRITE")),
                    ),
                ),
            ),
            FakeMetadata(mapOf("ancHbAttr01" to number("ancHbAttr01"), "pncHbAttr01" to number("pncHbAttr01"))),
        )

        assertTrue(errorsOf(config).any { it.contains("'overrides', which has been removed") })
    }

    @Test
    fun `a transfer still using explicit mappings is rejected rather than silently ignored`() {
        val config = validate(
            DataMapperConfigDto(
                transfers = listOf(
                    transfer().copy(
                        explicit = listOf(mapOf("id" to "legacy", "from" to "x", "to" to "y")),
                    ),
                ),
            ),
            FakeMetadata(emptyMap()),
        )

        assertTrue(errorsOf(config).any { it.contains("'explicit', which has been removed") })
    }

    // ─── Three programs, three encodings of one coded fact ─────────────────

    /**
     * The shape that turns up constantly: one program codes HIV status positive/negative/unknown,
     * another stores a bare boolean, a third has an option set with no "unknown" at all.
     */
    private fun hivMetadata() = FakeMetadata(
        mapOf(
            "hivFullOptSet" to options("hivFullOptSet", "P", "N", "U"),
            "hivBoolField1" to FieldMeta("hivBoolField1", "hivBoolField1", DhisValueType.BOOLEAN),
            "hivShortOptSt" to options("hivShortOptSt", "POS", "NEG"),
            "hivTrueOnly01" to FieldMeta("hivTrueOnly01", "hivTrueOnly01", DhisValueType.TRUE_ONLY),
        ),
        programs = setOf(ANC, PNC),
    )

    @Test
    fun `a boolean binding joins an option concept through an explicit code map`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "hiv",
                        CanonicalDto(type = "OPTION", codes = listOf("POSITIVE", "NEGATIVE", "UNKNOWN")),
                        attributeBinding(
                            ANC, "hivFullOptSet",
                            options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to "UNKNOWN"),
                        ),
                        attributeBinding(
                            PNC, "hivBoolField1",
                            options = mapOf("true" to "POSITIVE", "false" to "NEGATIVE"),
                        ),
                    ),
                ),
                transfers = listOf(transfer("hiv")),
            ),
            hivMetadata(),
        )

        assertEquals(emptyList<String>(), errorsOf(config))
        val mapping = config.transfers.single().resolvedMappings.single()
        val translate = mapping.pipeline.filterIsInstance<TransformSpec.TranslateOptions>().last()
        // Writing goes canonical -> this program's own encoding.
        assertEquals("true", translate.map["POSITIVE"])
        assertEquals("false", translate.map["NEGATIVE"])
    }

    @Test
    fun `a boolean binding with no code map is refused rather than guessed`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "hiv",
                        CanonicalDto(type = "OPTION", codes = listOf("POSITIVE", "NEGATIVE")),
                        attributeBinding(
                            ANC, "hivFullOptSet",
                            options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to "NEGATIVE"),
                        ),
                        attributeBinding(PNC, "hivBoolField1"),
                    ),
                ),
                transfers = listOf(transfer("hiv")),
            ),
            hivMetadata(),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("no option map was declared") })
    }

    /**
     * A target whose option set has no code for one canonical value compiles fine — the gap is
     * per-value, and only shows when such a value actually turns up. Nothing warns at config time.
     */
    @Test
    fun `a target missing a canonical code still compiles`() {
        val config = validate(
            DataMapperConfigDto(
                concepts = listOf(
                    concept(
                        "hiv",
                        CanonicalDto(type = "OPTION", codes = listOf("POSITIVE", "NEGATIVE", "UNKNOWN")),
                        attributeBinding(
                            ANC, "hivFullOptSet",
                            options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to "UNKNOWN"),
                        ),
                        attributeBinding(
                            PNC, "hivShortOptSt",
                            options = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE"),
                        ),
                    ),
                ),
                transfers = listOf(transfer("hiv")),
            ),
            hivMetadata(),
        )

        assertEquals(emptyList<String>(), errorsOf(config))
        val translate = config.transfers.single().resolvedMappings.single()
            .pipeline.filterIsInstance<TransformSpec.TranslateOptions>().last()
        assertEquals("POS", translate.map["POSITIVE"])
        assertEquals(null, translate.map["UNKNOWN"])

        // ...but the gap is named, rather than left to surface as an intermittent runtime failure.
        assertTrue(
            config.errors.any {
                it.severity == ErrorSeverity.WARNING && it.message.contains("no code for UNKNOWN")
            },
        )
    }

    // ─── Asymmetric vocabularies (writeOptions) ──────────────────────────────

    private fun hivConcept(vararg bindings: BindingDto) = DataMapperConfigDto(
        concepts = listOf(
            concept(
                "hiv",
                CanonicalDto(type = "OPTION", codes = listOf("POSITIVE", "NEGATIVE", "UNKNOWN")),
                *bindings,
            ),
        ),
        transfers = listOf(transfer("hiv")),
    )

    /**
     * Three canonical codes into a program that has two. The collapse is declared, so it is allowed;
     * the engine would never infer it.
     */
    @Test
    fun `an explicit write map collapses three canonical codes onto two program codes`() {
        val config = validate(
            hivConcept(
                attributeBinding(
                    ANC, "hivFullOptSet",
                    options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to "UNKNOWN"),
                ),
                attributeBinding(
                    PNC, "hivShortOptSt",
                    options = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE"),
                    writeOptions = mapOf(
                        "POSITIVE" to "POS",
                        "NEGATIVE" to "NEG",
                        "UNKNOWN" to "NEG",
                    ),
                ),
            ),
            hivMetadata(),
        )

        assertEquals(emptyList<String>(), errorsOf(config))
        val translate = config.transfers.single().resolvedMappings.single()
            .pipeline.filterIsInstance<TransformSpec.TranslateOptions>().last()
        assertEquals("POS", translate.map["POSITIVE"])
        assertEquals("NEG", translate.map["NEGATIVE"])
        assertEquals("NEG", translate.map["UNKNOWN"])
        // Nothing left unreachable, so nothing to warn about.
        assertTrue(config.errors.none { it.message.contains("no code for") })
    }

    @Test
    fun `SKIP leaves the target blank instead of failing when a code cannot be written`() {
        val config = validate(
            hivConcept(
                attributeBinding(
                    ANC, "hivFullOptSet",
                    options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to "UNKNOWN"),
                ),
                attributeBinding(
                    PNC, "hivShortOptSt",
                    options = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE"),
                    onUnmapped = "SKIP",
                ),
            ),
            hivMetadata(),
        )

        val translate = config.transfers.single().resolvedMappings.single()
            .pipeline.filterIsInstance<TransformSpec.TranslateOptions>().last()
        assertEquals(UnmatchedPolicy.SKIP, translate.unmatchedPolicy)
        assertTrue(config.errors.any { it.message.contains("will be left blank") })
    }

    /**
     * A many-to-one read map used to make the binding source-only. Declaring the write direction is
     * how an author says which of the candidate codes a canonical value should become.
     */
    @Test
    fun `a many-to-one read map becomes writable once the write direction is declared`() {
        val withoutWriteMap = validate(
            hivConcept(
                attributeBinding(ANC, "hivFullOptSet", options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to "UNKNOWN")),
                attributeBinding(
                    PNC, "hivShortOptSt",
                    options = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE"),
                ),
            ),
            hivMetadata(),
        )
        assertEquals(1, withoutWriteMap.transfers.single().resolvedMappings.size)

        val collapsingRead = validate(
            hivConcept(
                attributeBinding(ANC, "hivFullOptSet", options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to "UNKNOWN")),
                attributeBinding(
                    PNC, "hivShortOptSt",
                    // Both codes read as NEGATIVE, so inverting is ambiguous.
                    options = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE"),
                    writeOptions = mapOf("POSITIVE" to "POS", "NEGATIVE" to "NEG", "UNKNOWN" to "NEG"),
                ),
            ),
            hivMetadata(),
        )
        assertEquals(emptyList<String>(), errorsOf(collapsingRead))
    }

    @Test
    fun `a write map keyed the wrong way round is rejected with an explanation`() {
        val config = validate(
            hivConcept(
                attributeBinding(ANC, "hivFullOptSet", options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to "UNKNOWN")),
                attributeBinding(
                    PNC, "hivShortOptSt",
                    // Program code -> canonical, i.e. the read direction, by mistake.
                    writeOptions = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE"),
                ),
            ),
            hivMetadata(),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("not the other way round") })
    }

    @Test
    fun `a write map targeting a code the field does not have is rejected`() {
        val config = validate(
            hivConcept(
                attributeBinding(ANC, "hivFullOptSet", options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to "UNKNOWN")),
                attributeBinding(
                    PNC, "hivShortOptSt",
                    writeOptions = mapOf("POSITIVE" to "POS", "NEGATIVE" to "NEG", "UNKNOWN" to "DUNNO"),
                ),
            ),
            hivMetadata(),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("DUNNO") })
    }

    // ─── Per-value outcomes (@BLANK / @FAIL) ─────────────────────────────────

    /**
     * The honest answer where a program cannot express a finding at all: record nothing, rather
     * than record it as something it is not.
     */
    @Test
    fun `a canonical code can be routed to leave the target blank`() {
        val config = validate(
            hivConcept(
                attributeBinding(ANC, "hivFullOptSet", options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to "UNKNOWN")),
                attributeBinding(
                    PNC, "hivShortOptSt",
                    options = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE"),
                    writeOptions = mapOf(
                        "POSITIVE" to "POS",
                        "NEGATIVE" to "NEG",
                        "UNKNOWN" to OptionAction.BLANK,
                    ),
                ),
            ),
            hivMetadata(),
        )

        assertEquals(emptyList<String>(), errorsOf(config))
        // A code deliberately routed to an outcome is decided, not overlooked, so nothing warns.
        assertTrue(config.errors.none { it.message.contains("no code for") })

        val translate = config.transfers.single().resolvedMappings.single()
            .pipeline.filterIsInstance<TransformSpec.TranslateOptions>().last()
        assertEquals(OptionAction.BLANK, translate.map["UNKNOWN"])
    }

    @Test
    fun `an outcome on the read map is rejected as the wrong direction`() {
        val config = validate(
            hivConcept(
                attributeBinding(
                    ANC, "hivFullOptSet",
                    options = mapOf("P" to "POSITIVE", "N" to "NEGATIVE", "U" to OptionAction.BLANK),
                ),
                attributeBinding(PNC, "hivShortOptSt", options = mapOf("POS" to "POSITIVE", "NEG" to "NEGATIVE")),
            ),
            hivMetadata(),
        )

        assertEquals(emptyList<Any>(), config.transfers.single().resolvedMappings)
        assertTrue(errorsOf(config).any { it.contains("outcomes belong on the write map") })
    }

    // ─── Clearing a TRUE_ONLY target ─────────────────────────────────────────

    private fun boolToTrueOnly(targetPolicy: String?) = validate(
        DataMapperConfigDto(
            concepts = listOf(
                concept(
                    "hiv_flag",
                    CanonicalDto(type = "BOOLEAN"),
                    attributeBinding(ANC, "hivBoolField1"),
                    BindingDto(
                        programUid = PNC,
                        at = ValueAddressDto(kind = "ATTRIBUTE", uid = "hivTrueOnly01"),
                        onConflict = targetPolicy,
                    ),
                ),
            ),
            transfers = listOf(transfer("hiv_flag")),
        ),
        hivMetadata(),
    )

    /**
     * TRUE_ONLY has no "false". Writing the literal string would be rejected by the server, and
     * leaving the tick alone would assert the opposite of the source, so false must remove it.
     */
    @Test
    fun `a boolean written into a TRUE_ONLY field clears it instead of writing false`() {
        val config = boolToTrueOnly(targetPolicy = "OVERWRITE")

        assertEquals(emptyList<String>(), errorsOf(config))
        val booleanAs = config.transfers.single().resolvedMappings.single()
            .pipeline.filterIsInstance<TransformSpec.BooleanAs>().last()
        assertEquals("true", booleanAs.trueCode)
        assertEquals(OptionAction.CLEAR, booleanAs.falseCode)
    }

    @Test
    fun `a clearing binding under the default policy is warned about, not left silent`() {
        val config = boolToTrueOnly(targetPolicy = null)

        assertEquals(emptyList<String>(), errorsOf(config))
        assertTrue(
            config.errors.any {
                it.severity == ErrorSeverity.WARNING && it.message.contains("the field would stay set")
            },
        )
    }

    @Test
    fun `no such warning once the policy permits overwriting`() {
        val config = boolToTrueOnly(targetPolicy = "OVERWRITE")

        assertTrue(config.errors.none { it.message.contains("the field would stay set") })
    }
}
