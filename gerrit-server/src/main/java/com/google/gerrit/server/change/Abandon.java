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
import com.google.gerrit.common.ChangeHooks;
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
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeOp;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.ReplyToChangeSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class Abandon implements RestModifyView<ChangeResource, AbandonInput>,
    UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Abandon.class);

  private final ChangeHooks hooks;
  private final AbandonedSender.Factory abandonedSenderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;

  @Inject
  Abandon(ChangeHooks hooks,
      AbandonedSender.Factory abandonedSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory) {
    this.hooks = hooks;
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.dbProvider = dbProvider;
    this.json = json;
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  @Override
  public ChangeInfo apply(ChangeResource req,
      final AbandonInput input)
      throws RestApiException, UpdateException, OrmException {
    ChangeControl control = req.getControl();
    IdentifiedUser caller = (IdentifiedUser) control.getCurrentUser();
    if (!control.canAbandon()) {
      throw new AuthException("abandon not permitted");
    }
    Change change = abandon(control, input.message, caller.getAccount());
    return json.create(ChangeJson.NO_OPTIONS).format(change);
  }

  public Change abandon(ChangeControl control,
      final String msgTxt, final Account acc) throws RestApiException,
      UpdateException {
    final Change.Id id = control.getChange().getId();
    final AtomicReference<Change> change = new AtomicReference<>();
    final AtomicReference<PatchSet> patchSet = new AtomicReference<>();
    final AtomicReference<ChangeMessage> message = new AtomicReference<>();

    try (BatchUpdate u = batchUpdateFactory.create(dbProvider.get(),
        control.getChange().getProject(), TimeUtil.nowTs())) {
      u.addChangeOp(new ChangeOp(control) {
        @Override
        public void call(ReviewDb db, ChangeUpdate update) throws OrmException,
            ResourceConflictException {
          Change c = db.changes().get(id);
          if (c == null || !c.getStatus().isOpen()) {
            throw new ResourceConflictException("change is " + status(c));
          } else if (c.getStatus() == Change.Status.DRAFT) {
            throw new ResourceConflictException(
                "draft changes cannot be abandoned");
          }
          c.setStatus(Change.Status.ABANDONED);
          ChangeUtil.updated(c);
          db.changes().update(Collections.singleton(c));

          ChangeMessage m = newMessage(
              msgTxt, acc != null ? acc.getId() : null, c);
          cmUtil.addChangeMessage(db, update, m);

          change.set(c);
          message.set(m);
          patchSet.set(db.patchSets().get(c.currentPatchSetId()));
        }
      });
      u.addPostOp(new Callable<Void>() {
        @Override
        public Void call() throws OrmException {
          Change c = change.get();
          try {
            ReplyToChangeSender cm = abandonedSenderFactory.create(id);
            if (acc != null) {
              cm.setFrom(acc.getId());
            }
            cm.setChangeMessage(message.get());
            cm.send();
          } catch (Exception e) {
            log.error("Cannot email update for change " + id, e);
          }
          hooks.doChangeAbandonedHook(c,
              acc,
              patchSet.get(),
              Strings.emptyToNull(msgTxt),
              dbProvider.get());
          return null;
        }
      });
      u.execute();
    }
    return change.get();
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

  private ChangeMessage newMessage(String msgTxt, Account.Id accId,
      Change change) throws OrmException {
    StringBuilder msg = new StringBuilder();
    msg.append("Abandoned");
    if (!Strings.nullToEmpty(msgTxt).trim().isEmpty()) {
      msg.append("\n\n");
      msg.append(msgTxt.trim());
    }

    ChangeMessage message = new ChangeMessage(
        new ChangeMessage.Key(
            change.getId(),
            ChangeUtil.messageUUID(dbProvider.get())),
        accId,
        change.getLastUpdatedOn(),
        change.currentPatchSetId());
    message.setMessage(msg.toString());
    return message;
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}
