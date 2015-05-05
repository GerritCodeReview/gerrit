// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

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

public class PatchSetNotificationSender {
  private static final Logger log =
      LoggerFactory.getLogger(PatchSetNotificationSender.class);

  private final ReviewDb db;
  private final GitRepositoryManager repoManager;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ApprovalsUtil approvalsUtil;
  private final AccountResolver accountResolver;
  private final CreateChangeSender.Factory createChangeSenderFactory;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;

  @Inject
  public PatchSetNotificationSender(ReviewDb db,
      ChangeHooks hooks,
      GitRepositoryManager repoManager,
      PatchSetInfoFactory patchSetInfoFactory,
      ApprovalsUtil approvalsUtil,
      AccountResolver accountResolver,
      CreateChangeSender.Factory createChangeSenderFactory,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      ChangeIndexer indexer) {
    this.db = db;
    this.repoManager = repoManager;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.approvalsUtil = approvalsUtil;
    this.accountResolver = accountResolver;
    this.createChangeSenderFactory = createChangeSenderFactory;
    this.replacePatchSetFactory = replacePatchSetFactory;
  }

  public void send(final ChangeNotes notes, final ChangeUpdate update,
      final boolean newChange, final IdentifiedUser currentUser,
      final Change updatedChange, final PatchSet updatedPatchSet,
      final LabelTypes labelTypes)
      throws OrmException, IOException, PatchSetInfoNotAvailableException {
    final Repository git = repoManager.openRepository(updatedChange.getProject());
    try {
      final RevWalk revWalk = new RevWalk(git);
      final RevCommit commit;
      try {
        commit = revWalk.parseCommit(ObjectId.fromString(
            updatedPatchSet.getRevision().get()));
      } finally {
        revWalk.close();
      }
      final PatchSetInfo info = patchSetInfoFactory.get(commit, updatedPatchSet.getId());
      final List<FooterLine> footerLines = commit.getFooterLines();
      final Account.Id me = currentUser.getAccountId();
      final MailRecipients recipients =
          getRecipientsFromFooters(accountResolver, updatedPatchSet, footerLines);
      recipients.remove(me);

      if (newChange) {
        approvalsUtil.addReviewers(db, update, labelTypes, updatedChange,
            updatedPatchSet, info, recipients.getReviewers(),
            Collections.<Account.Id> emptySet());
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
        approvalsUtil.addReviewers(db, update, labelTypes, updatedChange,
            updatedPatchSet, info, recipients.getReviewers(),
            approvalsUtil.getReviewers(db, notes).values());
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
