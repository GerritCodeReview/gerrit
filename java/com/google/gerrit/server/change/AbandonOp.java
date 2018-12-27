// Copyright (C) 2016 The Android Open Source Project
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
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.extensions.events.ChangeAbandoned;
import com.google.gerrit.server.mail.send.AbandonedSender;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;

public class AbandonOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AbandonedSender.Factory abandonedSenderFactory;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ChangeAbandoned changeAbandoned;

  private final ChangeNotes origNotes;
  private final String msgTxt;
  private final NotifyHandling notifyHandling;
  private final ListMultimap<RecipientType, Account.Id> accountsToNotify;
  private final AccountState accountState;

  private Change updatedChange;
  private PatchSet patchSet;
  private ChangeMessage message;

  public interface Factory {
    AbandonOp create(
        @Assisted ChangeNotes origNotes,
        @Assisted @Nullable AccountState accountState,
        @Assisted @Nullable String msgTxt,
        @Assisted NotifyHandling notifyHandling,
        @Assisted ListMultimap<RecipientType, Account.Id> accountsToNotify);
  }

  @Inject
  AbandonOp(
      AbandonedSender.Factory abandonedSenderFactory,
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      ChangeAbandoned changeAbandoned,
      @Assisted ChangeNotes origNotes,
      @Assisted @Nullable AccountState accountState,
      @Assisted @Nullable String msgTxt,
      @Assisted NotifyHandling notifyHandling,
      @Assisted ListMultimap<RecipientType, Account.Id> accountsToNotify) {
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.changeAbandoned = changeAbandoned;

    this.origNotes = origNotes;
    this.accountState = accountState;
    this.msgTxt = Strings.nullToEmpty(msgTxt);
    this.notifyHandling = notifyHandling;
    this.accountsToNotify = accountsToNotify;
  }

  @Nullable
  public Change getUpdatedChange() {
    return updatedChange;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws IOException {
    Branch.NameKey source = origNotes.getChange().getSource();
    if (source == null) {
      return;
    }
    // TODO(dborowitz): This is racy. This is a pretty strong use case for being able to modify the
    // repo from within updateChange.
    ctx.addRefUpdate(
        ObjectId.fromString(origNotes.getCurrentPatchSet().getRevision().get()),
        ObjectId.zeroId(),
        source.get());
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws OrmException, ResourceConflictException {
    updatedChange = ctx.getChange();
    PatchSet.Id psId = updatedChange.currentPatchSetId();
    ChangeUpdate update = ctx.getUpdate(psId);
    if (!updatedChange.getStatus().isOpen()) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(updatedChange));
    }
    patchSet = psUtil.get(ctx.getNotes(), psId);
    updatedChange.setStatus(Change.Status.ABANDONED);
    updatedChange.setLastUpdatedOn(ctx.getWhen());

    update.setStatus(updatedChange.getStatus());
    message = newMessage(ctx);
    cmUtil.addChangeMessage(update, message);
    return true;
  }

  private ChangeMessage newMessage(ChangeContext ctx) {
    StringBuilder msg = new StringBuilder();
    msg.append("Abandoned");
    if (!Strings.nullToEmpty(msgTxt).trim().isEmpty()) {
      msg.append("\n\n");
      msg.append(msgTxt.trim());
    }

    return ChangeMessagesUtil.newMessage(ctx, msg.toString(), ChangeMessagesUtil.TAG_ABANDON);
  }

  @Override
  public void postUpdate(Context ctx) throws OrmException {
    try {
      ReplyToChangeSender cm =
          abandonedSenderFactory.create(ctx.getProject(), updatedChange.getId());
      if (accountState != null) {
        cm.setFrom(accountState.getAccount().getId());
      }
      cm.setChangeMessage(message.getMessage(), ctx.getWhen());
      cm.setNotify(notifyHandling);
      cm.setAccountsToNotify(accountsToNotify);
      cm.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Cannot email update for change %s", updatedChange.getId());
    }
    changeAbandoned.fire(
        updatedChange, patchSet, accountState, msgTxt, ctx.getWhen(), notifyHandling);
  }
}
