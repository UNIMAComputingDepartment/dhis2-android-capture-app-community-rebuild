package org.dhis2.community.derivation

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.dhis2.community.medicalHistory.engine.MHEngine
import org.dhis2.community.medicalHistory.repository.MHRepository
import org.dhis2.community.tasking.di.TaskingRepositoryProvider
import org.dhis2.community.tasking.engine.TaskingEngine
import org.dhis2.community.tasking.engine.TaskingWorker
import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Manager
import timber.log.Timber
import java.util.Date

/**
 * Derives tasks and medical history summaries from local data.
 *
 * This used to run inside [org.dhis2.data.service.SyncDataWorker.doWork], which put it in the
 * sync's failure, transaction and execution-time domain: a rule-evaluation bug could cancel the
 * event download, a failed insert could poison the database connection for the rest of the sync,
 * and the sweep ate into the same ten-minute window WorkManager gives the sync. It is its own
 * worker now.
 *
 * Two modes, because derivation has two different triggers:
 *
 *  - [MODE_SCHEDULED] is time-driven and runs on its own daily schedule. A quarter rolls over and
 *    a task becomes due whether or not anyone synced, so this must not hang off a sync. It is a
 *    full sweep, which also makes it the backstop for anything the scoped pass missed.
 *  - [MODE_POST_SYNC] is change-driven and runs after a sync. Data captured on another device
 *    fires no capture trigger, so something has to react to it - but only for what actually
 *    arrived, via [CommunityChangeScope].
 *
 * Locally captured data is already covered by the capture triggers in EventCapture and Enrollment,
 * so neither mode exists to serve it.
 */
class CommunityDerivationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // The evaluators are built on java.time.
            Timber.tag(TAG).d("Skipping derivation: requires API 26+")
            return Result.success()
        }

        val mode = inputData.getString(KEY_MODE) ?: MODE_SCHEDULED

        return try {
            val d2 = D2Manager.getD2()
            val taskingRepository = TaskingRepositoryProvider.get(d2)

            when (mode) {
                MODE_POST_SYNC -> runPostSync(d2, taskingRepository)
                else -> runScheduled(d2, taskingRepository)
            }
            Result.success()
        } catch (t: Throwable) {
            // Deliberately not retried: the next scheduled pass is a full sweep and will pick up
            // whatever this run missed. Retrying a sweep that just failed tends to fail again.
            Timber.tag(TAG).e(t, "Derivation pass failed (mode=%s)", mode)
            Result.success()
        }
    }

    /** Time-driven: everything, on the schedule, regardless of what synced. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun runScheduled(d2: D2, taskingRepository: TaskingRepository) {
        Timber.tag(TAG).d("Scheduled derivation: full sweep")
        val startedAt = Date()

        val worker = TaskingWorker(taskingRepository)
        worker.defaultingWorker()
        worker.periodicWorker()

        MHEngine(MHRepository(d2)).medicalHistoryWorker()

        CommunityChangeScope(d2).markPassCompleted(startedAt)
    }

    /** Change-driven: only what the server sent us since the last pass. */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun runPostSync(d2: D2, taskingRepository: TaskingRepository) {
        val startedAt = Date()
        val scope = CommunityChangeScope(d2)
        val changed = scope.changedTeiUids()

        if (changed == null) {
            // No watermark - a fresh install, a data clear or an account switch. Everything is
            // effectively new, so derive everything rather than silently skipping it all.
            Timber.tag(TAG).d("Post-sync derivation: no watermark, falling back to a full sweep")
            runScheduled(d2, taskingRepository)
            return
        }

        if (changed.isEmpty()) {
            Timber.tag(TAG).d("Post-sync derivation: nothing changed")
            scope.markPassCompleted(startedAt)
            return
        }

        Timber.tag(TAG).d("Post-sync derivation: %d changed TEI(s)", changed.size)

        evaluateTasksFor(taskingRepository, changed)
        MHEngine(MHRepository(d2)).medicalHistoryFor(changed)

        scope.markPassCompleted(startedAt)
    }

    /**
     * Runs the same per-TEI evaluation the capture screens run, for each changed TEI in each
     * configured program. Using [TaskingEngine.evaluate] rather than a second sweep implementation
     * is the point: capture and sync then share one code path and cannot drift apart.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun evaluateTasksFor(
        repository: TaskingRepository,
        teiUids: Set<String>,
    ) {
        val config = repository.getTaskingConfig()
        if (config.programTasks.isEmpty()) return

        if (!repository.hasDataAccessToTasking()) {
            Timber.tag(TAG).d("User has no access to the tasking program; skipping task evaluation")
            return
        }

        val engine = TaskingEngine(repository)

        config.programTasks.forEach { programTask ->
            val targetProgramUid = programTask.programUid

            teiUids.forEach { teiUid ->
                val orgUnitUid = repository.getOrgUnit(teiUid)
                val enrollmentUid = repository.getLatestEnrollment(teiUid, targetProgramUid)?.uid()

                if (orgUnitUid.isNullOrEmpty() || enrollmentUid.isNullOrEmpty()) {
                    // Not enrolled in this program - normal, most TEIs are not in most programs.
                    return@forEach
                }

                try {
                    engine.evaluate(
                        targetProgramUid = targetProgramUid,
                        sourceTieUid = teiUid,
                        sourceTieOrgUnitUid = orgUnitUid,
                        sourceTieProgramEnrollment = enrollmentUid,
                    )
                } catch (t: Throwable) {
                    // One bad TEI must not cost the rest of the pass.
                    Timber.tag(TAG).e(t, "Task evaluation failed for tei=%s program=%s", teiUid, targetProgramUid)
                }
            }
        }
    }

    companion object {
        const val TAG = "CommunityDerivation"

        const val KEY_MODE = "derivation_mode"
        const val MODE_SCHEDULED = "scheduled"
        const val MODE_POST_SYNC = "post_sync"
    }
}
