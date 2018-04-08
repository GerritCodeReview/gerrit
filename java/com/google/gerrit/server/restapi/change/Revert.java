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

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;

import com.google.common.base.Strings;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyUtil;
import com.google.gerrit.server.extensions.events.ChangeReverted;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.send.RevertedSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.MessageFormat;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Revert extends RetryingRestModifyView<ChangeResource, RevertInput, ChangeInfo>
    implements UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Revert.class);

  private final Provider<ReviewDb> db;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final ChangeInserter.Factory changeInserterFactory;
  private final ChangeMessagesUtil cmUtil;
  private final Sequences seq;
  private final PatchSetUtil psUtil;
  private final RevertedSender.Factory revertedSenderFactory;
  private final ChangeJson.Factory json;
  private final PersonIdent serverIdent;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeReverted changeReverted;
  private final ContributorAgreementsChecker contributorAgreements;
  private final ProjectCache projectCache;
  private final NotifyUtil notifyUtil;

  @Inject
  Revert(
      Provider<ReviewDb> db,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      ChangeInserter.Factory changeInserterFactory,
      ChangeMessagesUtil cmUtil,
      RetryHelper retryHelper,
      Sequences seq,
      PatchSetUtil psUtil,
      RevertedSender.Factory revertedSenderFactory,
      ChangeJson.Factory json,
      @GerritPersonIdent PersonIdent serverIdent,
      ApprovalsUtil approvalsUtil,
      ChangeReverted changeReverted,
      ContributorAgreementsChecker contributorAgreements,
      ProjectCache projectCache,
      NotifyUtil notifyUtil) {
    super(retryHelper);
    this.db = db;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.changeInserterFactory = changeInserterFactory;
    this.cmUtil = cmUtil;
    this.seq = seq;
    this.psUtil = psUtil;
    this.revertedSenderFactory = revertedSenderFactory;
    this.json = json;
    this.serverIdent = serverIdent;
    this.approvalsUtil = approvalsUtil;
    this.changeReverted = changeReverted;
    this.contributorAgreements = contributorAgreements;
    this.projectCache = projectCache;
    this.notifyUtil = notifyUtil;
  }

  @Override
  public ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, RevertInput input)
      throws IOException, OrmException, RestApiException, UpdateException, NoSuchChangeException,
          PermissionBackendException, NoSuchProjectException, ConfigInvalidException {
    Change change = rsrc.getChange();
    if (change.getStatus() != Change.Status.MERGED) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    }

    contributorAgreements.check(rsrc.getProject(), rsrc.getUser());
    permissionBackend.user(rsrc.getUser()).ref(change.getDest()).check(CREATE_CHANGE);
    projectCache.checkedGet(rsrc.getProject()).checkStatePermitsWrite();

    Change.Id revertId = revert(updateFactory, rsrc.getNotes(), rsrc.getUser(), input);
    return json.noOptions().format(rsrc.getProject(), revertId);
  }

  private Change.Id revert(
      BatchUpdate.Factory updateFactory, ChangeNotes notes, CurrentUser user, RevertInput input)
      throws OrmException, IOException, RestApiException, UpdateException, ConfigInvalidException {
    String message = Strings.emptyToNull(input.message);
    Change.Id changeIdToRevert = notes.getChangeId();
    PatchSet.Id patchSetId = notes.getChange().currentPatchSetId();
    PatchSet patch = psUtil.get(db.get(), notes, patchSetId);
    if (patch == null) {
      throw new ResourceNotFoundException(changeIdToRevert.toString());
    }

    Project.NameKey project = notes.getProjectName();
    try (Repository git = repoManager.openRepository(project);
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk revWalk = new RevWalk(reader)) {
      RevCommit commitToRevert =
          revWalk.parseCommit(ObjectId.fromString(patch.getRevision().get()));
      if (commitToRevert.getParentCount() == 0) {
        throw new ResourceConflictException("Cannot revert initial commit");
      }

      Timestamp now = TimeUtil.nowTs();
      PersonIdent committerIdent = new PersonIdent(serverIdent, now);
      PersonIdent authorIdent =
          user.asIdentifiedUser().newCommitterIdent(now, committerIdent.getTimeZone());

      RevCommit parentToCommitToRevert = commitToRevert.getParent(0);
      revWalk.parseHeaders(parentToCommitToRevert);

      CommitBuilder revertCommitBuilder = new CommitBuilder();
      revertCommitBuilder.addParentId(commitToRevert);
      revertCommitBuilder.setTreeId(parentToCommitToRevert.getTree());
      revertCommitBuilder.setAuthor(authorIdent);
      revertCommitBuilder.setCommitter(authorIdent);

      Change changeToRevert = notes.getChange();
      if (message == null) {
        message =
            MessageFormat.format(
                ChangeMessages.get().revertChangeDefaultMessage,
                changeToRevert.getSubject(),
                patch.getRevision().get());
      }

      ObjectId computedChangeId =
          ChangeIdUtil.computeChangeId(
              parentToCommitToRevert.getTree(),
              commitToRevert,
              authorIdent,
              committerIdent,
              message);
      revertCommitBuilder.setMessage(ChangeIdUtil.insertId(message, computedChangeId, true));

      Change.Id changeId = new Change.Id(seq.nextChangeId());
      ObjectId id = oi.insert(revertCommitBuilder);
      RevCommit revertCommit = revWalk.parseCommit(id);

      ListMultimap<RecipientType, Account.Id> accountsToNotify =
          notifyUtil.resolveAccounts(input.notifyDetails);

      ChangeInserter ins =
          changeInserterFactory
              .create(changeId, revertCommit, notes.getChange().getDest().get())
              .setTopic(changeToRevert.getTopic());
      ins.setMessage("Uploaded patch set 1.");
      ins.setNotify(input.notify);
      ins.setAccountsToNotify(accountsToNotify);

      ReviewerSet reviewerSet = approvalsUtil.getReviewers(db.get(), notes);

      Set<Account.Id> reviewers = new HashSet<>();
      reviewers.add(changeToRevert.getOwner());
      reviewers.addAll(reviewerSet.byState(ReviewerStateInternal.REVIEWER));
      reviewers.remove(user.getAccountId());
      ins.setReviewers(reviewers);

      Set<Account.Id> ccs = new HashSet<>(reviewerSet.byState(ReviewerStateInternal.CC));
      ccs.remove(user.getAccountId());
      ins.setExtraCC(ccs);
      ins.setRevertOf(changeIdToRevert);

      try (BatchUpdate bu = updateFactory.create(db.get(), project, user, now)) {
        bu.setRepository(git, revWalk, oi);
        bu.insertChange(ins);
        bu.addOp(changeId, new NotifyOp(changeToRevert, ins, input.notify, accountsToNotify));
        bu.addOp(changeToRevert.getId(), new PostRevertedMessageOp(computedChangeId));
        bu.execute();
      }
      return changeId;
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(changeIdToRevert.toString(), e);
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    boolean projectStatePermitsWrite = false;
    try {
      projectStatePermitsWrite = projectCache.checkedGet(rsrc.getProject()).statePermitsWrite();
    } catch (IOException e) {
      log.error("Failed to check if project state permits write: " + rsrc.getProject(), e);
    }
    return new UiAction.Description()
        .setLabel("Revert")
        .setTitle("Revert the change")
        .setVisible(
            and(
                change.getStatus() == Change.Status.MERGED && projectStatePermitsWrite,
                permissionBackend
                    .user(rsrc.getUser())
                    .ref(change.getDest())
                    .testCond(CREATE_CHANGE)));
  }

  private class NotifyOp implements BatchUpdateOp {
    private final Change change;
    private final ChangeInserter ins;
    private final NotifyHandling notifyHandling;
    private final ListMultimap<RecipientType, Account.Id> accountsToNotify;

    NotifyOp(
        Change change,
        ChangeInserter ins,
        NotifyHandling notifyHandling,
        ListMultimap<RecipientType, Account.Id> accountsToNotify) {
      this.change = change;
      this.ins = ins;
      this.notifyHandling = notifyHandling;
      this.accountsToNotify = accountsToNotify;
    }

    @Override
    public void postUpdate(Context ctx) throws Exception {
      changeReverted.fire(change, ins.getChange(), ctx.getWhen());
      try {
        RevertedSender cm = revertedSenderFactory.create(ctx.getProject(), change.getId());
        cm.setFrom(ctx.getAccountId());
        cm.setNotify(notifyHandling);
        cm.setAccountsToNotify(accountsToNotify);
        cm.send();
      } catch (Exception err) {
        log.error("Cannot send email for revert change " + change.getId(), err);
      }
    }
  }

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
              "Created a revert of this change as I" + computedChangeId.name(),
              ChangeMessagesUtil.TAG_REVERT);
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getUpdate(patchSetId), changeMessage);
      return true;
    }
  }
}
