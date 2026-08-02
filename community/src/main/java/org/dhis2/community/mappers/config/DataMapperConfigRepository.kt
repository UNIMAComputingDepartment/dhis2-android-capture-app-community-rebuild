package org.dhis2.community.mappers.config

import org.dhis2.community.common.readCommunityConfig
import org.dhis2.community.mappers.models.DataMapperConfigDto
import org.dhis2.community.mappers.models.ErrorSeverity
import org.dhis2.community.mappers.models.ValidatedConfig
import org.hisp.dhis.android.core.D2
import timber.log.Timber

/**
 * Reads and validates the `dataMappers` datastore entry.
 *
 * The validated result is cached for the life of the repository: a mapping run compiles many concepts
 * and would otherwise re-read metadata for each. [invalidate] drops the cache after a metadata sync.
 *
 * On an unparseable config the repository yields an empty configuration — the engine then does
 * nothing, which for clinical data is the only safe way to degrade. Doing something approximate is
 * not on the menu.
 */
class DataMapperConfigRepository(
    private val d2: D2,
    private val metadata: MetadataLookup = D2MetadataLookup(d2),
) {

    @Volatile
    private var cached: ValidatedConfig? = null

    fun config(): ValidatedConfig = cached ?: load().also { cached = it }

    fun invalidate() {
        cached = null
    }

    private fun load(): ValidatedConfig {
        val dto = d2.readCommunityConfig(DATASTORE_KEY, DataMapperConfigDto())
        val validated = ConfigValidator(metadata).validate(ConfigParser.parse(dto))

        val errors = validated.errors.count { it.severity == ErrorSeverity.ERROR }
        val warnings = validated.errors.size - errors
        val mappings = validated.transfers.sumOf { it.resolvedMappings.size }
        Timber.d(
            "Data mapping config loaded: %d concept(s), %d transfer(s), %d mapping(s), %d error(s), %d warning(s)",
            validated.concepts.size, validated.transfers.size, mappings, errors, warnings,
        )
        // Logged individually so a misconfiguration is diagnosable from a device log alone, without
        // needing the diagnostics screen.
        validated.errors.forEach { error ->
            val context = error.context?.let { " [$it]" }.orEmpty()
            when (error.severity) {
                ErrorSeverity.ERROR -> Timber.w("Data mapping config error%s: %s", context, error.message)
                ErrorSeverity.WARNING -> Timber.i("Data mapping config warning%s: %s", context, error.message)
            }
        }

        return validated
    }

    companion object {
        /** Key within the `community_redesign` datastore namespace. */
        const val DATASTORE_KEY = "dataMappers"
    }
}
