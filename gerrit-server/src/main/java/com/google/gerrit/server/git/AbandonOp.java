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

package com.google.gerrit.server.git;

import com.google.common.base.Strings;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.extensions.events.ChangeAbandoned;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.mail.send.AbandonedSender;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbandonOp extends BatchUpdate.Op {
  private static final Logger log = LoggerFactory.getLogger(AbandonOp.class);

  private final AbandonedSender.Factory abandonedSenderFactory;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ChangeAbandoned changeAbandoned;

  private final String msgTxt;
  private final NotifyHandling notifyHandling;
  private final ListMultimap<RecipientType, Account.Id> accountsToNotify;
  private final Account account;

  private Change change;
  private PatchSet patchSet;
  private ChangeMessage message;

  public interface Factory {
    AbandonOp create(
        @Assisted @Nullable Account account,
        @Assisted @Nullable String msgTxt,
        @Assisted NotifyHandling notifyHandling,
        @Assisted ListMultimap<RecipientType, Account.Id> accountsToNotify);
  }

  @AssistedInject
  AbandonOp(
      AbandonedSender.Factory abandonedSenderFactory,
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      ChangeAbandoned changeAbandoned,
      @Assisted @Nullable Account account,
      @Assisted @Nullable String msgTxt,
      @Assisted NotifyHandling notifyHandling,
      @Assisted ListMultimap<RecipientType, Account.Id> accountsToNotify) {
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.changeAbandoned = changeAbandoned;

    this.account = account;
    this.msgTxt = Strings.nullToEmpty(msgTxt);
    this.notifyHandling = notifyHandling;
    this.accountsToNotify = accountsToNotify;
  }

  @Nullable
  public Change getChange() {
    return change;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws OrmException, ResourceConflictException {
    change = ctx.getChange();
    PatchSet.Id psId = change.currentPatchSetId();
    ChangeUpdate update = ctx.getUpdate(psId);
    if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is " + status(change));
    }
    patchSet = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
    change.setStatus(Change.Status.ABANDONED);
    change.setLastUpdatedOn(ctx.getWhen());

    update.setStatus(change.getStatus());
    message = newMessage(ctx);
    cmUtil.addChangeMessage(ctx.getDb(), update, message);
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
      ReplyToChangeSender cm = abandonedSenderFactory.create(ctx.getProject(), change.getId());
      if (account != null) {
        cm.setFrom(account.getId());
      }
      cm.setChangeMessage(message.getMessage(), ctx.getWhen());
      cm.setNotify(notifyHandling);
      cm.setAccountsToNotify(accountsToNotify);
      cm.send();
    } catch (Exception e) {
      log.error("Cannot email update for change " + change.getId(), e);
    }
    changeAbandoned.fire(change, patchSet, account, msgTxt, ctx.getWhen(), notifyHandling);
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}
