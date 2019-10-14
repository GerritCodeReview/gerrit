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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.WalkSorter;
import com.google.gerrit.server.change.WalkSorter.PatchSetData;
import com.google.gerrit.server.extensions.events.ChangeReverted;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.send.RevertedSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.change.CherryPickChange.Result;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
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
import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class RevertSubmission
    extends RetryingRestModifyView<ChangeResource, RevertInput, List<ChangeInfo>>
    implements UiAction<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Revert revert;
  private final Provider<InternalChangeQuery> queryProvider;
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
  private final CommitUtil commitUtil;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeReverted changeReverted;
  private final RevertedSender.Factory revertedSenderFactory;
  private final Sequences seq;
  private final NotifyResolver notifyResolver;

  private String nextBase;
  private List<ChangeInfo> results;

  @Inject
  RevertSubmission(
      RetryHelper retryHelper,
      Revert revert,
      Provider<InternalChangeQuery> queryProvider,
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
      ChangeMessagesUtil cmUtil,
      CommitUtil commitUtil,
      ChangeNotes.Factory changeNotesFactory,
      ChangeReverted changeReverted,
      RevertedSender.Factory revertedSenderFactory,
      Sequences seq,
      NotifyResolver notifyResolver) {
    super(retryHelper);
    this.revert = revert;
    this.queryProvider = queryProvider;
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
    this.commitUtil = commitUtil;
    this.changeNotesFactory = changeNotesFactory;
    this.changeReverted = changeReverted;
    this.revertedSenderFactory = revertedSenderFactory;
    this.seq = seq;
    this.notifyResolver = notifyResolver;
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

    for (ChangeData changeData : changeDatas) {
      Change change = changeData.change();

      // Might do the permission tests multiple times, but these are necessary to ensure that the
      // user has permissions to revert all changes. If they lack any permission, no revert will be
      // done.

      contributorAgreements.check(change.getProject(), changeResource.getUser());
      permissionBackend.currentUser().ref(change.getDest()).check(CREATE_CHANGE);
      permissionBackend.currentUser().change(changeData).check(ChangePermission.READ);
      projectCache.checkedGet(change.getProject()).checkStatePermitsWrite();

      requireNonNull(
          psUtil.get(changeData.notes(), change.currentPatchSetId()),
          String.format(
              "current patch set %s of change %s not found",
              change.currentPatchSetId(), change.currentPatchSetId()));
    }

    if (input.topic == null) {
      input.topic =
          String.format(
              "revert-%s-%s", submissionId, RandomStringUtils.randomAlphabetic(10).toUpperCase());
    }
    results = new ArrayList<>();
    nextBase = null;

    return Response.ok(revertSubmission(changeDatas, input, updateFactory));
  }

  private List<ChangeInfo> revertSubmission(
      List<ChangeData> changeData, RevertInput revertInput, BatchUpdate.Factory updateFactory)
      throws Exception {
    Multimap<Pair<NameKey, String>, ChangeData> changesPerProjectAndBranch =
        ArrayListMultimap.create();
    changeData.stream()
        .forEach(
            c ->
                changesPerProjectAndBranch.put(
                    Pair.of(c.project(), c.change().getDest().branch()), c));

    CherryPickInput cherryPickInput = new CherryPickInput();
    // We only notify of the new changes that need to be approved.
    cherryPickInput.notify = revertInput.notify;
    cherryPickInput.notifyDetails = revertInput.notifyDetails;
    cherryPickInput.parent = 1;
    cherryPickInput.keepReviewers = true;

    for (Pair<Project.NameKey, String> projectAndBranch : changesPerProjectAndBranch.keySet()) {
      Project.NameKey project = projectAndBranch.getFirst();
      cherryPickInput.destination = projectAndBranch.getSecond();
      Iterator<PatchSetData> sortedChangesInProject =
          sorter.sort(changesPerProjectAndBranch.get(projectAndBranch)).iterator();

      while (sortedChangesInProject.hasNext()) {
        ChangeNotes changeNotes =
            changeNotesFactory.createChecked(sortedChangesInProject.next().data().getId());
        cherryPickInput.base =
            cherryPickInput.base != null
                ? cherryPickInput.base
                : changeNotes.getCurrentPatchSet().commitId().getName();
        ObjectId revCommitId =
            commitUtil.createRevertCommit(revertInput.message, changeNotes, user.get());
        cherryPickInput.message =
            revertInput.message != null
                ? revertInput.message
                : MessageFormat.format(
                    ChangeMessages.get().revertChangeDefaultMessage,
                    changeNotes.getChange().getSubject(),
                    changeNotes.getCurrentPatchSet().commitId().name());
        ObjectId generatedChangeId = Change.generateChangeId();
        Change.Id cherryPickRevertChangeId = Change.id(seq.nextChangeId());
        try (BatchUpdate bu = updateFactory.create(project, user.get(), TimeUtil.nowTs())) {
          bu.setNotify(
              notifyResolver.resolve(
                  firstNonNull(cherryPickInput.notify, NotifyHandling.ALL),
                  cherryPickInput.notifyDetails));
          bu.addOp(
              changeNotes.getChange().getId(),
              new CreateCherryPickOp(
                  updateFactory,
                  revCommitId,
                  cherryPickInput,
                  revertInput.topic,
                  generatedChangeId,
                  cherryPickRevertChangeId));
          bu.addOp(changeNotes.getChange().getId(), new PostRevertedMessageOp(generatedChangeId));
          bu.addOp(
              cherryPickRevertChangeId,
              new NotifyOp(changeNotes.getChange(), cherryPickRevertChangeId));

          bu.execute();
        }
        // It should actually be an IntegrationException but compiler doesn't allow it.
        catch (Exception ex) {
          if (!ex.getMessage().contains("identical tree")) {
            // This means it's a legitimate error and we should throw it further.
            throw ex;
          }
          // If the exception was in fact because of identical trees, it means the cherry-pick
          // failed
          // and we can continue performing the reverts.
        }
        cherryPickInput.base = nextBase != null ? nextBase : cherryPickInput.base;
      }
      cherryPickInput.base = null;
    }
    return results;
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
        .setLabel(
            "Revert this change and all changes that have been submitted together with this change")
        .setTitle("Revert submission")
        .setVisible(
            and(
                change.isMerged() && projectStatePermitsWrite,
                permissionBackend
                    .user(rsrc.getUser())
                    .ref(change.getDest())
                    .testCond(CREATE_CHANGE)));
  }

  private class CreateCherryPickOp implements BatchUpdateOp {
    private final BatchUpdate.Factory updateFactory;
    private final ObjectId revCommitId;
    private final CherryPickInput cherryPickInput;
    private final String topic;
    private final ObjectId computedChangeId;
    private final Change.Id cherryPickRevertChangeId;

    CreateCherryPickOp(
        BatchUpdate.Factory updateFactory,
        ObjectId revCommitId,
        CherryPickInput cherryPickInput,
        String topic,
        ObjectId computedChangeId,
        Change.Id cherryPickRevertChangeId) {
      this.updateFactory = updateFactory;
      this.revCommitId = revCommitId;
      this.cherryPickInput = cherryPickInput;
      this.topic = topic;
      this.computedChangeId = computedChangeId;
      this.cherryPickRevertChangeId = cherryPickRevertChangeId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      Change change = ctx.getChange();
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
              computedChangeId,
              cherryPickRevertChangeId);
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
      return true;
    }
  }

  private class NotifyOp implements BatchUpdateOp {
    private final Change change;
    private final Change.Id revertChangeId;

    NotifyOp(Change change, Change.Id revertChangeId) {
      this.change = change;
      this.revertChangeId = revertChangeId;
    }

    @Override
    public void postUpdate(Context ctx) throws Exception {
      changeReverted.fire(
          change, changeNotesFactory.createChecked(revertChangeId).getChange(), ctx.getWhen());
      try {
        RevertedSender cm = revertedSenderFactory.create(ctx.getProject(), change.getId());
        cm.setFrom(ctx.getAccountId());
        cm.setNotify(ctx.getNotify(change.getId()));
        cm.send();
      } catch (Exception err) {
        logger.atSevere().withCause(err).log(
            "Cannot send email for revert change %s", change.getId());
      }
    }
  }

  /**
   * create a message that describes the revert if the cherry-pick is successful, and point the
   * revert of the change towards the cherry-pick. The cherry-pick is the updated change that acts
   * as "revert-of" the original change.
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
