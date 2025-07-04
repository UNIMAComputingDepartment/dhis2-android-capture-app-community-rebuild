package org.dhis2.usescases.teiDashboard.teiProgramList;


import android.os.Build;
import android.widget.DatePicker;

import org.dhis2.R;
import org.dhis2.commons.dialogs.calendarpicker.CalendarPicker;
import org.dhis2.commons.dialogs.calendarpicker.OnDatePickerListener;
import org.dhis2.commons.orgunitselector.OUTreeFragment;
import org.dhis2.commons.orgunitselector.OrgUnitSelectorScope;
import org.dhis2.community.workflow.WorkflowRepository;
import org.dhis2.data.service.SyncStatusController;
import org.dhis2.usescases.main.program.ProgramDownloadState;
import org.dhis2.usescases.main.program.ProgramUiModel;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;
import org.hisp.dhis.android.core.program.Program;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import kotlin.Unit;
import timber.log.Timber;

/**
 * QUADRAM. Created by Cristian on 06/03/2018.
 */

public class TeiProgramListInteractor implements TeiProgramListContract.Interactor {

    private TeiProgramListContract.View view;
    private String trackedEntityId;
    private CompositeDisposable compositeDisposable;
    private final TeiProgramListRepository teiProgramListRepository;
    private Date selectedEnrollmentDate;
    private PublishProcessor<Unit> refreshData = PublishProcessor.create();
    private SyncStatusController syncStatusController;
    private WorkflowRepository workflowRepository;

    TeiProgramListInteractor(
            TeiProgramListRepository teiProgramListRepository,
            SyncStatusController syncStatusController, WorkflowRepository workflowRepository
    ) {
        this.teiProgramListRepository = teiProgramListRepository;
        this.syncStatusController = syncStatusController;
        this.workflowRepository = workflowRepository;
    }

    @Override
    public void init(TeiProgramListContract.View view, String trackedEntityId) {
        this.view = view;
        this.trackedEntityId = trackedEntityId;
        compositeDisposable = new CompositeDisposable();

        getActiveEnrollments();
        getOtherEnrollments();
        getPrograms();
    }

    private void showCustomCalendar(String programUid, String uid, OUTreeFragment orgUnitDialog) {
        CalendarPicker dialog = new CalendarPicker(view.getContext());

        Program selectedProgram = getProgramFromUid(programUid);
        if (selectedProgram != null && !selectedProgram.selectEnrollmentDatesInFuture()) {
            dialog.setMaxDate(new Date(System.currentTimeMillis()));
        }

        if (selectedProgram != null) {
            dialog.setTitle(selectedProgram.enrollmentDateLabel());
        }

        dialog.setListener(new OnDatePickerListener() {
            @Override
            public void onNegativeClick() {
                dialog.dismiss();
            }

            @Override
            public void onPositiveClick(@NotNull DatePicker datePicker) {
                Calendar selectedCalendar = Calendar.getInstance();
                selectedCalendar.set(Calendar.YEAR, datePicker.getYear());
                selectedCalendar.set(Calendar.MONTH, datePicker.getMonth());
                selectedCalendar.set(Calendar.DAY_OF_MONTH, datePicker.getDayOfMonth());
                selectedCalendar.set(Calendar.HOUR_OF_DAY, 0);
                selectedCalendar.set(Calendar.MINUTE, 0);
                selectedCalendar.set(Calendar.SECOND, 0);
                selectedCalendar.set(Calendar.MILLISECOND, 0);
                selectedEnrollmentDate = selectedCalendar.getTime();

                compositeDisposable.add(getOrgUnits(programUid)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(allOrgUnits -> {
                                    List<OrganisationUnit> orgUnits = filterOrgUnits(allOrgUnits);
                                    handleCalendarResult(orgUnitDialog, orgUnits, programUid, uid);
                                },
                                Timber::d
                        ));
            }
        });

        dialog.show();
    }

    private List<OrganisationUnit> filterOrgUnits(List<OrganisationUnit> allOrgUnits) {
        ArrayList<OrganisationUnit> orgUnits = new ArrayList<>();
        for (OrganisationUnit orgUnit : allOrgUnits) {
            boolean afterOpening = false;
            boolean beforeClosing = false;
            if (orgUnit.openingDate() == null || !selectedEnrollmentDate.before(orgUnit.openingDate()))
                afterOpening = true;
            if (orgUnit.closedDate() == null || !selectedEnrollmentDate.after(orgUnit.closedDate()))
                beforeClosing = true;
            if (afterOpening && beforeClosing)
                orgUnits.add(orgUnit);
        }
        return orgUnits;
    }

    private void handleCalendarResult(
            OUTreeFragment orgUnitDialog,
            List<OrganisationUnit> orgUnits,
            String programUid,
            String uid) {
        if (orgUnits.size() > 1) {
            orgUnitDialog.show(view.getAbstracContext().getSupportFragmentManager(), "OrgUnitEnrollment");
        } else if (!orgUnits.isEmpty()) {
            enrollInOrgUnit(orgUnits.get(0).uid(), programUid, uid, selectedEnrollmentDate);
        } else {
            view.displayMessage(view.getContext().getString(R.string.no_org_units));
        }
    }

