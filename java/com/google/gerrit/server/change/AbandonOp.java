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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.extensions.events.ChangeAbandoned;
import com.google.gerrit.server.mail.send.AbandonedSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.AsyncPostUpdateOp;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class AbandonOp implements BatchUpdateOp, AsyncPostUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AbandonedSender.Factory abandonedSenderFactory;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ChangeAbandoned changeAbandoned;
  private final MessageIdGenerator messageIdGenerator;

  private final String msgTxt;
  private final AccountState accountState;

  private Change change;
  private PatchSet patchSet;
  private ChangeMessage message;
  private NotifyResolver.Result notify;

  public interface Factory {
    AbandonOp create(
        @Assisted @Nullable AccountState accountState, @Assisted @Nullable String msgTxt);
  }

  @Inject
  AbandonOp(
      AbandonedSender.Factory abandonedSenderFactory,
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      ChangeAbandoned changeAbandoned,
      MessageIdGenerator messageIdGenerator,
      @Assisted @Nullable AccountState accountState,
      @Assisted @Nullable String msgTxt) {
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.changeAbandoned = changeAbandoned;
    this.messageIdGenerator = messageIdGenerator;

    this.accountState = accountState;
    this.msgTxt = Strings.nullToEmpty(msgTxt);
  }

  @Nullable
  public Change getChange() {
    return change;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws ResourceConflictException {
    change = ctx.getChange();
    PatchSet.Id psId = change.currentPatchSetId();
    ChangeUpdate update = ctx.getUpdate(psId);
    if (!change.isNew()) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    }
    patchSet = psUtil.get(ctx.getNotes(), psId);
    change.setStatus(Change.Status.ABANDONED);
    change.setLastUpdatedOn(ctx.getWhen());

    update.setStatus(change.getStatus());
    message = newMessage(ctx);
    cmUtil.addChangeMessage(update, message);
    notify = ctx.getNotify(change.getId());
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
  public void postUpdate(Context ctx) {
    changeAbandoned.fire(change, patchSet, accountState, msgTxt, ctx.getWhen(), notify.handling());
  }

  @Override
  public void asyncPostUpdate(Context ctx) {
    try {
      ReplyToChangeSender emailSender =
          abandonedSenderFactory.create(ctx.getProject(), change.getId());
      if (accountState != null) {
        emailSender.setFrom(accountState.account().id());
      }
      emailSender.setChangeMessage(message.getMessage(), ctx.getWhen());
      emailSender.setNotify(notify);
      emailSender.setMessageId(
          messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), patchSet.id()));
      emailSender.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot email update for change %s", change.getId());
    }
  }
}
