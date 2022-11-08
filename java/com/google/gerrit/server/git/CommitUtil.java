// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.ValidationOptionsUtil;
import com.google.gerrit.server.extensions.events.ChangeReverted;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.RevertedSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;

/** Static utilities for working with {@link RevCommit}s. */
@Singleton
public class CommitUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final Provider<PersonIdent> serverIdent;
  private final Sequences seq;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeInserter.Factory changeInserterFactory;
  private final NotifyResolver notifyResolver;
  private final RevertedSender.Factory revertedSenderFactory;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeReverted changeReverted;
  private final BatchUpdate.Factory updateFactory;
  private final MessageIdGenerator messageIdGenerator;

  @Inject
  CommitUtil(
      GitRepositoryManager repoManager,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      Sequences seq,
      ApprovalsUtil approvalsUtil,
      ChangeInserter.Factory changeInserterFactory,
      NotifyResolver notifyResolver,
      RevertedSender.Factory revertedSenderFactory,
      ChangeMessagesUtil cmUtil,
      ChangeReverted changeReverted,
      BatchUpdate.Factory updateFactory,
      MessageIdGenerator messageIdGenerator) {
    this.repoManager = repoManager;
    this.serverIdent = serverIdent;
    this.seq = seq;
    this.approvalsUtil = approvalsUtil;
    this.changeInserterFactory = changeInserterFactory;
    this.notifyResolver = notifyResolver;
    this.revertedSenderFactory = revertedSenderFactory;
    this.cmUtil = cmUtil;
    this.changeReverted = changeReverted;
    this.updateFactory = updateFactory;
    this.messageIdGenerator = messageIdGenerator;
  }

  public static CommitInfo toCommitInfo(RevCommit commit) throws IOException {
    return toCommitInfo(commit, null);
  }

  public static CommitInfo toCommitInfo(RevCommit commit, @Nullable RevWalk walk)
      throws IOException {
    CommitInfo info = new CommitInfo();
    info.commit = commit.getName();
    info.author = CommonConverters.toGitPerson(commit.getAuthorIdent());
    info.committer = CommonConverters.toGitPerson(commit.getCommitterIdent());
    info.subject = commit.getShortMessage();
    info.message = commit.getFullMessage();
    info.parents = new ArrayList<>(commit.getParentCount());
    for (int i = 0; i < commit.getParentCount(); i++) {
      RevCommit p = walk == null ? commit.getParent(i) : walk.parseCommit(commit.getParent(i));
      CommitInfo parentInfo = new CommitInfo();
      parentInfo.commit = p.getName();
      parentInfo.subject = p.getShortMessage();
      info.parents.add(parentInfo);
    }
    return info;
  }

  /**
   * Allows creating a revert change.
   *
   * @param notes ChangeNotes of the change being reverted.
   * @param user Current User performing the revert.
   * @param input the RevertInput entity for conducting the revert.
   * @param timestamp timestamp for the created change.
   * @return ObjectId that represents the newly created commit.
   */
  public Change.Id createRevertChange(
      ChangeNotes notes, CurrentUser user, RevertInput input, Instant timestamp)
      throws RestApiException, UpdateException, ConfigInvalidException, IOException {
    String message = Strings.emptyToNull(input.message);

    try (Repository git = repoManager.openRepository(notes.getProjectName());
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk revWalk = new RevWalk(reader)) {
      ObjectId generatedChangeId = CommitMessageUtil.generateChangeId();
      ObjectId revCommit =
          createRevertCommit(message, notes, user, timestamp, oi, revWalk, generatedChangeId);
      return createRevertChangeFromCommit(
          revCommit, input, notes, user, generatedChangeId, timestamp, oi, revWalk, git);
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(notes.getChangeId().toString(), e);
    }
  }

  /**
   * Wrapper function for creating a revert Commit.
   *
   * @param message Commit message for the revert commit.
   * @param notes ChangeNotes of the change being reverted.
   * @param user Current User performing the revert.
   * @param ts Timestamp of creation for the commit.
   * @return ObjectId that represents the newly created commit.
   */
  public ObjectId createRevertCommit(
      String message, ChangeNotes notes, CurrentUser user, Instant ts)
      throws RestApiException, IOException {

    try (Repository git = repoManager.openRepository(notes.getProjectName());
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk revWalk = new RevWalk(reader)) {
      return createRevertCommit(message, notes, user, ts, oi, revWalk, null);
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(notes.getProjectName().toString(), e);
    }
  }

  /**
   * Creates a commit with the specified tree ID.
   *
   * @param oi ObjectInserter for inserting the newly created commit.
   * @param authorIdent of the new commit
   * @param committerIdent of the new commit
   * @param parentCommit of the new commit. Can be null.
   * @param commitMessage for the new commit.
   * @param treeId of the content for the new commit.
   * @return the newly created commit.
   * @throws IOException if fails to insert the commit.
   */
  public static ObjectId createCommitWithTree(
      ObjectInserter oi,
      PersonIdent authorIdent,
      PersonIdent committerIdent,
      @Nullable RevCommit parentCommit,
      String commitMessage,
      ObjectId treeId)
      throws IOException {
    logger.atFine().log("Creating commit with tree: %s", treeId.getName());
    CommitBuilder commit = new CommitBuilder();
    commit.setTreeId(treeId);
    if (parentCommit != null) {
      commit.setParentId(parentCommit);
    }
    commit.setAuthor(authorIdent);
    commit.setCommitter(committerIdent);
    commit.setMessage(commitMessage);

    ObjectId id = oi.insert(commit);
    oi.flush();
    return id;
  }

  /**
   * Creates a revert commit.
   *
   * @param message Commit message for the revert commit.
   * @param notes ChangeNotes of the change being reverted.
   * @param user Current User performing the revert.
   * @param ts Timestamp of creation for the commit.
   * @param oi ObjectInserter for inserting the newly created commit.
   * @param revWalk Used for parsing the original commit.
   * @param generatedChangeId The changeId for the commit message, can be null since it is not
   *     needed for commits, only for changes.
   * @return ObjectId that represents the newly created commit.
   * @throws ResourceConflictException Can't revert the initial commit.
   * @throws IOException Thrown in case of I/O errors.
   */
  private ObjectId createRevertCommit(
      String message,
      ChangeNotes notes,
      CurrentUser user,
      Instant ts,
      ObjectInserter oi,
      RevWalk revWalk,
      @Nullable ObjectId generatedChangeId)
      throws ResourceConflictException, IOException {

    PatchSet patch = notes.getCurrentPatchSet();
    RevCommit commitToRevert = revWalk.parseCommit(patch.commitId());
    if (commitToRevert.getParentCount() == 0) {
      throw new ResourceConflictException("Cannot revert initial commit");
    }

    PersonIdent committerIdent = serverIdent.get();
    PersonIdent authorIdent =
        user.asIdentifiedUser().newCommitterIdent(ts, committerIdent.getZoneId());

    RevCommit parentToCommitToRevert = commitToRevert.getParent(0);
    revWalk.parseHeaders(parentToCommitToRevert);

    Change changeToRevert = notes.getChange();
    String subject = changeToRevert.getSubject();
    if (subject.length() > 63) {
      subject = subject.substring(0, 59) + "...";
    }
    if (message == null) {
      message =
          MessageFormat.format(
              ChangeMessages.get().revertChangeDefaultMessage, subject, patch.commitId().name());
    }
    if (generatedChangeId != null) {
      message = ChangeIdUtil.insertId(message, generatedChangeId, true);
    }

    return createCommitWithTree(
        oi, authorIdent, committerIdent, commitToRevert, message, parentToCommitToRevert.getTree());
  }

  private Change.Id createRevertChangeFromCommit(
      ObjectId revertCommitId,
      RevertInput input,
      ChangeNotes notes,
      CurrentUser user,
      @Nullable ObjectId generatedChangeId,
      Instant ts,
      ObjectInserter oi,
      RevWalk revWalk,
      Repository git)
      throws IOException, RestApiException, UpdateException, ConfigInvalidException {
    RevCommit revertCommit = revWalk.parseCommit(revertCommitId);
    Change changeToRevert = notes.getChange();
    Change.Id changeId = Change.id(seq.nextChangeId());
    if (input.workInProgress) {
      input.notify = firstNonNull(input.notify, NotifyHandling.OWNER);
    }
    NotifyResolver.Result notify =
        notifyResolver.resolve(firstNonNull(input.notify, NotifyHandling.ALL), input.notifyDetails);

    ChangeInserter ins =
        changeInserterFactory
            .create(changeId, revertCommit, notes.getChange().getDest().branch())
            .setTopic(input.topic == null ? changeToRevert.getTopic() : input.topic.trim());
    ins.setMessage("Uploaded patch set 1.");
    ins.setValidationOptions(ValidationOptionsUtil.getValidateOptionsAsMultimap(input.validationOptions));

    ReviewerSet reviewerSet = approvalsUtil.getReviewers(notes);

    Set<Account.Id> reviewers = new HashSet<>();
    reviewers.add(changeToRevert.getOwner());
    reviewers.addAll(reviewerSet.byState(ReviewerStateInternal.REVIEWER));
    reviewers.remove(user.getAccountId());
    Set<Account.Id> ccs = new HashSet<>(reviewerSet.byState(ReviewerStateInternal.CC));
    ccs.remove(user.getAccountId());
    ins.setReviewersAndCcsIgnoreVisibility(reviewers, ccs);
    ins.setRevertOf(notes.getChangeId());
    ins.setWorkInProgress(input.workInProgress);

    try (BatchUpdate bu = updateFactory.create(notes.getProjectName(), user, ts)) {
      bu.setRepository(git, revWalk, oi);
      bu.setNotify(notify);
      bu.insertChange(ins);
      bu.addOp(changeId, new NotifyOp(changeToRevert, ins));
      bu.addOp(changeToRevert.getId(), new PostRevertedMessageOp(generatedChangeId));
      bu.execute();
    }
    return changeId;
  }

  private class NotifyOp implements BatchUpdateOp {
    private final Change change;
    private final ChangeInserter ins;

    NotifyOp(Change change, ChangeInserter ins) {
      this.change = change;
      this.ins = ins;
    }

    @Override
    public void postUpdate(PostUpdateContext ctx) throws Exception {
      changeReverted.fire(
          ctx.getChangeData(change), ctx.getChangeData(ins.getChange()), ctx.getWhen());
      try {
        RevertedSender emailSender = revertedSenderFactory.create(ctx.getProject(), change.getId());
        emailSender.setFrom(ctx.getAccountId());
        emailSender.setNotify(ctx.getNotify(change.getId()));
        emailSender.setMessageId(
            messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), change.currentPatchSetId()));
        emailSender.send();
      } catch (Exception err) {
        logger.atSevere().withCause(err).log(
            "Cannot send email for revert change %s", change.getId());
      }
    }
  }

  private class PostRevertedMessageOp implements BatchUpdateOp {
    private final ObjectId computedChangeId;

    PostRevertedMessageOp(ObjectId computedChangeId) {
      this.computedChangeId = computedChangeId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) {
      cmUtil.setChangeMessage(
          ctx,
          "Created a revert of this change as I" + computedChangeId.name(),
          ChangeMessagesUtil.TAG_REVERT);
      return true;
    }
  }

  /**
   * Returns the parent commit for a new commit.
   *
   * <p>If {@code baseSha1} is provided, the method verifies it can be used as a base. If {@code
   * baseSha1} is not provided the tip of the {@code destRef} is returned.
   *
   * @param project The name of the project.
   * @param changeQuery Used for looking up the base commit.
   * @param revWalk Used for parsing the base commit.
   * @param destRef The destination branch.
   * @param baseSha1 The hash of the base commit. Nullable.
   * @return the base commit. Either the commit matching the provided hash, or the direct parent if
   *     a hash was not provided.
   * @throws IOException if the branch reference cannot be parsed.
   * @throws RestApiException if the base commit cannot be fetched.
   */
  public static RevCommit getBaseCommit(
      String project,
      InternalChangeQuery changeQuery,
      RevWalk revWalk,
      Ref destRef,
      @Nullable String baseSha1)
      throws IOException, RestApiException {
    RevCommit destRefTip = revWalk.parseCommit(destRef.getObjectId());
    // The tip commit of the destination ref is the default base for the newly created change.
    if (Strings.isNullOrEmpty(baseSha1)) {
      return destRefTip;
    }

    ObjectId baseObjectId;
    try {
      baseObjectId = ObjectId.fromString(baseSha1);
    } catch (InvalidObjectIdException e) {
      throw new BadRequestException(
          String.format("Base %s doesn't represent a valid SHA-1", baseSha1), e);
    }

    RevCommit baseCommit;
    try {
      baseCommit = revWalk.parseCommit(baseObjectId);
    } catch (MissingObjectException e) {
      throw new UnprocessableEntityException(
          String.format("Base %s doesn't exist", baseObjectId.name()), e);
    }

    changeQuery.enforceVisibility(true);
    List<ChangeData> changeDatas = changeQuery.byBranchCommit(project, destRef.getName(), baseSha1);

    if (changeDatas.isEmpty()) {
      if (revWalk.isMergedInto(baseCommit, destRefTip)) {
        // The base commit is a merged commit with no change associated.
        return baseCommit;
      }
      throw new UnprocessableEntityException(
          String.format("Commit %s does not exist on branch %s", baseSha1, destRef.getName()));
    } else if (changeDatas.size() != 1) {
      throw new ResourceConflictException("Multiple changes found for commit " + baseSha1);
    }

    Change change = changeDatas.get(0).change();
    if (!change.isAbandoned()) {
      // The base commit is a valid change revision.
      return baseCommit;
    }

    throw new ResourceConflictException(
        String.format(
            "Change %s with commit %s is %s",
            change.getChangeId(), baseSha1, ChangeUtil.status(change)));
  }
}
