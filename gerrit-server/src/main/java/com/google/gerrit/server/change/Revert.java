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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.extensions.events.ChangeReverted;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.send.RevertedSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Revert
    implements RestModifyView<ChangeResource, RevertInput>, UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Revert.class);

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;
  private final ChangeInserter.Factory changeInserterFactory;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory updateFactory;
  private final Sequences seq;
  private final PatchSetUtil psUtil;
  private final RevertedSender.Factory revertedSenderFactory;
  private final ChangeJson.Factory json;
  private final PersonIdent serverIdent;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeReverted changeReverted;

  @Inject
  Revert(
      Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      ChangeInserter.Factory changeInserterFactory,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory updateFactory,
      Sequences seq,
      PatchSetUtil psUtil,
      RevertedSender.Factory revertedSenderFactory,
      ChangeJson.Factory json,
      @GerritPersonIdent PersonIdent serverIdent,
      ApprovalsUtil approvalsUtil,
      ChangeReverted changeReverted) {
    this.db = db;
    this.repoManager = repoManager;
    this.changeInserterFactory = changeInserterFactory;
    this.cmUtil = cmUtil;
    this.updateFactory = updateFactory;
    this.seq = seq;
    this.psUtil = psUtil;
    this.revertedSenderFactory = revertedSenderFactory;
    this.json = json;
    this.serverIdent = serverIdent;
    this.approvalsUtil = approvalsUtil;
    this.changeReverted = changeReverted;
  }

  @Override
  public ChangeInfo apply(ChangeResource req, RevertInput input)
      throws IOException, OrmException, RestApiException, UpdateException, NoSuchChangeException {
    RefControl refControl = req.getControl().getRefControl();
    ProjectControl projectControl = req.getControl().getProjectControl();

    Capable capable = projectControl.canPushToAtLeastOneRef();
    if (capable != Capable.OK) {
      throw new AuthException(capable.getMessage());
    }

    Change change = req.getChange();
    if (!refControl.canUpload()) {
      throw new AuthException("revert not permitted");
    } else if (change.getStatus() != Status.MERGED) {
      throw new ResourceConflictException("change is " + status(change));
    }

    Change.Id revertedChangeId = revert(req.getControl(), Strings.emptyToNull(input.message));
    return json.create(ChangeJson.NO_OPTIONS).format(req.getProject(), revertedChangeId);
  }

  private Change.Id revert(ChangeControl ctl, String message)
      throws OrmException, IOException, RestApiException, UpdateException {
    Change.Id changeIdToRevert = ctl.getChange().getId();
    PatchSet.Id patchSetId = ctl.getChange().currentPatchSetId();
    PatchSet patch = psUtil.get(db.get(), ctl.getNotes(), patchSetId);
    if (patch == null) {
      throw new ResourceNotFoundException(changeIdToRevert.toString());
    }

    Project.NameKey project = ctl.getProject().getNameKey();
    CurrentUser user = ctl.getUser();
    try (Repository git = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(git)) {
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

      Change changeToRevert = ctl.getChange();
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
      try (ObjectInserter oi = git.newObjectInserter()) {
        ObjectId id = oi.insert(revertCommitBuilder);
        oi.flush();
        RevCommit revertCommit = revWalk.parseCommit(id);

        ChangeInserter ins =
            changeInserterFactory
                .create(changeId, revertCommit, ctl.getChange().getDest().get())
                .setValidatePolicy(CommitValidators.Policy.GERRIT)
                .setTopic(changeToRevert.getTopic());
        ins.setMessage("Uploaded patch set 1.");

        Set<Account.Id> reviewers = new HashSet<>();
        reviewers.add(changeToRevert.getOwner());
        reviewers.addAll(approvalsUtil.getReviewers(db.get(), ctl.getNotes()).all());
        reviewers.remove(user.getAccountId());
        ins.setReviewers(reviewers);

        try (BatchUpdate bu = updateFactory.create(db.get(), project, user, now)) {
          bu.setRepository(git, revWalk, oi);
          bu.insertChange(ins);
          bu.addOp(changeId, new NotifyOp(ctl.getChange(), ins));
          bu.addOp(changeToRevert.getId(), new PostRevertedMessageOp(computedChangeId));
          bu.execute();
        }
      }
      return changeId;
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(changeIdToRevert.toString(), e);
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    return new UiAction.Description()
        .setLabel("Revert")
        .setTitle("Revert the change")
        .setVisible(
            resource.getChange().getStatus() == Status.MERGED
                && resource.getControl().getRefControl().canUpload());
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }

  private class NotifyOp extends BatchUpdate.Op {
    private final Change change;
    private final ChangeInserter ins;

    NotifyOp(Change change, ChangeInserter ins) {
      this.change = change;
      this.ins = ins;
    }

    @Override
    public void postUpdate(Context ctx) throws Exception {
      changeReverted.fire(change, ins.getChange(), ctx.getWhen());
      Change.Id changeId = ins.getChange().getId();
      try {
        RevertedSender cm = revertedSenderFactory.create(ctx.getProject(), changeId);
        cm.setFrom(ctx.getAccountId());
        cm.setChangeMessage(ins.getChangeMessage().getMessage(), ctx.getWhen());
        cm.send();
      } catch (Exception err) {
        log.error("Cannot send email for revert change " + changeId, err);
      }
    }
  }

  private class PostRevertedMessageOp extends BatchUpdate.Op {
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
