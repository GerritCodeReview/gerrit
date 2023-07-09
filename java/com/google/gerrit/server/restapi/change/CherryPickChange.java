// Copyright (C) 2012 The Android Open Source Project
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
import static com.google.gerrit.server.project.ProjectCache.noSuchProject;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.change.ResetCherryPickOp;
import com.google.gerrit.server.change.SetCherryPickOp;
import com.google.gerrit.server.change.ValidationOptionsUtil;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.MergeUtilFactory;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.IntegrationConflictException;
import com.google.gerrit.server.submit.MergeIdenticalTreeException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.ChangeIdUtil;

@Singleton
public class CherryPickChange {
  @AutoValue
  abstract static class Result {
    static Result create(Change.Id changeId, ImmutableSet<String> filesWithGitConflicts) {
      return new AutoValue_CherryPickChange_Result(changeId, filesWithGitConflicts);
    }

    abstract Change.Id changeId();

    abstract ImmutableSet<String> filesWithGitConflicts();
  }

  private final Sequences seq;
  private final Provider<InternalChangeQuery> queryProvider;
  private final GitRepositoryManager gitManager;
  private final ZoneId serverZoneId;
  private final Provider<IdentifiedUser> user;
  private final ChangeInserter.Factory changeInserterFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final SetCherryPickOp.Factory setCherryPickOfFactory;
  private final MergeUtilFactory mergeUtilFactory;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ProjectCache projectCache;
  private final ApprovalsUtil approvalsUtil;
  private final NotifyResolver notifyResolver;
  private final BatchUpdate.Factory batchUpdateFactory;