    @Override
    public void enroll(String programUid, String uid) {
        selectedEnrollmentDate = Calendar.getInstance().getTime();
        OUTreeFragment orgUnitDialog = new OUTreeFragment.Builder()
                .singleSelection()
                .onSelection(selectedOrgUnits -> {
                    if (!selectedOrgUnits.isEmpty())
                        enrollInOrgUnit(selectedOrgUnits.get(0).uid(), programUid, uid, selectedEnrollmentDate);
                    return Unit.INSTANCE;
                })
                .orgUnitScope(new OrgUnitSelectorScope.ProgramCaptureScope(programUid))
                .build();

        showCustomCalendar(programUid, uid, orgUnitDialog);
    }

    @Override
    public Program getProgramFromUid(String programUid) {
        return teiProgramListRepository.getProgram(programUid);
    }

    private void enrollInOrgUnit(String orgUnitUid, String programUid, String teiUid, Date enrollmentDate) {
        compositeDisposable.add(
                teiProgramListRepository.saveToEnroll(orgUnitUid, programUid, teiUid, enrollmentDate)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(enrollmentUid -> view.goToEnrollmentScreen(enrollmentUid, programUid),
                                Timber::d)
        );
    }

    public Observable<List<OrganisationUnit>> getOrgUnits(String programUid) {
        return teiProgramListRepository.getOrgUnits(programUid);
    }

    private void getActiveEnrollments() {
        compositeDisposable.add(teiProgramListRepository.activeEnrollments(trackedEntityId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(enrollments -> {
                            Collections.sort(enrollments, (enrollment1, enrollment2) -> enrollment1.programName().compareToIgnoreCase(enrollment2.programName()));
                            view.setActiveEnrollments(enrollments);
                        },
                        Timber::d)
        );
    }

    private void getOtherEnrollments() {
        compositeDisposable.add(teiProgramListRepository.otherEnrollments(trackedEntityId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(enrollments -> {
                            Collections.sort(enrollments, (enrollment1, enrollment2) -> enrollment1.programName().compareToIgnoreCase(enrollment2.programName()));
                            view.setOtherEnrollments(enrollments);
                        },
                        Timber::d)
        );
    }

    private void getPrograms() {
        compositeDisposable.add(
                refreshData.startWith(Unit.INSTANCE)
                        .flatMap(unit -> teiProgramListRepository.allPrograms(trackedEntityId))
                        .map(programViewModels -> {
                            List<ProgramUiModel> programModels = new ArrayList<>();
                            for (ProgramUiModel programModel : programViewModels) {
                                programModels.add(
                                        teiProgramListRepository.updateProgramViewModel(
                                                programModel,
                                                getSyncState(programModel)
                                        )
                                );
                            }
                            return programModels;
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                this::getAlreadyEnrolledPrograms,
                                Timber::d)
        );
    }

    private ProgramDownloadState getSyncState(ProgramUiModel programUiModel) {
        ProgramDownloadState programDownloadState;
        if (syncStatusController.observeDownloadProcess().getValue().isProgramDownloading(
                programUiModel.getUid()
        )) {
            programDownloadState = ProgramDownloadState.DOWNLOADING;
        } else if (syncStatusController.observeDownloadProcess().getValue().isProgramDownloaded(
                programUiModel.getUid()
        )) {
            programDownloadState = ProgramDownloadState.DOWNLOADED;
        } else if (programUiModel.getDownloadState() == ProgramDownloadState.ERROR) {
            programDownloadState = ProgramDownloadState.ERROR;
        } else {
            programDownloadState = ProgramDownloadState.NONE;
        }
        return programDownloadState;
    }

    private void getAlreadyEnrolledPrograms(List<ProgramUiModel> programs) {
        compositeDisposable.add(teiProgramListRepository.alreadyEnrolledPrograms(trackedEntityId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        alreadyEnrolledPrograms -> deleteRepeatedPrograms(programs, alreadyEnrolledPrograms),
                        Timber::d)
        );
    }

    private void deleteRepeatedPrograms(List<ProgramUiModel> allPrograms, List<Program> alreadyEnrolledPrograms) {
        ArrayList<ProgramUiModel> programListToPrint = new ArrayList<>();
        for (ProgramUiModel programUiModel : allPrograms) {
            boolean isAlreadyEnrolled = false;
            boolean onlyEnrollOnce = false;
            for (Program program : alreadyEnrolledPrograms) {
                if (programUiModel.getUid().equals(program.uid())) {
                    isAlreadyEnrolled = true;
                    onlyEnrollOnce = program.onlyEnrollOnce();
                }
            }
            if (!isAlreadyEnrolled || !onlyEnrollOnce) {
                programListToPrint.add(programUiModel);
            }
        }
        Collections.sort(programListToPrint, (program1, program2) -> program1.getTitle().compareToIgnoreCase(program2.getTitle()));
        compositeDisposable.add(
                Single.fromCallable(() -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                List<String> uids = programListToPrint.stream().map(ProgramUiModel::getUid).toList();
                                return workflowRepository.enrollAblePrograms(uids, trackedEntityId);
                            }
                            return null;
                        }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(programUid ->{

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                view.setPrograms(programListToPrint.stream().filter(e -> programUid.contains(e.getUid())).toList());
                            }
                        },Timber ::d)
        );
    }

    @Override
    public String getProgramColor(String uid) {
        return teiProgramListRepository.getProgramColor(uid);
    }

    @Override
    public void onDettach() {
        compositeDisposable.clear();
    }

    @Override
    public void refreshData() {
        refreshData.onNext(Unit.INSTANCE);
    }
}
