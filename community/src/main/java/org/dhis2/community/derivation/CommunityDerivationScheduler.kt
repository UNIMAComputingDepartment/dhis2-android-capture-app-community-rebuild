package org.dhis2.community.derivation

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Schedules [CommunityDerivationWorker]. Follows the same shape as TaskReminderWorkScheduler:
 * the community module owns its own scheduling rather than going through :app's WorkerType graph.
 */
object CommunityDerivationScheduler {

    private const val WORK_NAME_SCHEDULED = "community_derivation_scheduled"
    private const val WORK_NAME_POST_SYNC = "community_derivation_post_sync"

    private const val MALAWI_TIMEZONE = "Africa/Johannesburg"

    /** Overnight, when nobody is waiting on the device. */
    private const val RUN_HOUR = 2
    private const val RUN_MINUTE = 0

    /**
     * The daily time-driven pass. Deliberately not run at app start: a quarter rolling over or a
     * due date passing is a function of the clock, not of the app being opened, and startup is
     * already busy enough.
     */
    @JvmStatic
    fun scheduleDaily(context: Context) {
        try {
            val initialDelayMinutes = calculateInitialDelayMinutes(RUN_HOUR, RUN_MINUTE)

            val request = PeriodicWorkRequestBuilder<CommunityDerivationWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .setInputData(modeData(CommunityDerivationWorker.MODE_SCHEDULED))
                .addTag(WORK_NAME_SCHEDULED)
                .build()

            // KEEP, so re-scheduling on every login does not keep pushing the run back by a day.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_SCHEDULED,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )

            Timber.tag(CommunityDerivationWorker.TAG)
                .d("Daily derivation scheduled for %02d:%02d (in %d min)", RUN_HOUR, RUN_MINUTE, initialDelayMinutes)
        } catch (e: Exception) {
            Timber.tag(CommunityDerivationWorker.TAG).e(e, "Could not schedule daily derivation")
        }
    }

    /**
     * The change-driven pass, enqueued when a data sync finishes. Separate work rather than part
     * of the sync worker, so the sync's result and time budget stay its own.
     */
    @JvmStatic
    fun runAfterSync(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<CommunityDerivationWorker>()
                .setInputData(modeData(CommunityDerivationWorker.MODE_POST_SYNC))
                .addTag(WORK_NAME_POST_SYNC)
                .build()

            // REPLACE: if a pass is already queued for an earlier sync, the newer one supersedes
            // it - both would read the same watermark and derive the same set.
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_POST_SYNC,
                ExistingWorkPolicy.REPLACE,
                request,
            )

            Timber.tag(CommunityDerivationWorker.TAG).d("Post-sync derivation enqueued")
        } catch (e: Exception) {
            Timber.tag(CommunityDerivationWorker.TAG).e(e, "Could not enqueue post-sync derivation")
        }
    }

    @JvmStatic
    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_SCHEDULED)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_POST_SYNC)
        } catch (e: Exception) {
            Timber.tag(CommunityDerivationWorker.TAG).e(e, "Could not cancel derivation work")
        }
    }

    private fun modeData(mode: String): Data =
        Data.Builder()
            .putString(CommunityDerivationWorker.KEY_MODE, mode)
            .build()

    private fun calculateInitialDelayMinutes(hour: Int, minute: Int): Long {
        val zone = TimeZone.getTimeZone(MALAWI_TIMEZONE)
        val target = Calendar.getInstance(zone).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = Calendar.getInstance(zone)
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        return TimeUnit.MILLISECONDS.toMinutes(target.timeInMillis - now.timeInMillis)
    }
}
