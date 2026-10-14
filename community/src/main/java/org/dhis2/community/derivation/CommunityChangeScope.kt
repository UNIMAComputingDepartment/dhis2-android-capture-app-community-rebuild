package org.dhis2.community.derivation

import org.hisp.dhis.android.core.D2
import timber.log.Timber
import java.util.Date

/**
 * Works out which TEIs a derivation pass needs to look at.
 *
 * A sync brings down records that were captured elsewhere, and no capture trigger ever fires for
 * those. Rather than re-derive every TEI afterwards, we record when the last pass ran and ask the
 * database what has moved since.
 *
 * The watermark deliberately lives in the SDK's LocalDataStore, which `wipeModule().wipeData()`
 * clears along with everything else. So a data clear, an account switch or a fresh install all
 * leave no watermark, and [changedTeiUids] answers null - meaning "no scope, derive everything".
 * That is what keeps a wipe-and-resync correct: the re-downloaded records carry their original
 * server timestamps, often far older than any watermark, so an incremental pass would skip them.
 *
 * One narrow gap remains: records re-downloaded with old server timestamps while a watermark does
 * survive. The scheduled daily pass is a full sweep precisely so that case is corrected within a
 * day rather than never.
 */
class CommunityChangeScope(
    private val d2: D2,
) {
    /**
     * TEIs touched since the last pass, or null when there is no watermark and the caller should
     * fall back to a full sweep.
     *
     * A TEI counts as touched if it, one of its enrollments, or one of its events changed - the
     * TEI row alone is not enough, since a downloaded event does not necessarily restamp its
     * parent.
     */
    fun changedTeiUids(): Set<String>? {
        val since = readWatermark() ?: return null

        val fromTeis = d2.trackedEntityModule().trackedEntityInstances()
            .byLastUpdated().after(since)
            .blockingGetUids()

        val changedEnrollments = d2.enrollmentModule().enrollments()
            .byLastUpdated().after(since)
            .blockingGet()

        val enrollmentUidsWithChangedEvents = d2.eventModule().events()
            .byLastUpdated().after(since)
            .blockingGet()
            .mapNotNull { it.enrollment() }
            .toSet()

        val fromEvents = if (enrollmentUidsWithChangedEvents.isEmpty()) {
            emptyList()
        } else {
            d2.enrollmentModule().enrollments()
                .byUid().`in`(enrollmentUidsWithChangedEvents.toList())
                .blockingGet()
                .mapNotNull { it.trackedEntityInstance() }
        }

        return buildSet {
            addAll(fromTeis)
            addAll(changedEnrollments.mapNotNull { it.trackedEntityInstance() })
            addAll(fromEvents)
        }
    }

    /**
     * Records the point this pass started from. Call it with the timestamp taken *before* the pass
     * ran, never after: anything that changed while the pass was running must be picked up by the
     * next one rather than falling in the gap.
     */
    fun markPassCompleted(startedAt: Date) {
        try {
            d2.dataStoreModule().localDataStore()
                .value(WATERMARK_KEY)
                .blockingSet(startedAt.time.toString())
        } catch (e: Exception) {
            // Losing the watermark costs a full sweep next time, which is safe. Never let it
            // fail the pass that just did the real work.
            Timber.tag(TAG).w(e, "Could not store derivation watermark")
        }
    }

    private fun readWatermark(): Date? =
        try {
            d2.dataStoreModule().localDataStore()
                .value(WATERMARK_KEY)
                .blockingGet()
                ?.value()
                ?.toLongOrNull()
                ?.let(::Date)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not read derivation watermark; falling back to a full sweep")
            null
        }

    private companion object {
        const val TAG = "CommunityDerivation"
        const val WATERMARK_KEY = "CommunityDerivationWatermark"
    }
}
