package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.api.changes.RevertSubmissionInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.change.RelatedChangesSorter.PatchSetData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RevertSubmission
    implements RestModifyView<ChangeResource, RevertSubmissionInput>, UiAction<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Revert revert;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final PatchSetUtil psUtil;
  private final ContributorAgreementsChecker contributorAgreements;
  private final RelatedChangesSorter relatedChangesSorter;

  @Inject
  RevertSubmission(
      Revert revert,
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory changeNotesFactory,
      ChangeResource.Factory changeResourceFactory,
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      PatchSetUtil psUtil,
      ContributorAgreementsChecker contributorAgreements,
      RelatedChangesSorter relatedChangesSorter) {
    this.revert = revert;
    this.queryProvider = queryProvider;
    this.changeNotesFactory = changeNotesFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.psUtil = psUtil;
    this.contributorAgreements = contributorAgreements;
    this.relatedChangesSorter = relatedChangesSorter;
  }

  @Override
  public Response<List<ChangeInfo>> apply(
      ChangeResource changeResource, RevertSubmissionInput input) throws Exception {
    String submissionId = changeResource.getChange().getSubmissionId();
    if (submissionId == null) {
      throw new ResourceConflictException(
          String.format(
              "submissionId doesn't exist for changeId %s, so the change was not submitted",
              changeResource.getId()));
    }
    List<ChangeData> changeDatas = queryProvider.get().bySubmissionId(submissionId);

    Map<NameKey, List<ChangeData>> changesPerProject = new HashMap<>();
    RevertInput revertInput = createRevertInput(input);

    for (ChangeData changeData : changeDatas) {
      Change change = changeData.change();
      contributorAgreements.check(change.getProject(), changeResource.getUser());
      permissionBackend.user(changeResource.getUser()).ref(change.getDest()).check(CREATE_CHANGE);
      projectCache.checkedGet(change.getProject()).checkStatePermitsWrite();

      PatchSet patch = psUtil.get(changeData.notes(), change.currentPatchSetId());
      if (patch == null) {
        throw new ResourceNotFoundException(changeData.getId().toString());
      }
      Project.NameKey nameKey = changeData.change().getProject();
      if (changesPerProject.containsKey(nameKey)) {
        changesPerProject.get(nameKey).add(changeData);
      } else {
        List<ChangeData> changeDataList = new ArrayList<>();
        changeDataList.add(changeData);
        changesPerProject.put(nameKey, changeDataList);
      }
    }
    if (revertInput.topicOfRevert == null) {
      revertInput.topicOfRevert = "revert-" + submissionId;
    }

    List<ChangeInfo> results = new ArrayList<>();

    for (Project.NameKey project : changesPerProject.keySet()) {
      List<ChangeData> changeDataList = changesPerProject.get(project);
      PatchSet startPatchSet =
          psUtil.get(
              changeDataList.get(0).notes(), changeDataList.get(0).change().currentPatchSetId());
      List<PatchSetData> sortedChangesInProject =
          relatedChangesSorter.sort(changeDataList, startPatchSet);

      for (int i = 0; i < sortedChangesInProject.size(); i++) {
        ChangeResource change = getChangeResource(sortedChangesInProject.get(i).data().getId());
        ChangeInfo changeInfo = revert.apply(change, revertInput).value();
        revertInput.parentOfRevert =
            getChangeResource(Change.id(changeInfo._number))
                .getNotes()
                .getCurrentPatchSet()
                .commitId()
                .getName();
        results.add(changeInfo);
      }
      revertInput.parentOfRevert = null;
    }
    return Response.ok(results);
  }

  private ChangeResource getChangeResource(Change.Id changeId) throws RestApiException {
    try {
      ChangeNotes notes = changeNotesFactory.createChecked(changeId);
      return changeResourceFactory.create(notes, user.get());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(String.format("Change %d not found", changeId.get()), e);
    } catch (Exception e) {
      throw new BadRequestException("Cannot retrieve change", e);
    }
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    boolean projectStatePermitsWrite = false;
    try {
      projectStatePermitsWrite = projectCache.checkedGet(rsrc.getProject()).statePermitsWrite();
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Failed to check if project state permits write: %s", rsrc.getProject());
    }
    return new UiAction.Description()
        .setLabel("Revert all changes of the submission id of this change")
        .setTitle("Revert by submission")
        .setVisible(
            and(
                change.isMerged() && projectStatePermitsWrite,
                permissionBackend
                    .user(rsrc.getUser())
                    .ref(change.getDest())
                    .testCond(CREATE_CHANGE)));
  }

  private RevertInput createRevertInput(RevertSubmissionInput revertSubmissionInput) {
    RevertInput revertInput = new RevertInput();
    revertInput.message = revertSubmissionInput.message;
    revertInput.notify = revertSubmissionInput.notify;
    revertInput.notifyDetails = revertSubmissionInput.notifyDetails;
    revertInput.topicOfRevert = revertSubmissionInput.topicOfReverts;
    return revertInput;
  }
}
