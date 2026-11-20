package org.dhis2.community.enrollmentfilters

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.dhis2.community.enrollmentfilters.models.AttributeConstraint

/**
 * Global holder of the current enrollment-list attribute-filter selections, mirroring the role the
 * core `FilterManager` singleton plays for the built-in filters.
 *
 * Keyed by attribute uid (globally unique). [version] increments on every change so both the UI
 * (recompose) and the search view model (re-run the query) can react through a single signal.
 */
object AttributeFilterState {

    private val constraints = mutableMapOf<String, AttributeConstraint>()

    private val _version = MutableStateFlow(0)

    /** Bumped on every mutation; observe to react to filter changes. */
    val version: StateFlow<Int> = _version

    @Synchronized
    fun get(uid: String): AttributeConstraint? = constraints[uid]

    /** Sets (or, when null/inactive, clears) the constraint for [uid]. No-op if nothing changes. */
    @Synchronized
    fun set(uid: String, constraint: AttributeConstraint?) {
        val normalized = constraint?.takeIf { it.isActive() }
        val current = constraints[uid]
        if (normalized == current) return
        if (normalized == null) {
            constraints.remove(uid)
        } else {
            constraints[uid] = normalized
        }
        _version.value++
    }

    @Synchronized
    fun clear(uid: String) = set(uid, null)

    @Synchronized
    fun clearAll() {
        if (constraints.isEmpty()) return
        constraints.clear()
        _version.value++
    }

    /** Active constraints only, keyed by attribute uid. */
    @Synchronized
    fun activeConstraints(): Map<String, AttributeConstraint> =
        constraints.filterValues { it.isActive() }

    @Synchronized
    fun isActive(uid: String): Boolean = constraints[uid]?.isActive() == true

    @Synchronized
    fun activeCount(): Int = constraints.count { it.value.isActive() }
}
