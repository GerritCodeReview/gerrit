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
import com.google.gerrit.extensions.common.RevertSubmissionInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
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
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.change.CherryPickChange.Result;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.vladsch.flexmark.util.Pair;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class RevertSubmission
    implements RestModifyView<ChangeResource, RevertInput>, UiAction<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<InternalChangeQuery> queryProvider;
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
  private final ChangeResource.Factory changeResourceFactory;
  private final ChangeReverted changeReverted;
  private final RevertedSender.Factory revertedSenderFactory;
  private final Sequences seq;
  private final NotifyResolver notifyResolver;
  private final Revert revert;
  private final BatchUpdate.Factory updateFactory;

  private String nextBase;
  private List<ChangeInfo> results;

  @Inject
  RevertSubmission(
      Provider<InternalChangeQuery> queryProvider,
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
      ChangeResource.Factory changeResourceFactory,
      ChangeReverted changeReverted,
      RevertedSender.Factory revertedSenderFactory,
      Sequences seq,
      NotifyResolver notifyResolver,
      Revert revert,
      BatchUpdate.Factory updateFactory) {
    this.queryProvider = queryProvider;
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
    this.changeResourceFactory = changeResourceFactory;
    this.changeReverted = changeReverted;
    this.revertedSenderFactory = revertedSenderFactory;
    this.seq = seq;
    this.notifyResolver = notifyResolver;
    this.revert = revert;
    this.updateFactory = updateFactory;
  }

  @Override
  public Response<RevertSubmissionInfo> apply(ChangeResource changeResource, RevertInput input)
      throws RestApiException, IOException, UpdateException, PermissionBackendException,
          NoSuchProjectException, ConfigInvalidException {

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

    return Response.ok(revertSubmission(updateFactory, changeDatas, input));
  }

  private RevertSubmissionInfo revertSubmission(
      BatchUpdate.Factory updateFactory, List<ChangeData> changeData, RevertInput revertInput)
      throws RestApiException, IOException, UpdateException, PermissionBackendException,
          NoSuchProjectException, ConfigInvalidException {
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
      List<ChangeData> changesInProjectAndBranch =
          changesPerProjectAndBranch.get(projectAndBranch).stream().collect(Collectors.toList());

      // Sort the changes topologically.
      Iterator<PatchSetData> sortedChangesInProject =
          sorter.sort(changesInProjectAndBranch).iterator();

      Set<ObjectId> commitIdsInProjectAndBranch =
          changesInProjectAndBranch.stream()
              .map(c -> c.currentPatchSet().commitId())
              .collect(Collectors.toSet());

      while (sortedChangesInProject.hasNext()) {
        ChangeNotes changeNotes =
            changeNotesFactory.createChecked(sortedChangesInProject.next().data().getId());
        if (cherryPickInput.base == null) {
          cherryPickInput.base = getBase(changeNotes, commitIdsInProjectAndBranch);
        }

        // This is the code in case this is the first revert of this project + branch, and the
        // revert
        // would be on top of the change being reverted.
        if (cherryPickInput.base.equals(changeNotes.getCurrentPatchSet().commitId().getName())) {
          ChangeInfo revertChangeInfo =
              revert
                  .apply(changeResourceFactory.create(changeNotes, user.get()), revertInput)
                  .value();
          results.add(revertChangeInfo);
          cherryPickInput.base =
              changeNotesFactory
                  .createChecked(Change.id(revertChangeInfo._number))
                  .getCurrentPatchSet()
                  .commitId()
                  .getName();
        } else {
          // This is the code in case this is the second revert (or more) of this project + branch.
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
            // failed and we can continue performing the reverts.
          }
          if (nextBase != null) {
            cherryPickInput.base = nextBase;
            nextBase = null;
          }
        }
      }
      cherryPickInput.base = null;
    }
    RevertSubmissionInfo revertSubmissionInfo = new RevertSubmissionInfo();
    revertSubmissionInfo.revertChanges = results;
    return revertSubmissionInfo;
  }

  /**
   * This function finds the base that the first revert of a project + branch should be rebased on.
   * It searches using BFS for the first commit that is either: 1. Has 2 or more parents, and has as
   * parents at least one commit that is part of the submission. 2. A commit that is part of the
   * submission. If neither of those are true, it just continues the search by going to the parents.
   *
   * <p>If 1 is true, if all the parents are part of the submission, it just returns that commit. If
   * only some of them are in the submission, the function changes and starts checking only the
   * commits that are not part of the submission. If all of them are part of the submission (or they
   * are also merge commits that have as parents only other merge commits, or other changes that are
   * part of the submission), we will return possibleMergeCommitToReturn which is the original
   * commit we started with when 1 was determined to be true.
   *
   * <p>If 2 is true, it will return the commit that WalkSorter has decided that it should be the
   * first commit reverted (e.g changeNotes, which is also the commit that is the first in the
   * topological sorting). Unless possibleMergeCommitToReturn is not null, which means we already
   * encountered a merge commit with a part of the submission earlier, which means we should return
   * that merge commit.
   *
   * <p>It doesn't run through the entire graph since it will stop once it finds at least one commit
   * that is part of the submission.
   *
   * @param changeNotes changeNotes for the change that is found by WalkSorter to be the first one
   *     that should be reverted, the first in the topological sorting.
   * @param commitIds The commitIds of this project and branch.
   * @return the base of the first revert.
   */
  private String getBase(ChangeNotes changeNotes, Set<ObjectId> commitIds)
      throws ResourceConflictException, IOException {
    try (Repository git = repoManager.openRepository(changeNotes.getProjectName());
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk revWalk = new RevWalk(reader)) {

      ObjectId mainCommit =
          git.getRefDatabase().findRef(changeNotes.getChange().getDest().branch()).getObjectId();
      Queue<ObjectId> commitsToSearch = new LinkedList<>();
      commitsToSearch.add(mainCommit);

      while (!commitsToSearch.isEmpty()) {

        RevCommit revCommit = revWalk.parseCommit(commitsToSearch.poll());
        if (commitIds.contains(revCommit.getId())) {
          return changeNotes.getCurrentPatchSet().commitId().getName();
        }
        if (revCommit.getParentCount() > 1) {
          List<RevCommit> parents = Arrays.asList(revCommit.getParents());
          List<RevCommit> parentsOfSubmission =
              parents.stream()
                  .filter(parent -> commitIds.contains(parent.getId()))
                  .collect(Collectors.toList());
          if (parentsOfSubmission.size() > 1) {
            // Found a merge commit that has multiple parents of the submission.
            return revCommit.getName();
          }
          if (!parentsOfSubmission.isEmpty()) {
            // Found a merge commit that has only one parent of this submission, but also other
            // parents not from the submission. Now we need to check if the others are merge commits
            // that only have as parents only other merge commits, or other changes from the
            // submission.
            commitsToSearch.clear();
            commitsToSearch.addAll(
                parents.stream()
                    .filter(parent -> !commitIds.contains(parent.getId()))
                    .collect(Collectors.toList()));

            if (isMergeCommitAncestorOfSubmission(commitsToSearch, commitIds, revWalk)) {
              // Found a second commit of that submission that share the same merge commit.
              return revCommit.getName();
            }
            // Couldn't find a second commit of that submission that share the same merge commit.
            return changeNotes.getCurrentPatchSet().commitId().getName();
          }
        }
        Arrays.asList(revCommit.getParents()).forEach(parent -> commitsToSearch.add(parent));
      }
      // This should never happen since it can only happen if we go through the entire repository
      // without finding a single commit that matches any commit from the submission.
      throw new ResourceConflictException(
          String.format("Couldn't find Change-Id %s in the repository", changeNotes.getChangeId()));
    }
  }

  /**
   * This helper function calculates whether or not there exists a second commit that is part of the
   * submission with the same merge commit as an ancestor.
   *
   * @param commitsToSearch Starts as all the parents of the potential merge commit, except the one
   *     that is part of the submission.
   * @param commitIds The commitIds of this project and branch.
   * @param revWalk Used for walking through the revisions.
   * @return True if found another commit of this submission, false if not found.
   */
  private Boolean isMergeCommitAncestorOfSubmission(
      Queue<ObjectId> commitsToSearch, Set<ObjectId> commitIds, RevWalk revWalk)
      throws ResourceConflictException, IOException {
    while (!commitsToSearch.isEmpty()) {
      RevCommit revCommit = revWalk.parseCommit(commitsToSearch.poll());
      if (revCommit.getParentCount() > 1) {
        List<RevCommit> parents = Arrays.asList(revCommit.getParents());
        if (parents.stream().anyMatch(p -> commitIds.contains(p.getId()))) {
          // found another commit with a common ancestor.
          return true;
        }
        Arrays.asList(revCommit.getParents()).forEach(parent -> commitsToSearch.add(parent));
      } else {
        // We found a merge commit, we found that one of the parents is not a merge commit nor a
        // change of this submission. Therefore the merge commit is not useful, and we just
        // rebase on the most recent revert as usual.
        return false;
      }
    }
    // This should never happen since it can only happen if we go through the entire repository
    // encountering only merge commits after encountering the change whose submission we are
    // reverting.
    throw new ResourceConflictException(
        "Couldn't find a non-merge commit after older than the change");
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
        .setLabel("Revert submission")
        .setTitle(
            "Revert this change and all changes that have been submitted together with this change")
        .setVisible(
            and(
                change.isMerged() && change.getSubmissionId() != null && projectStatePermitsWrite,
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