  @Inject
  CherryPickChange(
      Sequences seq,
      Provider<InternalChangeQuery> queryProvider,
      @GerritPersonIdent PersonIdent myIdent,
      GitRepositoryManager gitManager,
      Provider<IdentifiedUser> user,
      ChangeInserter.Factory changeInserterFactory,
      PatchSetInserter.Factory patchSetInserterFactory,
      SetCherryPickOp.Factory setCherryPickOfFactory,
      MergeUtilFactory mergeUtilFactory,
      ChangeNotes.Factory changeNotesFactory,
      ProjectCache projectCache,
      ApprovalsUtil approvalsUtil,
      NotifyResolver notifyResolver,
      BatchUpdate.Factory batchUpdateFactory) {
    this.seq = seq;
    this.queryProvider = queryProvider;
    this.gitManager = gitManager;
    this.serverZoneId = myIdent.getZoneId();
    this.user = user;
    this.changeInserterFactory = changeInserterFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.setCherryPickOfFactory = setCherryPickOfFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.changeNotesFactory = changeNotesFactory;
    this.projectCache = projectCache;
    this.approvalsUtil = approvalsUtil;
    this.notifyResolver = notifyResolver;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  /**
   * This function is used for cherry picking a change.
   *
   * @param change Change to cherry pick.
   * @param patch The patch of that change.
   * @param input Input object for different configurations of cherry pick.
   * @param dest Destination branch for the cherry pick.
   * @return Result object that describes the cherry pick.
   * @throws IOException Unable to open repository or read from the database.
   * @throws InvalidChangeOperationException Parent or branch don't exist, or two changes with same
   *     key exist in the branch.
   * @throws UpdateException Problem updating the database using batchUpdateFactory.
   * @throws RestApiException Error such as invalid SHA1
   * @throws ConfigInvalidException Can't find account to notify.
   * @throws NoSuchProjectException Can't find project state.
   */
  public Result cherryPick(Change change, PatchSet patch, CherryPickInput input, BranchNameKey dest)
      throws IOException, InvalidChangeOperationException, UpdateException, RestApiException,
          ConfigInvalidException, NoSuchProjectException {
    return cherryPick(
        change,
        change.getProject(),
        patch.commitId(),
        input,
        dest,
        TimeUtil.now(),
        null,
        null,
        null,
        null);
  }

  /**
   * This function is called directly to cherry pick a commit. Also, it is used to cherry pick a
   * change as well as long as sourceChange is not null.
   *
   * @param sourceChange Change to cherry pick. Can be null, and then the function will only cherry
   *     pick a commit.
   * @param project Project name
   * @param sourceCommit Id of the commit to be cherry picked.
   * @param input Input object for different configurations of cherry pick.
   * @param dest Destination branch for the cherry pick.
   * @return Result object that describes the cherry pick.
   * @throws IOException Unable to open repository or read from the database.
   * @throws InvalidChangeOperationException Parent or branch don't exist, or two changes with same
   *     key exist in the branch.
   * @throws UpdateException Problem updating the database using batchUpdateFactory.
   * @throws RestApiException Error such as invalid SHA1
   * @throws ConfigInvalidException Can't find account to notify.
   * @throws NoSuchProjectException Can't find project state.
   */
  public Result cherryPick(
      @Nullable Change sourceChange,
      Project.NameKey project,
      ObjectId sourceCommit,
      CherryPickInput input,
      BranchNameKey dest)
      throws IOException, InvalidChangeOperationException, UpdateException, RestApiException,
          ConfigInvalidException, NoSuchProjectException {
    return cherryPick(
        sourceChange, project, sourceCommit, input, dest, TimeUtil.now(), null, null, null, null);
  }

  /**
   * This function can be called directly to cherry-pick a change (or commit if sourceChange is
   * null) with a few other parameters that are especially useful for cherry-picking a commit that
   * is the revert-of another change.
   *
   * @param sourceChange Change to cherry pick. Can be null, and then the function will only cherry
   *     pick a commit.
   * @param project Project name
   * @param sourceCommit Id of the commit to be cherry picked.
   * @param input Input object for different configurations of cherry pick.
   * @param dest Destination branch for the cherry pick.
   * @param timestamp the current timestamp.
   * @param revertedChange The id of the change that is reverted. This is used for the "revertOf"
   *     field to mark the created cherry pick change as "revertOf" the original change that was
   *     reverted.
   * @param changeIdForNewChange The Change-Id that the new change of the cherry pick will have.
   * @param idForNewChange The ID that the new change of the cherry pick will have. If provided and
   *     the cherry-pick doesn't result in creating a new change, then
   *     InvalidChangeOperationException is thrown.
   * @return Result object that describes the cherry pick.
   * @throws IOException Unable to open repository or read from the database.
   * @throws InvalidChangeOperationException Parent or branch don't exist, or two changes with same
   *     key exist in the branch. Also thrown when idForNewChange is not null but cherry-pick only
   *     creates a new patchset rather than a new change.
   * @throws UpdateException Problem updating the database using batchUpdateFactory.
   * @throws RestApiException Error such as invalid SHA1
   * @throws ConfigInvalidException Can't find account to notify.
   * @throws NoSuchProjectException Can't find project state.
   */
  public Result cherryPick(
      @Nullable Change sourceChange,
      Project.NameKey project,
      ObjectId sourceCommit,
      CherryPickInput input,
      BranchNameKey dest,
      Instant timestamp,
      @Nullable Change.Id revertedChange,
      @Nullable ObjectId changeIdForNewChange,
      @Nullable Change.Id idForNewChange,
      @Nullable Boolean workInProgress)
      throws IOException, InvalidChangeOperationException, UpdateException, RestApiException,
          ConfigInvalidException, NoSuchProjectException {
    IdentifiedUser identifiedUser = user.get();
    try (Repository git = gitManager.openRepository(project);
        // This inserter and revwalk *must* be passed to any BatchUpdates
        // created later on, to ensure the cherry-picked commit is flushed
        // before patch sets are updated.
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        CodeReviewRevWalk revWalk = CodeReviewCommit.newRevWalk(reader)) {
      Ref destRef = git.getRefDatabase().exactRef(dest.branch());
      if (destRef == null) {
        throw new InvalidChangeOperationException(
            String.format("Branch %s does not exist.", dest.branch()));
      }

      RevCommit baseCommit =
          CommitUtil.getBaseCommit(
              project.get(), queryProvider.get(), revWalk, destRef, input.base);

      CodeReviewCommit commitToCherryPick = revWalk.parseCommit(sourceCommit);

      if (input.parent <= 0 || input.parent > commitToCherryPick.getParentCount()) {
        throw new InvalidChangeOperationException(
            String.format(
                "Cherry Pick: Parent %s does not exist. Please specify a parent in"
                    + " range [1, %s].",
                input.parent, commitToCherryPick.getParentCount()));
      }

      // If the commit message is not set, the commit message of the source commit will be used.
      String commitMessage = Strings.nullToEmpty(input.message);
      commitMessage = commitMessage.isEmpty() ? commitToCherryPick.getFullMessage() : commitMessage;

      String destChangeId = getDestinationChangeId(commitMessage, changeIdForNewChange);

      ChangeData destChange = null;
      if (destChangeId != null) {
        // If "idForNewChange" is not null we must fail, since we are not expecting an already
        // existing change.
        destChange = getDestChangeWithVerification(destChangeId, dest, idForNewChange != null);
      }

      if (changeIdForNewChange != null) {
        // If Change-Id was explicitly provided for the new change, override the value in commit
        // message.
        commitMessage = ChangeIdUtil.insertId(commitMessage, changeIdForNewChange, true);
      } else if (destChangeId == null) {
        // If commit message did not specify Change-Id, generate a new one and insert to the
        // message.
        commitMessage =
            ChangeIdUtil.insertId(commitMessage, CommitMessageUtil.generateChangeId(), true);
      }
      commitMessage = CommitMessageUtil.checkAndSanitizeCommitMessage(commitMessage);

      CodeReviewCommit cherryPickCommit;
      ProjectState projectState =
          projectCache.get(dest.project()).orElseThrow(noSuchProject(dest.project()));
      PersonIdent committerIdent =
          commitToCherryPick.getCommitterIdent() == null
              ? identifiedUser.newCommitterIdent(timestamp, serverZoneId)
              : identifiedUser.newCommitterIdent(
                  commitToCherryPick.getCommitterIdent().getEmailAddress(),
                  timestamp,
                  serverZoneId);

      try {
        MergeUtil mergeUtil;
        if (input.allowConflicts) {
          // allowConflicts requires to use content merge
          mergeUtil = mergeUtilFactory.create(projectState, true);
        } else {
          // use content merge only if it's configured on the project
          mergeUtil = mergeUtilFactory.create(projectState);
        }
        cherryPickCommit =
            mergeUtil.createCherryPickFromCommit(
                oi,
                git.getConfig(),
                baseCommit,
                commitToCherryPick,
                committerIdent,
                commitMessage,
                revWalk,
                input.parent - 1,
                input.allowEmpty,
                input.allowConflicts);
        oi.flush();
      } catch (MergeIdenticalTreeException | MergeConflictException e) {
        throw new IntegrationConflictException("Cherry pick failed: " + e.getMessage(), e);
      }
      try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
        try (BatchUpdate bu = batchUpdateFactory.create(project, identifiedUser, timestamp)) {
          bu.setRepository(git, revWalk, oi);
          bu.setNotify(resolveNotify(input));
          Change.Id changeId;
          String newTopic = null;
          if (input.topic != null) {
            newTopic = Strings.emptyToNull(input.topic.trim());
          }
          if (newTopic == null
              && sourceChange != null
              && !Strings.isNullOrEmpty(sourceChange.getTopic())) {
            newTopic = sourceChange.getTopic() + "-" + dest.shortName();
          }
          if (destChange != null) {
            // The change key exists on the destination branch. The cherry pick
            // will be added as a new patch set.
            changeId =
                insertPatchSet(
                    bu,
                    git,
                    destChange.notes(),
                    cherryPickCommit,
                    sourceChange,
                    newTopic,
                    input,
                    workInProgress);
          } else {
            // Change key not found on destination branch. We can create a new
            // change.
            changeId =
                createNewChange(
                    bu,
                    cherryPickCommit,
                    dest.branch(),
                    newTopic,
                    project,
                    sourceChange,
                    sourceCommit,
                    input,
                    revertedChange,
                    idForNewChange,
                    workInProgress);
          }
          bu.execute();
          return Result.create(changeId, cherryPickCommit.getFilesWithGitConflicts());
        }
      }
    }
  }

