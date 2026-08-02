package org.dhis2.community.mappers.models

/** Gson DTOs — every field nullable; Gson bypasses constructor, so no reliance on Kotlin defaults. */

data class DataMapperConfigDto(
    val version: Int? = null,
    val settings: SettingsDto? = null,
    val concepts: List<ConceptDto>? = null,
    val transfers: List<TransferDto>? = null,
)

data class SettingsDto(
    val defaultOnConflict: String? = null,
    val targetEventPolicy: String? = null,
    val failFast: Boolean? = null,
)

data class ConceptDto(
    val id: String? = null,
    val label: String? = null,
    val canonical: CanonicalDto? = null,
    val bindings: List<BindingDto>? = null,
)

data class CanonicalDto(
    val type: String? = null,
    val unit: String? = null,
    val codes: List<String>? = null,
)

data class BindingDto(
    val programUid: String? = null,
    val at: ValueAddressDto? = null,
    val unit: String? = null,
    val options: Map<String, String>? = null,
    val writeOptions: Map<String, String>? = null,
    val onUnmapped: String? = null,
    val derive: DerivationDto? = null,
    val direction: String? = null,
    val onConflict: String? = null,
)

data class ValueAddressDto(
    val kind: String? = null,
    val uid: String? = null,
    val stageUid: String? = null,
    val event: EventSelectorDto? = null,
)

data class EventSelectorDto(
    val select: String? = null,
    val where: List<EventConditionDto>? = null,
    val enrollment: String? = null,
    val index: Int? = null,
)

data class EventConditionDto(
    val dataElement: String? = null,
    val op: String? = null,
    val value: String? = null,
)

data class DerivationDto(
    val op: String? = null,
    val unit: String? = null,
    val asOf: String? = null,
)

data class TransferDto(
    val id: String? = null,
    val from: ProgramRefDto? = null,
    val to: ProgramRefDto? = null,
    val `when`: List<String>? = null,
    val concepts: List<String>? = null,

    /**
     * Removed from the schema; retained only so a config written against the old one is *rejected*
     * rather than quietly ignored.
     *
     * Gson drops unknown keys without complaint, so deleting these fields outright would make an
     * existing `overrides` or `explicit` block vanish silently — a mapping that used to run and now
     * does nothing, with no message anywhere. Keeping them loosely typed lets [ConfigParser] see that
     * they are present and raise a migration error naming the transfer.
     *
     * Both are safe to delete once no deployed datastore contains them.
     */
    val overrides: List<Map<String, Any?>>? = null,
    val explicit: List<Map<String, Any?>>? = null,
)

data class ProgramRefDto(
    val programUid: String? = null,
)
