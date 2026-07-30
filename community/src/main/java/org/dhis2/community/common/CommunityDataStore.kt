package org.dhis2.community.common

import com.google.gson.Gson
import org.hisp.dhis.android.core.D2
import timber.log.Timber

/** Datastore namespace all community customizations read their config from. */
const val COMMUNITY_NAMESPACE = "community_redesign"

/**
 * Reads [key] from the community datastore namespace and returns the raw JSON, or null when the
 * entry is absent/blank/not an object.
 *
 * Datastore values sometimes arrive wrapped as `JsonWrapper(json={...})` (Gson's toString of the
 * SDK wrapper) rather than bare JSON; this unwraps that shape in one place so every config reader
 * doesn't reinvent (and drift on) the same fragile string surgery.
 */
fun D2.readCommunityJson(key: String): String? {
    val entry = dataStoreModule().dataStore()
        .byNamespace().eq(COMMUNITY_NAMESPACE)
        .blockingGet()
        .firstOrNull { it.key() == key }
        ?: run {
            Timber.w("No '%s' entry found in '%s' namespace", key, COMMUNITY_NAMESPACE)
            return null
        }

    val raw = entry.value()?.takeIf { it.isNotBlank() } ?: return null
    val json = if (raw.startsWith("JsonWrapper(json=")) {
        raw.removePrefix("JsonWrapper(json=").removeSuffix(")")
    } else {
        raw
    }
    return json.takeIf { it.trim().startsWith("{") }
        ?: run {
            Timber.w("Community config '%s' is not a JSON object", key)
            null
        }
}

/**
 * Reads and parses the community config at [key] into [T], returning [fallback] on any missing
 * entry or parse error. Parsing never throws to the caller.
 */
inline fun <reified T> D2.readCommunityConfig(key: String, fallback: T): T =
    try {
        readCommunityJson(key)?.let { Gson().fromJson(it, T::class.java) } ?: fallback
    } catch (e: Exception) {
        Timber.e(e, "Error parsing community config '%s'", key)
        fallback
    }
