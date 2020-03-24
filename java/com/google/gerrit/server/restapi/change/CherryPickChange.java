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

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.FooterConstants;
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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.change.SetCherryPickOp;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
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
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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
  private final TimeZone serverTimeZone;
  private final Provider<IdentifiedUser> user;
  private final ChangeInserter.Factory changeInserterFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final SetCherryPickOp.Factory setCherryPickOfFactory;
  private final MergeUtil.Factory mergeUtilFactory;
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
      MergeUtil.Factory mergeUtilFactory,
      ChangeNotes.Factory changeNotesFactory,
      ProjectCache projectCache,
      ApprovalsUtil approvalsUtil,
      NotifyResolver notifyResolver,
      BatchUpdate.Factory batchUpdateFactory) {
    this.seq = seq;
    this.queryProvider = queryProvider;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
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
        false,
        TimeUtil.nowTs(),
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
        sourceChange,
        project,
        sourceCommit,
        input,
        dest,
        false,
        TimeUtil.nowTs(),
        null,
        null,
        null,
        null);
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
   * @param ignoreIdenticalTree When false, we throw an error when trying to cherry-pick creates an
   *     empty commit. When true, we allow creation of an empty commit.
   * @param timestamp the current timestamp.
   * @param revertedChange The id of the change that is reverted. This is used for the "revertOf"
   *     field to mark the created cherry pick change as "revertOf" the original change that was
   *     reverted.
   * @param changeIdForNewChange The Change-Id that the new change of the cherry pick will have.
   * @param idForNewChange The ID that the new change of the cherry pick will have. If provided and
   *     the cherry-pick doesn't result in creating a new change, then
   *     InvalidChangeOperationException is thrown.
   * @param groupName The name of the group for grouping related changes (used by GetRelated
   *     endpoint).
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
      boolean ignoreIdenticalTree,
      Timestamp timestamp,
      @Nullable Change.Id revertedChange,
      @Nullable ObjectId changeIdForNewChange,
      @Nullable Change.Id idForNewChange,
      @Nullable String groupName)
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

      RevCommit baseCommit = getBaseCommit(destRef, project.get(), revWalk, input.base);

      CodeReviewCommit commitToCherryPick = revWalk.parseCommit(sourceCommit);

      if (input.parent <= 0 || input.parent > commitToCherryPick.getParentCount()) {
        throw new InvalidChangeOperationException(
            String.format(
                "Cherry Pick: Parent %s does not exist. Please specify a parent in"
                    + " range [1, %s].",
                input.parent, commitToCherryPick.getParentCount()));
      }

      String message = Strings.nullToEmpty(input.message).trim();
      message = message.isEmpty() ? commitToCherryPick.getFullMessage() : message;

      PersonIdent committerIdent = identifiedUser.newCommitterIdent(timestamp, serverTimeZone);

      final ObjectId generatedChangeId =
          changeIdForNewChange != null
              ? changeIdForNewChange
              : CommitMessageUtil.generateChangeId();
      String commitMessage = ChangeIdUtil.insertId(message, generatedChangeId).trim() + '\n';

      CodeReviewCommit cherryPickCommit;
      ProjectState projectState =
          projectCache.get(dest.project()).orElseThrow(noSuchProject(dest.project()));
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
                ignoreIdenticalTree,
                input.allowConflicts);

        Change.Key changeKey;
        final List<String> idList = cherryPickCommit.getFooterLines(FooterConstants.CHANGE_ID);
        if (!idList.isEmpty()) {
          final String idStr = idList.get(idList.size() - 1).trim();
          changeKey = Change.key(idStr);
        } else {
          changeKey = Change.key("I" + generatedChangeId.name());
        }

        BranchNameKey newDest = BranchNameKey.create(project, destRef.getName());
        List<ChangeData> destChanges =
            queryProvider.get().setLimit(2).byBranchKey(newDest, changeKey);
        if (destChanges.size() > 1) {
          throw new InvalidChangeOperationException(
              "Several changes with key "
                  + changeKey
                  + " reside on the same branch. "
                  + "Cannot create a new patch set.");
        }
        try (BatchUpdate bu = batchUpdateFactory.create(project, identifiedUser, timestamp)) {
          bu.setRepository(git, revWalk, oi);
          bu.setNotify(resolveNotify(input));
          Change.Id changeId;
          if (destChanges.size() == 1) {
            // The change key exists on the destination branch. The cherry pick
            // will be added as a new patch set. If "idForNewChange" is not null we must fail,
            // since we are not expecting an already existing change.
            if (idForNewChange != null) {
              throw new InvalidChangeOperationException(
                  String.format(
                      "Expected that cherry-pick of commit %s with Change-Id %s to branch %s"
                          + "in project %s creates a new change, but found existing change %d",
                      sourceCommit.getName(),
                      changeKey,
                      dest.branch(),
                      dest.project(),
                      destChanges.get(0).getId().get()));
            }
            changeId =
                insertPatchSet(
                    bu,
                    git,
                    destChanges.get(0).notes(),
                    cherryPickCommit,
                    sourceChange.currentPatchSetId());
          } else {
            // Change key not found on destination branch. We can create a new
            // change.
            String newTopic = null;
            if (input.topic != null) {
              newTopic = Strings.emptyToNull(input.topic.trim());
            }
            if (newTopic == null
                && sourceChange != null
                && !Strings.isNullOrEmpty(sourceChange.getTopic())) {
              newTopic = sourceChange.getTopic() + "-" + newDest.shortName();
            }
            changeId =
                createNewChange(
                    bu,
                    cherryPickCommit,
                    dest.branch(),
                    newTopic,
                    sourceChange,
                    sourceCommit,
                    input,
                    revertedChange,
                    idForNewChange,
                    groupName);
          }
          bu.execute();
          return Result.create(changeId, cherryPickCommit.getFilesWithGitConflicts());
        }
      } catch (MergeIdenticalTreeException | MergeConflictException e) {
        throw new IntegrationConflictException("Cherry pick failed: " + e.getMessage());
      }
    }
  }

  private RevCommit getBaseCommit(Ref destRef, String project, RevWalk revWalk, String base)
      throws RestApiException, IOException {
    RevCommit destRefTip = revWalk.parseCommit(destRef.getObjectId());
    // The tip commit of the destination ref is the default base for the newly created change.
    if (Strings.isNullOrEmpty(base)) {
      return destRefTip;
    }

    ObjectId baseObjectId;
    try {
      baseObjectId = ObjectId.fromString(base);
    } catch (InvalidObjectIdException e) {
      throw new BadRequestException(
          String.format("Base %s doesn't represent a valid SHA-1", base), e);
    }

    RevCommit baseCommit;
    try {
      baseCommit = revWalk.parseCommit(baseObjectId);
    } catch (MissingObjectException e) {
      throw new UnprocessableEntityException(
          String.format("Base %s doesn't exist", baseObjectId.name()), e);
    }

    InternalChangeQuery changeQuery = queryProvider.get();
    changeQuery.enforceVisibility(true);
    List<ChangeData> changeDatas = changeQuery.byBranchCommit(project, destRef.getName(), base);

    if (changeDatas.isEmpty()) {
      if (revWalk.isMergedInto(baseCommit, destRefTip)) {
        // The base commit is a merged commit with no change associated.
        return baseCommit;
      }
      throw new UnprocessableEntityException(
          String.format("Commit %s does not exist on branch %s", base, destRef.getName()));
    } else if (changeDatas.size() != 1) {
      throw new ResourceConflictException("Multiple changes found for commit " + base);
    }

    Change change = changeDatas.get(0).change();
    if (!change.isAbandoned()) {
      // The base commit is a valid change revision.
      return baseCommit;
    }

    throw new ResourceConflictException(
        String.format(
            "Change %s with commit %s is %s",
            change.getChangeId(), base, ChangeUtil.status(change)));
  }

  private Change.Id insertPatchSet(
      BatchUpdate bu,
      Repository git,
      ChangeNotes destNotes,
      CodeReviewCommit cherryPickCommit,
      PatchSet.Id sourcePatchSetId)
      throws IOException {
    Change destChange = destNotes.getChange();
    PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
    PatchSetInserter inserter = patchSetInserterFactory.create(destNotes, psId, cherryPickCommit);
    inserter.setMessage("Uploaded patch set " + inserter.getPatchSetId().get() + ".");
    bu.addOp(destChange.getId(), inserter);
    if (destChange.getCherryPickOf() == null
        || !destChange.getCherryPickOf().equals(sourcePatchSetId)) {
      SetCherryPickOp cherryPickOfUpdater = setCherryPickOfFactory.create(sourcePatchSetId);
      bu.addOp(destChange.getId(), cherryPickOfUpdater);
    }
    return destChange.getId();
  }

  private Change.Id createNewChange(
      BatchUpdate bu,
      CodeReviewCommit cherryPickCommit,
      String refName,
      String topic,
      @Nullable Change sourceChange,
      @Nullable ObjectId sourceCommit,
      CherryPickInput input,
      @Nullable Change.Id revertOf,
      @Nullable Change.Id idForNewChange,
      @Nullable String groupName)
      throws IOException {
    Change.Id changeId = idForNewChange != null ? idForNewChange : Change.id(seq.nextChangeId());
    ChangeInserter ins = changeInserterFactory.create(changeId, cherryPickCommit, refName);
    ins.setRevertOf(revertOf);
    BranchNameKey sourceBranch = sourceChange == null ? null : sourceChange.getDest();
    PatchSet.Id sourcePatchSetId = sourceChange == null ? null : sourceChange.currentPatchSetId();
    ins.setMessage(
            revertOf == null
                ? messageForDestinationChange(
                    ins.getPatchSetId(), sourceBranch, sourceCommit, cherryPickCommit)
                : "Uploaded patch set 1.") // For revert commits, the message should not include
        // cherry-pick information.
        .setTopic(topic)
        .setCherryPickOf(sourcePatchSetId)
        .setWorkInProgress(
            (sourceChange != null && sourceChange.isWorkInProgress())
                || !cherryPickCommit.getFilesWithGitConflicts().isEmpty());
    if (input.keepReviewers && sourceChange != null) {
      ReviewerSet reviewerSet =
          approvalsUtil.getReviewers(changeNotesFactory.createChecked(sourceChange));
      Set<Account.Id> reviewers =
          new HashSet<>(reviewerSet.byState(ReviewerStateInternal.REVIEWER));
      reviewers.add(sourceChange.getOwner());
      reviewers.remove(user.get().getAccountId());
      Set<Account.Id> ccs = new HashSet<>(reviewerSet.byState(ReviewerStateInternal.CC));
      ccs.remove(user.get().getAccountId());
      ins.setReviewersAndCcs(reviewers, ccs);
      if (groupName != null) {
        ins.setGroups(ImmutableList.of(groupName));
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
}
