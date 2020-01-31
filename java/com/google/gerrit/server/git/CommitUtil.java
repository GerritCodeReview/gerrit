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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Account.Id;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.extensions.events.ChangeReverted;
import com.google.gerrit.server.mail.send.RevertedSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
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
      BatchUpdate.Factory updateFactory) {
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
      ChangeNotes notes, CurrentUser user, RevertInput input, Timestamp timestamp)
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
      String message, ChangeNotes notes, CurrentUser user, Timestamp ts)
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
      Timestamp ts,
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
        user.asIdentifiedUser().newCommitterIdent(ts, committerIdent.getTimeZone());

    RevCommit parentToCommitToRevert = commitToRevert.getParent(0);
    revWalk.parseHeaders(parentToCommitToRevert);

    CommitBuilder revertCommitBuilder = new CommitBuilder();
    revertCommitBuilder.addParentId(commitToRevert);
    revertCommitBuilder.setTreeId(parentToCommitToRevert.getTree());
    revertCommitBuilder.setAuthor(authorIdent);
    revertCommitBuilder.setCommitter(authorIdent);

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
      revertCommitBuilder.setMessage(ChangeIdUtil.insertId(message, generatedChangeId, true));
    }
    ObjectId id = oi.insert(revertCommitBuilder);
    oi.flush();
    return id;
  }

  private Change.Id createRevertChangeFromCommit(
      ObjectId revertCommitId,
      RevertInput input,
      ChangeNotes notes,
      CurrentUser user,
      @Nullable ObjectId generatedChangeId,
      Timestamp ts,
      ObjectInserter oi,
      RevWalk revWalk,
      Repository git)
      throws IOException, RestApiException, UpdateException, ConfigInvalidException {
    RevCommit revertCommit = revWalk.parseCommit(revertCommitId);
    Change changeToRevert = notes.getChange();
    Change.Id changeId = Change.id(seq.nextChangeId());
    NotifyResolver.Result notify =
        notifyResolver.resolve(firstNonNull(input.notify, NotifyHandling.ALL), input.notifyDetails);

    ChangeInserter ins =
        changeInserterFactory
            .create(changeId, revertCommit, notes.getChange().getDest().branch())
            .setTopic(input.topic == null ? changeToRevert.getTopic() : input.topic.trim());
    ins.setMessage("Uploaded patch set 1.");

    ReviewerSet reviewerSet = approvalsUtil.getReviewers(notes);

    Set<Id> reviewers = new HashSet<>();
    reviewers.add(changeToRevert.getOwner());
    reviewers.addAll(reviewerSet.byState(ReviewerStateInternal.REVIEWER));
    reviewers.remove(user.getAccountId());
    Set<Account.Id> ccs = new HashSet<>(reviewerSet.byState(ReviewerStateInternal.CC));
    ccs.remove(user.getAccountId());
    ins.setReviewersAndCcs(reviewers, ccs);
    ins.setRevertOf(notes.getChangeId());

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
    public void postUpdate(Context ctx) throws Exception {
      changeReverted.fire(change, ins.getChange(), ctx.getWhen());
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

  private class PostRevertedMessageOp implements BatchUpdateOp {
    private final ObjectId computedChangeId;

    PostRevertedMessageOp(ObjectId computedChangeId) {
      this.computedChangeId = computedChangeId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) {
      Change change = ctx.getChange();
      PatchSet.Id patchSetId = change.currentPatchSetId();
      ChangeMessage changeMessage =
          ChangeMessagesUtil.newMessage(
              ctx,
              "Created a revert of this change as I" + computedChangeId.name(),
              ChangeMessagesUtil.TAG_REVERT);
      cmUtil.addChangeMessage(ctx.getUpdate(patchSetId), changeMessage);
      return true;
    }
  }
}