  private Change.Id insertPatchSet(
      BatchUpdate bu,
      Repository git,
      ChangeNotes destNotes,
      CodeReviewCommit cherryPickCommit,
      @Nullable Change sourceChange,
      String topic,
      CherryPickInput input,
      @Nullable Boolean workInProgress)
      throws IOException {
    Change destChange = destNotes.getChange();
    PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
    PatchSetInserter inserter = patchSetInserterFactory.create(destNotes, psId, cherryPickCommit);
    inserter.setMessage("Uploaded patch set " + inserter.getPatchSetId().get() + ".");
    inserter.setTopic(topic);
    if (workInProgress != null) {
      inserter.setWorkInProgress(workInProgress);
    }
    if (shouldSetToReady(cherryPickCommit, destNotes, workInProgress)) {
      inserter.setWorkInProgress(false);
    }
    inserter.setValidationOptions(
        ValidationOptionsUtil.getValidateOptionsAsMultimap(input.validationOptions));
    bu.addOp(destChange.getId(), inserter);
    PatchSet.Id sourcePatchSetId = sourceChange == null ? null : sourceChange.currentPatchSetId();
    // If sourceChange is not provided, reset cherryPickOf to avoid stale value.
    if (sourcePatchSetId == null) {
      bu.addOp(destChange.getId(), new ResetCherryPickOp());
    } else if (destChange.getCherryPickOf() == null
        || !destChange.getCherryPickOf().equals(sourcePatchSetId)) {
      SetCherryPickOp cherryPickOfUpdater = setCherryPickOfFactory.create(sourcePatchSetId);
      bu.addOp(destChange.getId(), cherryPickOfUpdater);
    }
    return destChange.getId();
  }

