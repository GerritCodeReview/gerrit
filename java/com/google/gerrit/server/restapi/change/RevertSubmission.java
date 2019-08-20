// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.BranchNameKey;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.WalkSorter;
import com.google.gerrit.server.change.WalkSorter.PatchSetData;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.change.CherryPickChange.Result;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class RevertSubmission
    extends RetryingRestModifyView<ChangeResource, RevertInput, List<ChangeInfo>>
    implements UiAction<ChangeResource> {
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
  private final CherryPickChange cherryPickChange;
  private final ChangeJson.Factory json;
  private final GitRepositoryManager repoManager;
  private final DeleteChange deleteChange;
  private final WalkSorter sorter;

  @Inject
  RevertSubmission(
      RetryHelper retryHelper,
      Revert revert,
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory changeNotesFactory,
      ChangeResource.Factory changeResourceFactory,
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      PatchSetUtil psUtil,
      ContributorAgreementsChecker contributorAgreements,
      CherryPickChange cherryPickChange,
      ChangeJson.Factory json,
      GitRepositoryManager repoManager,
      DeleteChange deleteChange,
      WalkSorter sorter) {
    super(retryHelper);
    this.revert = revert;
    this.queryProvider = queryProvider;
    this.changeNotesFactory = changeNotesFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.psUtil = psUtil;
    this.contributorAgreements = contributorAgreements;
    this.cherryPickChange = cherryPickChange;
    this.json = json;
    this.repoManager = repoManager;
    this.deleteChange = deleteChange;
    this.sorter = sorter;
  }

  @Override
  public Response<List<ChangeInfo>> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource changeResource, RevertInput input)
      throws Exception {

    if (!changeResource.getChange().isMerged()) {
      throw new ResourceConflictException(
          String.format(
              "change is %s: %s",
              ChangeUtil.status(changeResource.getChange()), changeResource.getId()));
    }

    String submissionId =
        requireNonNull(
            changeResource.getChange().getSubmissionId(),
            String.format("merged change %s has no submission ID", changeResource.getId()));

    List<ChangeData> changeDatas = queryProvider.get().bySubmissionId(submissionId);

    Multimap<Project.NameKey, ChangeData> changesPerProject = ArrayListMultimap.create();

    for (ChangeData changeData : changeDatas) {
      Change change = changeData.change();
      contributorAgreements.check(change.getProject(), changeResource.getUser());
      permissionBackend.currentUser().ref(change.getDest()).check(CREATE_CHANGE);
      permissionBackend.currentUser().change(changeData).check(ChangePermission.READ);
      projectCache.checkedGet(change.getProject()).checkStatePermitsWrite();

      requireNonNull(
          psUtil.get(changeData.notes(), change.currentPatchSetId()),
          String.format(
              "current patch set %s of change %s not found",
              change.currentPatchSetId(), change.currentPatchSetId()));
      Project.NameKey nameKey = changeData.change().getProject();
      changesPerProject.put(nameKey, changeData);
    }
    String topic = "revert-" + submissionId;
    CherryPickInput cherryPickInput = new CherryPickInput();

    // We only notify of the new changes that need to be approved, not the temporary reverts that
    // are deleted.
    cherryPickInput.notify = input.notify;
    cherryPickInput.notifyDetails = input.notifyDetails;
    input.notify = NotifyHandling.NONE;
    input.notifyDetails = null;

    cherryPickInput.parent = 1;
    cherryPickInput.keepReviewers = true;
    cherryPickInput.allowConflicts = true;
    return Response.ok(
        revertSubmissionCreate(changesPerProject, cherryPickInput, input, topic, updateFactory));
  }

  private List<ChangeInfo> revertSubmissionCreate(
      Multimap<Project.NameKey, ChangeData> changesPerProject,
      CherryPickInput cherryPickInput,
      RevertInput revertInput,
      String topic,
      BatchUpdate.Factory updateFactory)
      throws Exception {
    List<ChangeInfo> results = new ArrayList<>();

    for (Project.NameKey project : changesPerProject.keySet()) {
      List<ChangeData> changeDataList =
          changesPerProject.get(project).stream().collect(Collectors.toList());
      Collections.reverse(changeDataList);
      Iterator<PatchSetData> sortedChangesInProject = sorter.sort(changeDataList).iterator();
      Repository repo = repoManager.openRepository(project);
      Map<String, String> destinationToBase = new HashMap<>();

      while (sortedChangesInProject.hasNext()) {
        ChangeResource change = getChangeResource(sortedChangesInProject.next().data().getId());
        ChangeInfo changeInfo = revert.apply(change, revertInput).value();
        ChangeResource revertedChange = getChangeResource(Change.id(changeInfo._number));

        cherryPickInput.destination = revertedChange.getChange().getDest().branch();
        if (destinationToBase.containsKey(cherryPickInput.destination)) {
          cherryPickInput.base = destinationToBase.get(cherryPickInput.destination);
        } else {
          cherryPickInput.base =
              repo.getRefDatabase().findRef(cherryPickInput.destination).getObjectId().getName();
        }
        cherryPickInput.message =
            MessageFormat.format(
                ChangeMessages.get().revertChangeDefaultMessage,
                change.getChange().getSubject(),
                change.getNotes().getCurrentPatchSet().commitId().name());
        Result cherryPickResult =
            cherryPickChange.cherryPick(
                updateFactory,
                revertedChange.getChange(),
                revertedChange.getNotes().getCurrentPatchSet(),
                cherryPickInput,
                BranchNameKey.create(project, RefNames.fullName(cherryPickInput.destination)),
                topic,
                change.getId(),
                true);

        // save base for next cherryPick of that branch
        destinationToBase.put(
            cherryPickInput.destination,
            changeNotesFactory
                .createChecked(cherryPickResult.changeId())
                .getCurrentPatchSet()
                .commitId()
                .getName());

        // delete unnecessary reverted change, keep only the cherry-picked change.
        deleteChange.apply(revertedChange, new Input());

        results.add(json.noOptions().format(project, cherryPickResult.changeId(), ChangeInfo::new));
      }
    }
    return results;
  }

  private ChangeResource getChangeResource(Change.Id changeId) throws RestApiException {
    ChangeNotes notes = changeNotesFactory.createChecked(changeId);
    return changeResourceFactory.create(notes, user.get());
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
        .setTitle("Revert submission")
        .setVisible(
            and(
                change.isMerged() && projectStatePermitsWrite,
                permissionBackend
                    .user(rsrc.getUser())
                    .ref(change.getDest())
                    .testCond(CREATE_CHANGE)));
  }
}
