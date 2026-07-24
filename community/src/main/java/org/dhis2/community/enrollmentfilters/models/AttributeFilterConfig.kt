package org.dhis2.community.enrollmentfilters.models

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type

/**
 * Datastore config (namespace `community_redesign`, key `enrollmentListFilters`) that curates which
 * attributes are filterable per program. Optional: when a program has no entry, the repository
 * falls back to that program's `displayInList` attributes.
 *
 * Parsed by Gson via reflection, which bypasses Kotlin's non-null enforcement — every field is
 * nullable and must be read with `.orEmpty()`/null-guards (same lesson as the tasking config).
 */
data class AttributeFilterConfig(
    val programFilters: List<ProgramFilter>? = null,
) {
    data class ProgramFilter(
        val programUid: String? = null,
        // Allow-list of attribute uids, in display order. Null/empty -> fall back to displayInList.
        val attributes: List<String>? = null,
        // Optional per-attribute widget override, e.g. {"zDhUuAYrxNC": "AGE_RANGE"}.
        // Tolerant of a malformed value (e.g. an empty array `[]` instead of `{}`): a non-object
        // deserializes to an empty map rather than throwing and discarding the whole config.
        @JsonAdapter(LenientStringMapDeserializer::class)
        val typeOverrides: Map<String, String>? = null,
    )

    /** Reads a JSON object of string->string, treating any non-object (array, null, primitive) as empty. */
    class LenientStringMapDeserializer : JsonDeserializer<Map<String, String>> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?,
        ): Map<String, String> {
            if (json == null || !json.isJsonObject) return emptyMap()
            return json.asJsonObject.entrySet().mapNotNull { (key, value) ->
                if (value != null && value.isJsonPrimitive) key to value.asString else null
            }.toMap()
        }
    }
}
