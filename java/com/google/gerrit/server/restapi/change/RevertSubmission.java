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
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.BranchNameKey;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ChangeMessagesUtil;
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
import com.google.gerrit.server.submit.IntegrationException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.vladsch.flexmark.util.Pair;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
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
  private final WalkSorter sorter;
  private final ChangeMessagesUtil cmUtil;
  private String nextBase;
  private List<ChangeInfo> results;

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
      WalkSorter sorter,
      ChangeMessagesUtil cmUtil) {
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
    this.sorter = sorter;
    this.cmUtil = cmUtil;
    results = new ArrayList<>();
    nextBase = null;
  }

  @Override
  public Response<List<ChangeInfo>> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource changeResource, RevertInput input)
      throws Exception {

    if (!changeResource.getChange().isMerged()) {
      throw new ResourceConflictException(
          String.format("change is %s.", ChangeUtil.status(changeResource.getChange())));
    }

    String submissionId =
        requireNonNull(
            changeResource.getChange().getSubmissionId(),
            String.format("merged change %s has no submission ID", changeResource.getId()));

    List<ChangeData> changeDatas = queryProvider.get().bySubmissionId(submissionId);

    Multimap<Pair<NameKey, String>, ChangeData> changesPerProjectAndBranch =
        ArrayListMultimap.create();
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
      String branchName = changeData.change().getDest().branch();
      changesPerProjectAndBranch.put(Pair.of(nameKey, branchName), changeData);
    }
    CherryPickInput cherryPickInput = new CherryPickInput();

    // We only notify of the new changes that need to be approved.
    cherryPickInput.notify = input.notify;
    cherryPickInput.notifyDetails = input.notifyDetails;

    cherryPickInput.parent = 1;
    cherryPickInput.keepReviewers = true;
    return Response.ok(
        revertSubmissionCreate(
            changesPerProjectAndBranch,
            cherryPickInput,
            input.message,
            submissionId,
            updateFactory));
  }

  private List<ChangeInfo> revertSubmissionCreate(
      Multimap<Pair<Project.NameKey, String>, ChangeData> changesPerProjectAndBranch,
      CherryPickInput cherryPickInput,
      String commitMessage,
      String submissionId,
      BatchUpdate.Factory updateFactory)
      throws Exception {
    results = new ArrayList<>();
    String topic = String.format("revert-%s-%s", submissionId, TimeUtil.nowTs());
    for (Pair<Project.NameKey, String> projectAndBranch : changesPerProjectAndBranch.keySet()) {
      Project.NameKey project = projectAndBranch.getFirst();
      cherryPickInput.destination = projectAndBranch.getSecond();
      try (Repository repo = repoManager.openRepository(project)) {
        cherryPickInput.base =
            repo.getRefDatabase().findRef(cherryPickInput.destination).getObjectId().getName();
      }
      List<ChangeData> changeDataList =
          changesPerProjectAndBranch.get(projectAndBranch).stream().collect(Collectors.toList());
      Iterator<PatchSetData> sortedChangesInProject = sorter.sort(changeDataList).iterator();

      while (sortedChangesInProject.hasNext()) {
        ChangeResource change = getChangeResource(sortedChangesInProject.next().data().getId());
        ObjectId revCommitId = revert.createCommit(commitMessage, change.getNotes(), user.get());
        cherryPickInput.message =
            MessageFormat.format(
                ChangeMessages.get().revertChangeDefaultMessage,
                change.getChange().getSubject(),
                change.getNotes().getCurrentPatchSet().commitId().name());
        ObjectId generatedChangeId = Change.generateChangeId();
        try (BatchUpdate bu = updateFactory.create(project, user.get(), TimeUtil.nowTs())) {
          bu.addOp(
              change.getId(),
              new CreateCherryPickOp(
                  updateFactory, revCommitId, cherryPickInput, topic, generatedChangeId));
          bu.addOp(change.getId(), new PostRevertedMessageOp(generatedChangeId.toObjectId()));
          bu.execute();
        }
        cherryPickInput.base = nextBase != null ? nextBase : cherryPickInput.base;
      }
    }
    return results;
  }

  private ChangeResource getChangeResource(Change.Id changeId) {
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

  /**
   * create a message for revert for the change after cherry-pick (or to the original revert, if the
   * cherry pick failed).
   */
  private class CreateCherryPickOp implements BatchUpdateOp {
    private final BatchUpdate.Factory updateFactory;
    private final ObjectId revCommitId;
    private final CherryPickInput cherryPickInput;
    private final String topic;
    private final ObjectId computedChangeId;

    CreateCherryPickOp(
        BatchUpdate.Factory updateFactory,
        ObjectId revCommitId,
        CherryPickInput cherryPickInput,
        String topic,
        ObjectId computedChangeId) {
      this.updateFactory = updateFactory;
      this.revCommitId = revCommitId;
      this.cherryPickInput = cherryPickInput;
      this.topic = topic;
      this.computedChangeId = computedChangeId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      Change change = ctx.getChange();
      try {
        Result cherryPickResult =
            cherryPickChange.cherryPick(
                updateFactory,
                change,
                change.getProject(),
                revCommitId,
                cherryPickInput,
                BranchNameKey.create(
                    change.getProject(), RefNames.fullName(cherryPickInput.destination)),
                topic,
                change.getId(),
                computedChangeId);
        // save base for next cherryPick of that branch
        nextBase =
            changeNotesFactory
                .createChecked(cherryPickResult.changeId())
                .getCurrentPatchSet()
                .commitId()
                .getName();

        results.add(
            json.noOptions()
                .format(change.getProject(), cherryPickResult.changeId(), ChangeInfo::new));

      } catch (IntegrationException ex) {
        if (ex.getMessage().contains("identical tree")) {
          // This means the cherry-pick failed since it is unnecessary. No need to update
          // destinationToBase for next cherry-picks, and we can ignore this revert.
          // No need to add message because no revert should be created for empty changes.
          return false;
        } else {
          throw ex;
        }
      }
      return true;
    }
  }
  /**
   * create a message for revert for the change after cherry-pick (or to the original revert, if the
   * cherry pick failed).
   */
  private class PostRevertedMessageOp implements BatchUpdateOp {
    private final ObjectId computedChangeId;

    PostRevertedMessageOp(ObjectId computedChangeId) {
      this.computedChangeId = computedChangeId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      Change change = ctx.getChange();
      PatchSet.Id patchSetId = change.currentPatchSetId();
      ChangeMessage changeMessage =
          ChangeMessagesUtil.newMessage(
              ctx,
              "Created a revert of this change as I" + computedChangeId.getName(),
              ChangeMessagesUtil.TAG_REVERT);
      cmUtil.addChangeMessage(ctx.getUpdate(patchSetId), changeMessage);
      return true;
    }
  }
}