  /**
   * We should set the change to be "ready for review" if: 1. workInProgress is not already set on
   * this request. 2. The patch-set doesn't have any git conflict markers. 3. The change used to be
   * work in progress (because of a previous patch-set).
   */
  private boolean shouldSetToReady(
      CodeReviewCommit cherryPickCommit,
      ChangeNotes destChangeNotes,
      @Nullable Boolean workInProgress) {
    return workInProgress == null
        && cherryPickCommit.getFilesWithGitConflicts().isEmpty()
        && destChangeNotes.getChange().isWorkInProgress();
  }

  private Change.Id createNewChange(
      BatchUpdate bu,
      CodeReviewCommit cherryPickCommit,
      String refName,
      String topic,
      Project.NameKey project,
      @Nullable Change sourceChange,
      @Nullable ObjectId sourceCommit,
      CherryPickInput input,
      @Nullable Change.Id revertOf,
      @Nullable Change.Id idForNewChange,
      @Nullable Boolean workInProgress)
      throws IOException, InvalidChangeOperationException {
    Change.Id changeId = idForNewChange != null ? idForNewChange : Change.id(seq.nextChangeId());
    ChangeInserter ins = changeInserterFactory.create(changeId, cherryPickCommit, refName);
    ins.setRevertOf(revertOf);
    if (workInProgress != null) {
      ins.setWorkInProgress(workInProgress);
    } else {
      ins.setWorkInProgress(
          (sourceChange != null && sourceChange.isWorkInProgress())
              || !cherryPickCommit.getFilesWithGitConflicts().isEmpty());
    }
    ins.setValidationOptions(
        ValidationOptionsUtil.getValidateOptionsAsMultimap(input.validationOptions));
    BranchNameKey sourceBranch = sourceChange == null ? null : sourceChange.getDest();
    PatchSet.Id sourcePatchSetId = sourceChange == null ? null : sourceChange.currentPatchSetId();
    ins.setMessage(
            revertOf == null
                ? messageForDestinationChange(
                    ins.getPatchSetId(), sourceBranch, sourceCommit, cherryPickCommit)
                : "Uploaded patch set 1.") // For revert commits, the message should not include
        // cherry-pick information.
        .setTopic(topic);
    if (revertOf == null) {
      ins.setCherryPickOf(sourcePatchSetId);
    }
    if (input.keepReviewers && sourceChange != null) {
      ReviewerSet reviewerSet =
          approvalsUtil.getReviewers(changeNotesFactory.createChecked(sourceChange));
      Set<Account.Id> reviewers =
          new HashSet<>(reviewerSet.byState(ReviewerStateInternal.REVIEWER));
      reviewers.add(sourceChange.getOwner());
      reviewers.remove(user.get().getAccountId());
      Set<Account.Id> ccs = new HashSet<>(reviewerSet.byState(ReviewerStateInternal.CC));
      ccs.remove(user.get().getAccountId());
      ins.setReviewersAndCcsIgnoreVisibility(reviewers, ccs);
    }
    // If there is a base, and the base is not merged, the groups will be overridden by the base's
    // groups.
    ins.setGroups(GroupCollector.getDefaultGroups(cherryPickCommit.getId()));
    if (input.base != null) {
      List<ChangeData> changes =
          queryProvider.get().setLimit(2).byBranchCommitOpen(project.get(), refName, input.base);
      if (changes.size() > 1) {
        throw new InvalidChangeOperationException(
            "Several changes with key "
                + input.base
                + " reside on the same branch. "
                + "Cannot cherry-pick on target branch.");
      }
      if (changes.size() == 1) {
        Change change = changes.get(0).change();
        ins.setGroups(changeNotesFactory.createChecked(change).getCurrentPatchSet().groups());
      }
    }
    bu.insertChange(ins);
    return changeId;
  }

