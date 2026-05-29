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

import static com.google.gerrit.server.mail.EmailFactories.CHANGE_ABANDONED;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.extensions.events.ChangeAbandoned;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class AbandonOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final EmailFactories emailFactories;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ChangeAbandoned changeAbandoned;
  private final MessageIdGenerator messageIdGenerator;

  private final String msgTxt;
  private final AccountState accountState;

  private Change change;
  private PatchSet patchSet;
  private String mailMessage;

  public interface Factory {
    AbandonOp create(
        @Assisted @Nullable AccountState accountState, @Assisted @Nullable String msgTxt);
  }

  @Inject
  AbandonOp(
      EmailFactories emailFactories,
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      ChangeAbandoned changeAbandoned,
      MessageIdGenerator messageIdGenerator,
      @Assisted @Nullable AccountState accountState,
      @Assisted @Nullable String msgTxt) {
    this.emailFactories = emailFactories;
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
    mailMessage = cmUtil.setChangeMessage(ctx, commentMessage(), ChangeMessagesUtil.TAG_ABANDON);
    return true;
  }

  private String commentMessage() {
    StringBuilder msg = new StringBuilder();
    msg.append("Abandoned");
    if (!Strings.nullToEmpty(msgTxt).trim().isEmpty()) {
      msg.append("\n\n");
      msg.append(msgTxt.trim());
    }
    return msg.toString();
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    NotifyResolver.Result notify = ctx.getNotify(change.getId());
    try {
      ChangeEmail changeEmail =
          emailFactories.createChangeEmail(
              ctx.getProject(), change.getId(), emailFactories.createAbandonedChangeEmail());
      changeEmail.setChangeMessage(mailMessage, ctx.getWhen());
      OutgoingEmail email = emailFactories.createOutgoingEmail(CHANGE_ABANDONED, changeEmail);
      if (accountState != null) {
        email.setFrom(accountState.account().id());
      }
      email.setNotify(notify);
      email.setMessageId(messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), patchSet.id()));
      email.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot email update for change %s", change.getId());
    }
    changeAbandoned.fire(
        ctx.getChangeData(change),
        patchSet,
        accountState,
        msgTxt,
        ctx.getWhen(),
        notify.handling());
  }
}
