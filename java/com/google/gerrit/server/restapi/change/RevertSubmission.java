package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.api.ApiUtil.asRestApiException;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.extensions.webui.UiAction.Description;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.ChangeUtil;
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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class RevertSubmission
    implements RestModifyView<ChangeResource, RevertInput>, UiAction<ChangeResource> {
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
      ContributorAgreementsChecker contributorAgreements) {
    this.revert = revert;
    this.queryProvider = queryProvider;
    this.changeNotesFactory = changeNotesFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.psUtil = psUtil;
    this.contributorAgreements = contributorAgreements;
  }

  @Override
  public Response<List<ChangeInfo>> apply(ChangeResource changeResource, RevertInput input)
      throws Exception {
    // TODO: all checks that are necessary, permissions etc. detailed in design doc solution,

    PatchSet patch = psUtil.get(notes, changeResource.);
    if (patch == null) {
      throw new ResourceNotFoundException(changeIdToRevert.toString());
    }

    String submissionId = changeResource.getChange().getSubmissionId();
    List<ChangeData> changeDatas = queryProvider.get().bySubmissionId(submissionId);
    List<ChangeResource> changeResources = new ArrayList<>();

    for (ChangeData changeData : changeDatas) {
      Change change = changeData.change();
      if (!change.isMerged()) {
        throw new ResourceConflictException("change is " + ChangeUtil.status(change));
      }
      contributorAgreements.check(change.getProject(), changeResource.getUser());
      permissionBackend.user(changeResource.getUser()).ref(change.getDest()).check(CREATE_CHANGE);
      projectCache.checkedGet(change.getProject()).checkStatePermitsWrite();

      changeResources.add(getChangeResource(changeData.getId()));
    }
    List<ChangeInfo> results = new ArrayList<>();
    for (ChangeResource change : changeResources) {
      //TODO: revert in the right order.
      results.add(revert.apply(change, input).value());
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
      throw asRestApiException("Cannot retrieve change", e);
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

}
