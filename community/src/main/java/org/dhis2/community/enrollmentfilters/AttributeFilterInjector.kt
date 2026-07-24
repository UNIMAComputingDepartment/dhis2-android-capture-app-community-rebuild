package org.dhis2.community.enrollmentfilters

import android.os.Build
import androidx.annotation.RequiresApi
import org.dhis2.community.enrollmentfilters.models.AttributeConstraint
import org.dhis2.community.enrollmentfilters.models.FilterWidget
import org.dhis2.community.enrollmentfilters.models.FilterableAttribute
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.trackedentity.search.TrackedEntitySearchCollectionRepository
import org.hisp.dhis.android.core.trackedentity.search.TrackedEntitySearchItem
import timber.log.Timber

/**
 * Bridges [AttributeFilterState] into the two core search seams.
 *
 * Equality-style filters (keyword/options/boolean) are pushed into the SDK query in [applyTo] — they
 * run at the DB/server level via `byFilter(uid).like/in/eq`. Range filters (number/age/date) can't be
 * expressed by the SDK's public search API, so they are evaluated client-side in [rangeMatches] over
 * the loaded results (see [hasRangeFilters] to skip the post-filter entirely when none are active).
 *
 * State lives in the [AttributeFilterState] singleton; this class is safe to construct per call site.
 */
class AttributeFilterInjector(
    private val d2: D2,
    private val configRepository: AttributeFilterConfigRepository = AttributeFilterConfigRepository(d2),
) {
    // Caches attribute values fetched per (teiUid, attributeUid) during a post-filter pass.
    private val valueCache = mutableMapOf<String, String?>()

    /** The resolved, ordered filterable attributes for [programUid] (for rendering the filter UI). */
    fun filterableAttributes(programUid: String?): List<FilterableAttribute> =
        configRepository.getFilterableAttributes(programUid)

    /** Applies keyword/options/boolean constraints to [query]. Returns the (possibly) narrowed query. */
    fun applyTo(
        query: TrackedEntitySearchCollectionRepository,
        programUid: String?,
    ): TrackedEntitySearchCollectionRepository {
        val attributes = filterableByUid(programUid)
        if (attributes.isEmpty()) return query

        var result = query
        AttributeFilterState.activeConstraints().forEach { (uid, constraint) ->
            val widget = attributes[uid]?.widget ?: return@forEach
            result =
                when (widget) {
                    FilterWidget.KEYWORD ->
                        (constraint as? AttributeConstraint.Keyword)?.let {
                            result.byFilter(uid).like(it.text.trim())
                        } ?: result

                    FilterWidget.OPTIONS ->
                        (constraint as? AttributeConstraint.Options)?.takeIf { it.codes.isNotEmpty() }?.let {
                            result.byFilter(uid).`in`(it.codes.toList())
                        } ?: result

                    FilterWidget.BOOLEAN ->
                        (constraint as? AttributeConstraint.Bool)?.value?.let {
                            result.byFilter(uid).eq(it.toString())
                        } ?: result

                    // Ranges are handled by rangeMatches (post-filter), not here.
                    FilterWidget.NUMBER_RANGE,
                    FilterWidget.AGE_RANGE,
                    FilterWidget.DATE_RANGE,
                    -> result
                }
        }
        return result
    }

    /** True if any active range constraint applies to [programUid]; lets callers skip the post-filter. */
    fun hasRangeFilters(programUid: String?): Boolean {
        val attributes = filterableByUid(programUid)
        return AttributeFilterState.activeConstraints().any { (uid, _) ->
            attributes[uid]?.widget?.isRange == true
        }
    }

    /**
     * Evaluates every active range constraint (AND) against [item]. An item whose value for a range
     * attribute is missing or unparseable does not match.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun rangeMatches(
        item: TrackedEntitySearchItem,
        programUid: String?,
    ): Boolean {
        val attributes = filterableByUid(programUid)
        val rangeConstraints =
            AttributeFilterState.activeConstraints().filter { (uid, _) ->
                attributes[uid]?.widget?.isRange == true
            }
        if (rangeConstraints.isEmpty()) return true

        return rangeConstraints.all { (uid, constraint) ->
            val value = valueFor(item, uid)
            when (constraint) {
                is AttributeConstraint.NumberRange -> AttributeRangeEvaluator.numberInRange(value, constraint)
                is AttributeConstraint.AgeRange -> AttributeRangeEvaluator.ageInRange(value, constraint)
                is AttributeConstraint.DateRange -> AttributeRangeEvaluator.dateInRange(value, constraint)
                else -> true
            }
        }
    }

    /** Clears the per-pass value cache. Call when the underlying data may have changed. */
    fun resetValueCache() = valueCache.clear()

    /** Attribute value for a TEI: prefer the value carried on the list item, else read from d2. */
    private fun valueFor(item: TrackedEntitySearchItem, attributeUid: String): String? {
        item.attributeValues
            ?.firstOrNull { it.attribute == attributeUid }
            ?.let { return it.value }

        val teiUid = item.uid() ?: return null
        return valueCache.getOrPut("$teiUid|$attributeUid") {
            try {
                d2.trackedEntityModule()
                    .trackedEntityAttributeValues()
                    .value(attributeUid, teiUid)
                    .blockingGet()
                    ?.value()
            } catch (e: Exception) {
                Timber.w(e, "Failed to read attribute %s for TEI %s", attributeUid, teiUid)
                null
            }
        }
    }

    private fun filterableByUid(programUid: String?): Map<String, FilterableAttribute> =
        configRepository.getFilterableAttributes(programUid).associateBy { it.uid }
}
