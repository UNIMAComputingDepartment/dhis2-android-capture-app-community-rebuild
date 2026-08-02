package org.dhis2.community.mappers.config

import org.dhis2.community.mappers.models.DhisValueType
import org.dhis2.community.mappers.models.FieldMeta
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.common.ValueType

/**
 * The metadata questions validation and conversion need to answer.
 *
 * An interface rather than a direct D2 dependency so [ConfigValidator] — which decides whether a
 * mapping is safe — can be exercised with a fake in plain JUnit tests.
 */
interface MetadataLookup {
    fun programExists(uid: String): Boolean
    fun attribute(uid: String): FieldMeta?
    fun dataElement(uid: String): FieldMeta?

    /** True when [stageUid] is a stage of [programUid]. Guards against stages pasted from another program. */
    fun stageBelongsToProgram(stageUid: String, programUid: String): Boolean

    /** True when [dataElementUid] is collected by [stageUid]. */
    fun dataElementInStage(dataElementUid: String, stageUid: String): Boolean

    /** True when [attributeUid] is assigned to [programUid]. */
    fun attributeInProgram(attributeUid: String, programUid: String): Boolean

    fun isStageRepeatable(stageUid: String): Boolean

    fun programName(uid: String): String?
}

/** D2-backed [MetadataLookup]. The single place SDK types cross into the mapping engine. */
class D2MetadataLookup(private val d2: D2) : MetadataLookup {

    // Metadata is stable for the life of a mapping run; caching keeps a plan over many concepts from
    // re-querying the same attribute dozens of times.
    private val attributeCache = mutableMapOf<String, FieldMeta?>()
    private val dataElementCache = mutableMapOf<String, FieldMeta?>()
    private val optionCodeCache = mutableMapOf<String, List<String>>()

    override fun programExists(uid: String): Boolean =
        d2.programModule().programs().uid(uid).blockingExists()

    override fun programName(uid: String): String? =
        d2.programModule().programs().uid(uid).blockingGet()?.displayName()

    override fun attribute(uid: String): FieldMeta? = attributeCache.getOrPut(uid) {
        val attribute = d2.trackedEntityModule().trackedEntityAttributes().uid(uid).blockingGet()
            ?: return@getOrPut null
        val optionSetUid = attribute.optionSet()?.uid()
        FieldMeta(
            uid = uid,
            name = attribute.displayName() ?: uid,
            valueType = attribute.valueType().toDhisValueType(),
            optionSetUid = optionSetUid,
            optionCodes = optionSetUid?.let { optionCodes(it) } ?: emptyList(),
        )
    }

    override fun dataElement(uid: String): FieldMeta? = dataElementCache.getOrPut(uid) {
        val dataElement = d2.dataElementModule().dataElements().uid(uid).blockingGet()
            ?: return@getOrPut null
        val optionSetUid = dataElement.optionSet()?.uid()
        FieldMeta(
            uid = uid,
            name = dataElement.displayName() ?: uid,
            valueType = dataElement.valueType().toDhisValueType(),
            optionSetUid = optionSetUid,
            optionCodes = optionSetUid?.let { optionCodes(it) } ?: emptyList(),
        )
    }

    override fun stageBelongsToProgram(stageUid: String, programUid: String): Boolean =
        d2.programModule().programStages()
            .byUid().eq(stageUid)
            .byProgramUid().eq(programUid)
            .one()
            .blockingExists()

    override fun dataElementInStage(dataElementUid: String, stageUid: String): Boolean =
        d2.programModule().programStageDataElements()
            .byProgramStage().eq(stageUid)
            .byDataElement().eq(dataElementUid)
            .one()
            .blockingExists()

    override fun attributeInProgram(attributeUid: String, programUid: String): Boolean =
        d2.programModule().programTrackedEntityAttributes()
            .byProgram().eq(programUid)
            .byTrackedEntityAttribute().eq(attributeUid)
            .one()
            .blockingExists()

    override fun isStageRepeatable(stageUid: String): Boolean =
        d2.programModule().programStages().uid(stageUid).blockingGet()?.repeatable() == true

    private fun optionCodes(optionSetUid: String): List<String> = optionCodeCache.getOrPut(optionSetUid) {
        d2.optionModule().options()
            .byOptionSetUid().eq(optionSetUid)
            .blockingGet()
            .mapNotNull { it.code() }
    }
}

/**
 * Maps the SDK's value type onto the engine's mirror.
 *
 * Anything not explicitly listed becomes [DhisValueType.UNSUPPORTED] rather than a guess, so a value
 * type the engine has no conversion rule for is reported as unmappable instead of being string-copied.
 */
internal fun ValueType?.toDhisValueType(): DhisValueType = when (this) {
    ValueType.TEXT -> DhisValueType.TEXT
    ValueType.LONG_TEXT -> DhisValueType.LONG_TEXT
    ValueType.LETTER -> DhisValueType.LETTER
    ValueType.PHONE_NUMBER -> DhisValueType.PHONE_NUMBER
    ValueType.EMAIL -> DhisValueType.EMAIL
    ValueType.BOOLEAN -> DhisValueType.BOOLEAN
    ValueType.TRUE_ONLY -> DhisValueType.TRUE_ONLY
    ValueType.DATE -> DhisValueType.DATE
    ValueType.DATETIME -> DhisValueType.DATETIME
    ValueType.TIME -> DhisValueType.TIME
    ValueType.NUMBER -> DhisValueType.NUMBER
    ValueType.UNIT_INTERVAL -> DhisValueType.UNIT_INTERVAL
    ValueType.PERCENTAGE -> DhisValueType.PERCENTAGE
    ValueType.INTEGER -> DhisValueType.INTEGER
    ValueType.INTEGER_POSITIVE -> DhisValueType.INTEGER_POSITIVE
    ValueType.INTEGER_NEGATIVE -> DhisValueType.INTEGER_NEGATIVE
    ValueType.INTEGER_ZERO_OR_POSITIVE -> DhisValueType.INTEGER_ZERO_OR_POSITIVE
    ValueType.ORGANISATION_UNIT -> DhisValueType.ORGANISATION_UNIT
    ValueType.AGE -> DhisValueType.AGE
    ValueType.URL -> DhisValueType.URL
    ValueType.USERNAME -> DhisValueType.USERNAME
    ValueType.MULTI_TEXT -> DhisValueType.MULTI_TEXT
    else -> DhisValueType.UNSUPPORTED
}
