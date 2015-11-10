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
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.ChangeAbandoned;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.ReplyToChangeSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

@Singleton
public class Abandon implements RestModifyView<ChangeResource, AbandonInput>,
    UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Abandon.class);

  private final AbandonedSender.Factory abandonedSenderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangeAbandoned changeAbandoned;

  @Inject
  Abandon(AbandonedSender.Factory abandonedSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory,
      ChangeAbandoned changeAbandoned) {
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.dbProvider = dbProvider;
    this.json = json;
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
    this.changeAbandoned = changeAbandoned;
  }

  @Override
  public ChangeInfo apply(ChangeResource req,
      final AbandonInput input)
      throws RestApiException, UpdateException, OrmException {
    ChangeControl control = req.getControl();
    IdentifiedUser caller = control.getUser().asIdentifiedUser();
    if (!control.canAbandon()) {
      throw new AuthException("abandon not permitted");
    }
    Change change = abandon(control, input.message, caller.getAccount());
    return json.create(ChangeJson.NO_OPTIONS).format(change);
  }

  public Change abandon(ChangeControl control,
      final String msgTxt, final Account account)
      throws RestApiException, UpdateException {
    Op op = new Op(msgTxt, account);
    Change c = control.getChange();
    try (BatchUpdate u = batchUpdateFactory.create(dbProvider.get(),
        c.getProject(), control.getUser(), TimeUtil.nowTs())) {
      u.addOp(c.getId(), op).execute();
    }
    return op.change;
  }

  private class Op extends BatchUpdate.Op {
    private final Account account;
    private final String msgTxt;

    private Change change;
    private PatchSet patchSet;
    private ChangeMessage message;

    private Op(String msgTxt, Account account) {
      this.account = account;
      this.msgTxt = msgTxt;
    }

    @Override
    public void updateChange(ChangeContext ctx) throws OrmException,
        ResourceConflictException {
      change = ctx.getChange();
      if (change == null || !change.getStatus().isOpen()) {
        throw new ResourceConflictException("change is " + status(change));
      } else if (change.getStatus() == Change.Status.DRAFT) {
        throw new ResourceConflictException(
            "draft changes cannot be abandoned");
      }
      patchSet = ctx.getDb().patchSets().get(change.currentPatchSetId());
      change.setStatus(Change.Status.ABANDONED);
      change.setLastUpdatedOn(ctx.getWhen());
      ctx.getDb().changes().update(Collections.singleton(change));

      message = newMessage(ctx.getDb());
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getChangeUpdate(), message);
    }

    private ChangeMessage newMessage(ReviewDb db) throws OrmException {
      StringBuilder msg = new StringBuilder();
      msg.append("Abandoned");
      if (!Strings.nullToEmpty(msgTxt).trim().isEmpty()) {
        msg.append("\n\n");
        msg.append(msgTxt.trim());
      }

      ChangeMessage message = new ChangeMessage(
          new ChangeMessage.Key(
              change.getId(),
              ChangeUtil.messageUUID(db)),
          account != null ? account.getId() : null,
          change.getLastUpdatedOn(),
          change.currentPatchSetId());
      message.setMessage(msg.toString());
      return message;
    }

    @Override
    public void postUpdate(Context ctx) throws OrmException {
      try {
        ReplyToChangeSender cm = abandonedSenderFactory.create(change.getId());
        if (account != null) {
          cm.setFrom(account.getId());
        }
        cm.setChangeMessage(message);
        cm.send();
      } catch (Exception e) {
        log.error("Cannot email update for change " + change.getId(), e);
      }
      changeAbandoned.fire(change, patchSet, account, msgTxt);
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    return new UiAction.Description()
      .setLabel("Abandon")
      .setTitle("Abandon the change")
      .setVisible(resource.getChange().getStatus().isOpen()
          && resource.getChange().getStatus() != Change.Status.DRAFT
          && resource.getControl().canAbandon());
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}
