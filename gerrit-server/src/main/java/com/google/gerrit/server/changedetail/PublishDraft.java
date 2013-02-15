// Copyright (C) 2011 The Android Open Source Project
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


package com.google.gerrit.server.changedetail;

import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromApprovals;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class PublishDraft implements Callable<ReviewResult> {
  private static final Logger log =
      LoggerFactory.getLogger(PublishDraft.class);

  public interface Factory {
    PublishDraft create(PatchSet.Id patchSetId);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final ChangeHooks hooks;
  private final GitRepositoryManager repoManager;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ApprovalsUtil approvalsUtil;
  private final AccountResolver accountResolver;
  private final CreateChangeSender.Factory createChangeSenderFactory;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;

  private final PatchSet.Id patchSetId;

  @Inject
  PublishDraft(final ChangeControl.Factory changeControlFactory,
      final ReviewDb db, final ChangeHooks hooks,
      final GitRepositoryManager repoManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ApprovalsUtil approvalsUtil,
      final AccountResolver accountResolver,
      final CreateChangeSender.Factory createChangeSenderFactory,
      final ReplacePatchSetSender.Factory replacePatchSetFactory,
      @Assisted final PatchSet.Id patchSetId) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.hooks = hooks;
    this.repoManager = repoManager;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.approvalsUtil = approvalsUtil;
    this.accountResolver = accountResolver;
    this.createChangeSenderFactory = createChangeSenderFactory;
    this.replacePatchSetFactory = replacePatchSetFactory;

    this.patchSetId = patchSetId;
  }

  @Override
  public ReviewResult call() throws NoSuchChangeException, OrmException,
      IOException, PatchSetInfoNotAvailableException {
    final ReviewResult result = new ReviewResult();

    final Change.Id changeId = patchSetId.getParentKey();
    result.setChangeId(changeId);
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    final LabelTypes labelTypes = control.getLabelTypes();
    final PatchSet patch = db.patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }
    if (!patch.isDraft()) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.NOT_A_DRAFT));
      return result;
    }

    if (!control.canPublish(db)) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.PUBLISH_NOT_PERMITTED));
    } else {
      final PatchSet updatedPatchSet = db.patchSets().atomicUpdate(patchSetId,
          new AtomicUpdate<PatchSet>() {
        @Override
        public PatchSet update(PatchSet patchset) {
          patchset.setDraft(false);
          return patchset;
        }
      });

      final Change updatedChange = db.changes().atomicUpdate(changeId,
          new AtomicUpdate<Change>() {
        @Override
        public Change update(Change change) {
          if (change.getStatus() == Change.Status.DRAFT) {
            change.setStatus(Change.Status.NEW);
            ChangeUtil.updated(change);
          }
          return change;
        }
      });

      if (!updatedPatchSet.isDraft() || updatedChange.getStatus() == Change.Status.NEW) {
        hooks.doDraftPublishedHook(updatedChange, updatedPatchSet, db);

        sendNotifications(control.getChange().getStatus() == Change.Status.DRAFT,
            (IdentifiedUser) control.getCurrentUser(), updatedChange, updatedPatchSet,
            labelTypes);
      }
    }

    return result;
  }

  private void sendNotifications(final boolean newChange,
      final IdentifiedUser currentUser, final Change updatedChange,
      final PatchSet updatedPatchSet, final LabelTypes labelTypes)
      throws OrmException, IOException, PatchSetInfoNotAvailableException {
    final Repository git = repoManager.openRepository(updatedChange.getProject());
    try {
      final RevWalk revWalk = new RevWalk(git);
      final RevCommit commit;
      try {
        commit = revWalk.parseCommit(ObjectId.fromString(updatedPatchSet.getRevision().get()));
      } finally {
        revWalk.release();
      }
      final PatchSetInfo info = patchSetInfoFactory.get(commit, updatedPatchSet.getId());
      final List<FooterLine> footerLines = commit.getFooterLines();
      final Account.Id me = currentUser.getAccountId();
      final MailRecipients recipients =
          getRecipientsFromFooters(accountResolver, updatedPatchSet, footerLines);
      recipients.remove(me);

      if (newChange) {
        approvalsUtil.addReviewers(db, labelTypes, updatedChange, updatedPatchSet, info,
            recipients.getReviewers(), Collections.<Account.Id> emptySet());
        try {
          CreateChangeSender cm = createChangeSenderFactory.create(updatedChange);
          cm.setFrom(me);
          cm.setPatchSet(updatedPatchSet, info);
          cm.addReviewers(recipients.getReviewers());
          cm.addExtraCC(recipients.getCcOnly());
          cm.send();
        } catch (Exception e) {
          log.error("Cannot send email for new change " + updatedChange.getId(), e);
        }
      } else {
        final List<PatchSetApproval> patchSetApprovals =
            db.patchSetApprovals().byChange(updatedChange.getId()).toList();
        final MailRecipients oldRecipients =
            getRecipientsFromApprovals(patchSetApprovals);
        approvalsUtil.addReviewers(db, labelTypes, updatedChange, updatedPatchSet, info,
            recipients.getReviewers(), oldRecipients.getAll());
        final ChangeMessage msg =
            new ChangeMessage(new ChangeMessage.Key(updatedChange.getId(),
                ChangeUtil.messageUUID(db)), me,
                updatedPatchSet.getCreatedOn(), updatedPatchSet.getId());
        msg.setMessage("Uploaded patch set " + updatedPatchSet.getPatchSetId() + ".");
        try {
          ReplacePatchSetSender cm = replacePatchSetFactory.create(updatedChange);
          cm.setFrom(me);
          cm.setPatchSet(updatedPatchSet, info);
          cm.setChangeMessage(msg);
          cm.addReviewers(recipients.getReviewers());
          cm.addExtraCC(recipients.getCcOnly());
          cm.send();
        } catch (Exception e) {
          log.error("Cannot send email for new patch set " + updatedPatchSet.getId(), e);
        }
      }
    } finally {
      git.close();
    }
  }
}
