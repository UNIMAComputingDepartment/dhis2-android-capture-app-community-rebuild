package org.dhis2.community.mappers.di

import android.content.Context
import org.dhis2.community.mappers.config.D2MetadataLookup
import org.dhis2.community.mappers.config.DataMapperConfigRepository
import org.dhis2.community.mappers.engine.DataMappingEngine
import org.dhis2.community.mappers.models.MappingReport
import org.dhis2.community.mappers.models.Trigger
import org.dhis2.community.mappers.models.TriggerContext
import org.dhis2.community.mappers.resolve.D2TrackerDataSource
import org.dhis2.community.mappers.resolve.ValueResolver
import org.dhis2.community.mappers.write.MappingLedger
import org.dhis2.community.mappers.write.NoOpMappingLedger
import org.dhis2.community.mappers.write.SharedPreferencesMappingLedger
import org.hisp.dhis.android.core.D2
import timber.log.Timber

/**
 * Assembles a [DataMappingEngine] from a [D2] instance.
 *
 * A plain factory rather than a Dagger module: the community module has no Dagger of its own, and the
 * engine is needed from several presenters that already receive `D2`. Callers that do have Dagger can
 * still provide [DataMappingEngine] directly.
 */
object DataMappers {

    @Volatile
    private var cachedEngine: DataMappingEngine? = null

    @Volatile
    private var cachedRepository: DataMapperConfigRepository? = null

    @Volatile
    private var appContext: Context? = null

    /**
     * Supplies the application context once, from `Application.onCreate`.
     *
     * Held here so the many presenters that already receive a [D2] do not each have to thread a
     * Context through their Dagger modules just to give the provenance ledger somewhere to live.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun repository(d2: D2): DataMapperConfigRepository =
        cachedRepository ?: DataMapperConfigRepository(d2).also { cachedRepository = it }

    fun engine(d2: D2): DataMappingEngine =
        cachedEngine ?: build(d2).also { cachedEngine = it }

    /** Drops cached config and engine; call after a metadata sync so config changes take effect. */
    fun invalidate() {
        cachedRepository?.invalidate()
        cachedRepository = null
        cachedEngine = null
    }

    private fun build(d2: D2): DataMappingEngine {
        val repository = repository(d2)
        val data = D2TrackerDataSource(d2)
        return DataMappingEngine(
            configProvider = repository::config,
            resolver = ValueResolver(data),
            data = data,
            metadata = D2MetadataLookup(d2),
            ledger = ledger(),
        )
    }

    private fun ledger(): MappingLedger = appContext?.let { SharedPreferencesMappingLedger(it) }
        ?: run {
            // Without a Context there is nowhere to persist provenance. Mapping still works, but the
            // human-edit guard cannot fire, so conservative policies do the protecting instead.
            Timber.w("Data mapping running without a Context; manual-edit protection is unavailable")
            NoOpMappingLedger
        }

    /**
     * Runs mapping for a newly created target enrollment, swallowing any failure.
     *
     * Mapping is an enhancement to enrollment, never a precondition for it: a mapping problem must
     * not surface as a failed enrollment or a lost record, so this never propagates.
     *
     * The source arguments are optional and usually should be omitted. They exist so an
     * auto-enrollment can say which event caused it; a **manual** enrollment has no such origin, and
     * passing null means "any source", so every transfer into this program fires. That is the right
     * reading: enrolling a child already in EPI into IMCI by hand should pull the EPI data across
     * exactly as an automatic enrollment would. The trigger is TARGET_ENROLLMENT_CREATED — created,
     * not created-by-automation.
     *
     * `@JvmOverloads` because the manual call sites are the app module's Java repositories, which
     * cannot see Kotlin default arguments.
     */
    @JvmOverloads
    fun runForNewEnrollment(
        d2: D2,
        teiUid: String,
        targetProgramUid: String,
        targetEnrollmentUid: String,
        sourceProgramUid: String? = null,
        sourceEnrollmentUid: String? = null,
        sourceEventUid: String? = null,
    ): MappingReport? = runCatching {
        engine(d2).run(
            TriggerContext(
                trigger = Trigger.TARGET_ENROLLMENT_CREATED,
                teiUid = teiUid,
                targetProgramUid = targetProgramUid,
                targetEnrollmentUid = targetEnrollmentUid,
                sourceProgramUid = sourceProgramUid,
                sourceEnrollmentUid = sourceEnrollmentUid,
                sourceEventUid = sourceEventUid,
            ),
        )
    }.onFailure { Timber.e(it, "Data mapping failed for new enrollment in %s", targetProgramUid) }
        .getOrNull()

    /** Runs mapping after a source event is saved, propagating updates to existing target enrollments. */
    fun runForSavedEvent(
        d2: D2,
        teiUid: String,
        sourceProgramUid: String,
        sourceEnrollmentUid: String?,
        sourceEventUid: String?,
    ): MappingReport? = runCatching {
        engine(d2).run(
            TriggerContext(
                trigger = Trigger.SOURCE_EVENT_SAVED,
                teiUid = teiUid,
                sourceProgramUid = sourceProgramUid,
                sourceEnrollmentUid = sourceEnrollmentUid,
                sourceEventUid = sourceEventUid,
            ),
        )
    }.onFailure { Timber.e(it, "Data mapping failed after saving event in %s", sourceProgramUid) }
        .getOrNull()
}
