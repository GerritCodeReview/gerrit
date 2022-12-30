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

import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.FROM_ID;
import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.MESSAGE_ID;
import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.TIMESTAMP;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.converter.AccountIdProtoConverter;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyEntry;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyHandling;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.extensions.events.ChangeAbandoned;
import com.google.gerrit.server.mail.EmailTaskDispatcher;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;

public class AbandonOp implements BatchUpdateOp {
  // private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ChangeAbandoned changeAbandoned;
  private final MessageIdGenerator messageIdGenerator;
  private final EmailTaskDispatcher emailTaskDispatcher;

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
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      ChangeAbandoned changeAbandoned,
      MessageIdGenerator messageIdGenerator,
      EmailTaskDispatcher emailDispatcher,
      @Assisted @Nullable AccountState accountState,
      @Assisted @Nullable String msgTxt) {
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.changeAbandoned = changeAbandoned;
    this.messageIdGenerator = messageIdGenerator;
    this.emailTaskDispatcher = emailDispatcher;

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
  public void postUpdate(PostUpdateContext ctx) throws IOException {
    NotifyResolver.Result notify = ctx.getNotify(change.getId());
    String messageId = messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), patchSet.id()).id();
    EmailTask.Builder emailTaskBuilder =
        EmailTask.newBuilder()
            .setEventType(EmailTask.Type.ABANDON)
            .setProject(ProjectNameKeyProtoConverter.INSTANCE.toProto(ctx.getProject()))
            .setChangeId(ChangeIdProtoConverter.INSTANCE.toProto(change.getId()))
            .setNotifyInput(getNotify(notify))
            .setMessage(mailMessage)
            .addHeader(header(TIMESTAMP, String.valueOf(ctx.getWhen().toEpochMilli())))
            .addHeader(header(MESSAGE_ID, messageId));
    if (accountState != null) {
      emailTaskBuilder.addHeader(header(FROM_ID, accountState.account().id().toString()));
    }
    emailTaskDispatcher.dispatch(emailTaskBuilder.build());
    changeAbandoned.fire(
        ctx.getChangeData(change),
        patchSet,
        accountState,
        msgTxt,
        ctx.getWhen(),
        notify.handling());
  }

  private EmailTask.Header header(EmailTask.Header.HeaderName headerName, String value) {
    return EmailTask.Header.newBuilder().setName(headerName).setValue(value).build();
  }

  private NotifyInput getNotify(NotifyResolver.Result notify) {
    NotifyInput.Builder builder =
        NotifyInput.newBuilder()
            .setNotifyHandling(NotifyHandling.valueOf(notify.handling().name()));
    for (RecipientType recipientType : notify.accounts().keySet()) {
      notify.accounts().get(recipientType).stream()
          .forEach(
              a ->
                  builder.addNotifyEntry(
                      NotifyEntry.newBuilder()
                          .setAccount(AccountIdProtoConverter.INSTANCE.toProto(a))
                          .setRecipientType(EmailTask.RecipientType.valueOf(recipientType.name()))
                          .build()));
    }
    return builder.build();
  }
}
