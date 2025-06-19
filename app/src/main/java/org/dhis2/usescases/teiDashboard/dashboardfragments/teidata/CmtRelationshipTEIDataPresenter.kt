package org.dhis2.usescases.teiDashboard.dashboardfragments.teidata

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import org.dhis2.commons.orgunitselector.OUTreeFragment
import org.dhis2.commons.orgunitselector.OrgUnitSelectorScope.ProgramCaptureScope
import org.dhis2.commons.schedulers.SchedulerProvider
import org.dhis2.community.relationships.CmtRelationshipTypeViewModel
import org.dhis2.community.relationships.Relationship
import org.dhis2.community.relationships.RelationshipConfig
import org.dhis2.community.relationships.RelationshipConstraintSide
import org.dhis2.community.relationships.RelationshipRepository
import org.dhis2.community.workflow.WorkflowConfig
import org.dhis2.community.workflow.WorkflowRepository
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import timber.log.Timber
import java.util.concurrent.Callable

class CmtRelationshipTEIDataPresenter(
    private val view: TEIDataContracts.View,
    private val d2: D2,
    private var programUid: String?,
    private val teiUid: String,
    private val schedulerProvider: SchedulerProvider,
    private val relationshipRepository: RelationshipRepository,
    private val workflowRepository: WorkflowRepository
) {

    private val compositeDisposable: CompositeDisposable = CompositeDisposable()

    private val relationshipsConfig: MutableList<Relationship> = mutableListOf()

    private lateinit var workflowConfig: WorkflowConfig

    private val _relationships = MutableLiveData<List<CmtRelationshipTypeViewModel>>()
    val relationships: LiveData<List<CmtRelationshipTypeViewModel>> = _relationships

    private val _searchedEntities = MutableLiveData<CmtRelationshipTypeViewModel>()
    val searchedEntities: LiveData<CmtRelationshipTypeViewModel> = _searchedEntities

    init {
        programUid?.let {
            initializeRelationships()
            initializeWorkflow()
        }
    }

    private fun initializeWorkflow() {
        compositeDisposable.add(
            Single.fromCallable<WorkflowConfig>(Callable<WorkflowConfig> { workflowRepository.getWorkflowConfig() })
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe(
                    { workflowConfig: WorkflowConfig ->
                        Timber.d("WorkflowConfig: %s", workflowConfig)
                        this.workflowConfig = workflowConfig
                    },
                    { t: Throwable? -> Timber.e(t) }
                )
        )
    }

    fun initializeRelationships() {
        compositeDisposable.add(
            Single.fromCallable<RelationshipConfig>(Callable<RelationshipConfig> { relationshipRepository.getRelationshipConfig() })
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe(
                    { relationshipConfig: RelationshipConfig ->
                        Timber.d("RelationshipConfig: %s", relationshipConfig)
                        this.relationshipsConfig.clear()
                        this.relationshipsConfig.addAll(
                            relationshipConfig.relationships.filter {
                                it.access.targetProgramUid == programUid
                            }
                        )
                        if (relationshipsConfig.isNotEmpty()) {
                            retrieveRelationships()
                        }
                    },
                    { t: Throwable? -> Timber.e(t) }
                )
        )
    }

    internal fun retrieveRelationships() {
        compositeDisposable.add(
            Single.zip(
                relationshipsConfig.map { relationship ->
                    Single.fromCallable {
                        relationshipRepository.getRelatedTeis(
                            teiUid = teiUid,
                            relationshipTypeUid = relationship.access.targetRelationshipUid,
                            relationship = relationship
                        )
                    }.subscribeOn(schedulerProvider.io())
                        .map { teis ->
                            CmtRelationshipTypeViewModel(
                                uid = relationship.access.targetRelationshipUid,
                                name = "",
                                description = relationship.description,
                                relatedTeis = teis,
                                relatedProgramName = relationship.relatedProgram.teiTypeName,
                                relatedProgramUid = relationship.relatedProgram.programUid,
                            )
                        }
                }
            ) { results ->
                results.map { it as CmtRelationshipTypeViewModel }
            }
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe(
                    { relationshipMap ->
                        _relationships.value = relationshipMap
                    },
                    { error ->
                        Timber.e(error)
                    }
                )
        )
    }

    fun onSearchTEIs(keyword: String, relationshipTypeUid: String) {
        compositeDisposable.add(
            Single.fromCallable {
                relationshipRepository.searchEntities(
                    relationship = relationshipsConfig.first { it.access.targetRelationshipUid == relationshipTypeUid },
                    keyword = keyword
                )
            }.subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe(
                    { _searchedEntities.value = it },
                    { error ->
                        Timber.e(error)
                    }
                )
        )
    }

    private fun checkForAutoCreation(programUid: String, teiUid: String) {
        val autoCreationConfig = workflowConfig.entityAutoCreation.firstOrNull {
            it.triggerProgram == programUid
        }
        autoCreationConfig?.let {
            compositeDisposable.add(
                Single.fromCallable {
                    workflowRepository.autoCreateEntity(
                        teiUid = teiUid,
                        autoCreationConfig = it
                    )
                }.subscribeOn(schedulerProvider.io())
                    .observeOn(schedulerProvider.ui())
                    .subscribe(
                        { Timber.d("Created ${it.first}") },
                        {error -> Timber.e(error)}
                    )
            )
        }
    }

    fun addRelationship(teiUid: String, relationshipTypeUid: String) {
        compositeDisposable.add(
            Single.fromCallable {
                relationshipRepository.createAndAddRelationship(
                    teiUid = this.teiUid,
                    relationshipTypeUid = relationshipTypeUid,
                    selectedTeiUid = teiUid,
                    relationshipSide = RelationshipConstraintSide.TO
                )
            }
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe(
                    { retrieveRelationships() },
                    { error -> Timber.e(error) }
                )
        )
    }

    private var relationshipToAdd: String? = null
    private var programUidToCheck: String? = null
    fun addRelationship(teiUid: String) {
        relationshipToAdd?.let { this.addRelationship(teiUid, relationshipTypeUid = it) }
        programUidToCheck?.let { checkForAutoCreation(it, teiUid) }
        relationshipToAdd = null
        programUidToCheck = null
    }

    fun enroll(
        programUid: String,
        relationshipTypeUid: String
    ) {
        compositeDisposable.add(
            d2.organisationUnitModule().organisationUnits()
                .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .byProgramUids(listOf<String>(programUid)).get().toObservable()
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe(
                    Consumer { allOrgUnits ->
                        if (allOrgUnits!!.size > 1) {
                            OUTreeFragment.Builder()
                                .singleSelection()
                                .onSelection { selectedOrgUnits ->
                                    if (!selectedOrgUnits.isEmpty()) enrollInOrgUnit(
                                        selectedOrgUnits[0].uid(),
                                        programUid,
                                        relationshipsConfig.first { it.access.targetRelationshipUid == relationshipTypeUid }
                                    )
                                    Unit
                                }
                                .orgUnitScope(ProgramCaptureScope(programUid))
                                .build()
                                .show(
                                    view.getAbstracContext().supportFragmentManager,
                                    "OrgUnitEnrollment"
                                )
                        } else if (allOrgUnits.size == 1) enrollInOrgUnit(
                            allOrgUnits[0].uid(), programUid, relationshipsConfig.first { it.access.targetRelationshipUid == relationshipTypeUid }
                        )
                    },
                    Consumer { t: Throwable? -> Timber.d(t) }
                )
        )
    }

    private fun enrollInOrgUnit(
        orgUnitUid: String,
        programUid: String,
        relationship: Relationship,
    ) {
        compositeDisposable.add(
            Single.fromCallable {
                relationshipRepository.saveToEnroll(
                    orgUnit = orgUnitUid,
                    programUid = programUid,
                    relationship = relationship
                )
            }
                .subscribeOn(schedulerProvider.computation())
                .observeOn(schedulerProvider.ui())
                .subscribe(
                    Consumer { enrollmentAndTEI ->
                        view.goToEnrollment(
                            programUid,
                            enrollmentAndTEI.second.toString(),
                        )
                        relationshipToAdd = relationship.access.targetRelationshipUid
                        programUidToCheck = programUid
                    },
                    Consumer { t: Throwable? -> Timber.d(t) })
        )
    }
}