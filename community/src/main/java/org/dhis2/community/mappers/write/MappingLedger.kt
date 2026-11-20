package org.dhis2.community.mappers.write

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber

/** What a previous run wrote to one target, so a later run can tell its own work from a human's. */
data class LedgerEntry(
    val mappingId: String,
    val writtenValue: String,
    val writtenAt: Long,
)

/**
 * Remembers what the engine itself wrote.
 *
 * Without this, mapping is not safely repeatable: it runs on every enrollment and event save, so a
 * clinician who corrects a carried-over value in the target program would see the next save
 * silently revert it (failure mode F7). Comparing the target's current value against the last value
 * *we* wrote is what distinguishes "still ours to update" from "a human has taken ownership".
 */
interface MappingLedger {

    fun lastWrite(teiUid: String, targetKey: String): LedgerEntry?

    fun record(teiUid: String, targetKey: String, entry: LedgerEntry)

    fun forget(teiUid: String)
}

/** No-op ledger. Every target looks untouched, so policies fall back to their unguarded behaviour. */
object NoOpMappingLedger : MappingLedger {
    override fun lastWrite(teiUid: String, targetKey: String): LedgerEntry? = null
    override fun record(teiUid: String, targetKey: String, entry: LedgerEntry) = Unit
    override fun forget(teiUid: String) = Unit
}

/**
 * SharedPreferences-backed ledger, one entry per TEI holding that TEI's mapped targets.
 *
 * Keyed per TEI rather than per field so the number of preference entries tracks the number of TEIs
 * rather than TEIs x mapped fields. Deliberately local-only and never synced: it records what *this
 * device* wrote, which is exactly the scope of the question being asked.
 *
 * A Room table would scale further; the interface is the seam for that swap if the entry count ever
 * justifies it.
 */
class SharedPreferencesMappingLedger(
    context: Context,
    private val gson: Gson = Gson(),
) : MappingLedger {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val entryMapType = object : TypeToken<Map<String, LedgerEntry>>() {}.type

    override fun lastWrite(teiUid: String, targetKey: String): LedgerEntry? =
        entriesFor(teiUid)[targetKey]

    override fun record(teiUid: String, targetKey: String, entry: LedgerEntry) {
        val updated = entriesFor(teiUid) + (targetKey to entry)
        prefs.edit().putString(teiUid, gson.toJson(updated)).apply()
    }

    override fun forget(teiUid: String) {
        prefs.edit().remove(teiUid).apply()
    }

    private fun entriesFor(teiUid: String): Map<String, LedgerEntry> {
        val raw = prefs.getString(teiUid, null) ?: return emptyMap()
        return try {
            gson.fromJson<Map<String, LedgerEntry>>(raw, entryMapType) ?: emptyMap()
        } catch (e: Exception) {
            // A corrupt entry must not block mapping. Losing provenance degrades to the conservative
            // behaviour (the value looks human-edited), which errs toward not overwriting.
            Timber.w(e, "Corrupt mapping ledger entry for TEI %s; treating as empty", teiUid)
            emptyMap()
        }
    }

    private companion object {
        const val PREFS_NAME = "community_mapping_ledger"
    }
}
