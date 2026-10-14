package org.dhis2.community.tasking.di

import org.dhis2.community.tasking.repositories.TaskingRepository
import org.hisp.dhis.android.core.D2

/**
 * Holds the session's single [TaskingRepository].
 *
 * The repository memoises task state and clears that cache on every write, so two instances mean
 * one of them can hold task state the other has already changed - a sweep missing a task the
 * capture screen just created, or a screen showing a task the sweep has completed. Everything must
 * therefore share one instance.
 *
 * Dagger provides it at @PerServer scope and publishes it here via [publish]. The call sites that
 * live outside the Dagger graph - workers, services, broadcast receivers, fragments in this module,
 * which cannot depend on :app - read it back through [get].
 *
 * Publishing is what bounds the lifetime: a new server component means a new session, which
 * replaces the instance and drops the previous user's cached tasks. D2 itself is a process
 * singleton and survives logout, so it cannot be used as that boundary.
 */
object TaskingRepositoryProvider {

    @Volatile
    private var instance: TaskingRepository? = null

    /** Called by the Dagger provider when a session's component is built. */
    @Synchronized
    fun publish(repository: TaskingRepository): TaskingRepository {
        instance = repository
        return repository
    }

    /**
     * The published instance, or a fresh one if nothing has been published yet - which happens only
     * when a receiver or worker runs before a session component exists. That fallback is not
     * shared, so treat it as read-only.
     */
    @Synchronized
    fun get(d2: D2): TaskingRepository = instance ?: TaskingRepository(d2)

    /** Drops the session's instance so the next session cannot inherit its cache. */
    @Synchronized
    fun clear() {
        instance = null
    }
}
