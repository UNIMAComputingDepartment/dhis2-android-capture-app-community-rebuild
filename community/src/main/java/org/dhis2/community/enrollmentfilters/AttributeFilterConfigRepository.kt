package org.dhis2.community.enrollmentfilters

import com.google.gson.Gson
import org.dhis2.community.enrollmentfilters.models.AttributeFilterConfig
import org.dhis2.community.enrollmentfilters.models.FilterOption
import org.dhis2.community.enrollmentfilters.models.FilterWidget
import org.dhis2.community.enrollmentfilters.models.FilterableAttribute
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import timber.log.Timber

/**
 * Resolves, per program, the ordered list of [FilterableAttribute] shown on the enrollment list.
 *
 * Source of truth:
 *  1. If the datastore config ([DATASTORE_NAMESPACE]/[DATASTORE_KEY]) has an entry for the program,
 *     its `attributes` allow-list (in order) is used, with optional per-attribute `typeOverrides`.
 *  2. Otherwise, the program's attributes flagged `displayInList` (ordered by sort order) are used.
 *
 * The widget for each attribute is derived from its value type ([FilterWidget.forAttribute]) unless
 * an override is present. Attributes whose value type can't be filtered (and have no option set) are
 * dropped.
 */
class AttributeFilterConfigRepository(
    private val d2: D2,
) {
    private val cache = mutableMapOf<String, List<FilterableAttribute>>()

    fun getFilterableAttributes(programUid: String?): List<FilterableAttribute> {
        if (programUid.isNullOrBlank()) return emptyList()
        cache[programUid]?.let { return it }

        val programFilter = readConfig()
            ?.programFilters.orEmpty()
            .firstOrNull { it?.programUid == programUid }

        val allowList =
            programFilter?.attributes.orEmpty()
                .filterNotNull()
                .filter { it.isNotBlank() }
        val overrides = programFilter?.typeOverrides.orEmpty()

        val orderedUids =
            if (allowList.isNotEmpty()) {
                allowList
            } else {
                displayInListAttributeUids(programUid)
            }

        val resolved =
            orderedUids.mapNotNull { uid ->
                resolveAttribute(uid, FilterWidget.fromOverride(overrides[uid]))
            }

        cache[programUid] = resolved
        return resolved
    }

    private fun displayInListAttributeUids(programUid: String): List<String> =
        d2.programModule()
            .programTrackedEntityAttributes()
            .byProgram().eq(programUid)
            .orderBySortOrder(RepositoryScope.OrderByDirection.ASC)
            .blockingGet()
            .filter { it.displayInList() == true }
            .mapNotNull { it.trackedEntityAttribute()?.uid() }

    private fun resolveAttribute(uid: String, override: FilterWidget?): FilterableAttribute? {
        val attribute =
            d2.trackedEntityModule().trackedEntityAttributes().uid(uid).blockingGet()
                ?: return null

        val optionSetUid = attribute.optionSet()?.uid()
        val widget = override ?: FilterWidget.forAttribute(attribute.valueType(), optionSetUid != null)
        if (widget == null) return null

        val options =
            if (widget == FilterWidget.OPTIONS && optionSetUid != null) {
                loadOptions(optionSetUid)
            } else {
                emptyList()
            }
        // An option-set widget with no resolvable options is useless — drop it.
        if (widget == FilterWidget.OPTIONS && options.isEmpty()) return null

        return FilterableAttribute(
            uid = uid,
            label = attribute.displayFormName() ?: attribute.displayName() ?: uid,
            widget = widget,
            valueType = attribute.valueType(),
            optionSetUid = optionSetUid,
            options = options,
        )
    }

    private fun loadOptions(optionSetUid: String): List<FilterOption> =
        d2.optionModule().options()
            .byOptionSetUid().eq(optionSetUid)
            .orderBySortOrder(RepositoryScope.OrderByDirection.ASC)
            .blockingGet()
            .mapNotNull { option ->
                val code = option.code() ?: return@mapNotNull null
                FilterOption(code = code, label = option.displayName() ?: code)
            }

    private fun readConfig(): AttributeFilterConfig? {
        return try {
            val entry =
                d2.dataStoreModule().dataStore()
                    .byNamespace().eq(DATASTORE_NAMESPACE)
                    .blockingGet()
                    .firstOrNull { it.key() == DATASTORE_KEY }
                    ?: return null

            val raw = entry.value()?.takeIf { it.isNotBlank() } ?: return null
            val json =
                if (raw.startsWith("JsonWrapper(json=")) {
                    raw.removePrefix("JsonWrapper(json=").removeSuffix(")")
                } else {
                    raw
                }
            Timber.d("Read enrollmentListFilters config: ${json.take(200)}")
            if (!json.trim().startsWith("{")) return null
            Gson().fromJson(json, AttributeFilterConfig::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing enrollmentListFilters config")
            null
        }
    }

    companion object {
        const val DATASTORE_NAMESPACE = "community_redesign"
        const val DATASTORE_KEY = "enrollmentListFilters"
    }
}