  private NotifyResolver.Result resolveNotify(CherryPickInput input)
      throws BadRequestException, ConfigInvalidException, IOException {
    return notifyResolver.resolve(
        firstNonNull(input.notify, NotifyHandling.ALL), input.notifyDetails);
  }

  private String messageForDestinationChange(
      PatchSet.Id patchSetId,
      BranchNameKey sourceBranch,
      ObjectId sourceCommit,
      CodeReviewCommit cherryPickCommit) {
    StringBuilder stringBuilder = new StringBuilder("Patch Set ").append(patchSetId.get());
    if (sourceBranch != null) {
      stringBuilder.append(": Cherry Picked from branch ").append(sourceBranch.shortName());
    } else {
      stringBuilder.append(": Cherry Picked from commit ").append(sourceCommit.getName());
    }
    stringBuilder.append(".");

    if (!cherryPickCommit.getFilesWithGitConflicts().isEmpty()) {
      stringBuilder.append("\n\nThe following files contain Git conflicts:");
      cherryPickCommit.getFilesWithGitConflicts().stream()
          .sorted()
          .forEach(filePath -> stringBuilder.append("\n* ").append(filePath));
    }

    return stringBuilder.toString();
  }

  /**
   * Returns the Change-Id of destination change (as intended by the caller of cherry-pick
   * operation).
   *
   * <p>The Change-Id can be provided in one of the following ways:
   *
   * <ul>
   *   <li>Explicitly provided for the new change.
   *   <li>Provided in the input commit message.
   *   <li>Taken from the source commit if commit message was not set.
   * </ul>
   *
   * Otherwise should be generated.
   *
   * @param commitMessage the commit message, as intended by the caller of cherry-pick operation.
   * @param changeIdForNewChange the explicitly provided Change-Id for the new change.
   * @return The Change-Id of destination change, {@code null} if Change-Id was not provided by the
   *     caller of cherry-pick operation and should be generated.
   */
  @Nullable
  private String getDestinationChangeId(
      String commitMessage, @Nullable ObjectId changeIdForNewChange) {
    if (changeIdForNewChange != null) {
      return CommitMessageUtil.getChangeIdFromObjectId(changeIdForNewChange);
    }
    return CommitMessageUtil.getChangeIdFromCommitMessageFooter(commitMessage).orElse(null);
  }

  /**
   * Returns the change from the destination branch, if it exists and is valid for the cherry-pick.
   *
   * @param destChangeId the Change-ID of the change in the destination branch.
   * @param destBranch the branch to search by the Change-ID.
   * @param verifyIsMissing if {@code true}, verifies that the change should be missing in the
   *     destination branch.
   * @return the verified change or {@code null} if the change was not found.
   * @throws InvalidChangeOperationException if the change was found but failed validation
   */
  @Nullable
  private ChangeData getDestChangeWithVerification(
      String destChangeId, BranchNameKey destBranch, boolean verifyIsMissing)
      throws InvalidChangeOperationException {
    List<ChangeData> destChanges =
        queryProvider.get().setLimit(2).byBranchKey(destBranch, Change.key(destChangeId));
    if (destChanges.size() > 1) {
      throw new InvalidChangeOperationException(
          "Several changes with key "
              + destChangeId
              + " reside on the same branch. "
              + "Cannot create a new patch set.");
    }
    if (destChanges.size() == 1 && verifyIsMissing) {
      throw new InvalidChangeOperationException(
          String.format(
              "Expected that cherry-pick with Change-Id %s to branch %s "
                  + "in project %s creates a new change, but found existing change %d",
              destChangeId,
              destBranch.branch(),
              destBranch.project().get(),
              destChanges.get(0).getId().get()));
    }
    ChangeData destChange = destChanges.size() == 1 ? destChanges.get(0) : null;

    if (destChange != null && destChange.change().isClosed()) {
      throw new InvalidChangeOperationException(
          String.format(
              "Cherry-pick with Change-Id %s could not update the existing change %d "
                  + "in destination branch %s of project %s, because the change was closed (%s)",
              destChangeId,
              destChange.getId().get(),
              destBranch.branch(),
              destBranch.project(),
              destChange.change().getStatus().name()));
    }
    return destChange;
  }
}
