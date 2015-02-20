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
  private final ChangeJson json;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;

  @Inject
  Abandon(ChangeHooks hooks,
      AbandonedSender.Factory abandonedSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson json,
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
      final AbandonInput input) throws AuthException,
      RestApiException, UpdateException, OrmException {
    if (!req.getControl().canAbandon()) {
      throw new AuthException("abandon not permitted");
    }
    final Change.Id id = req.getChange().getId();
    final IdentifiedUser caller =
        (IdentifiedUser) req.getControl().getCurrentUser();
    final AtomicReference<Change> change = new AtomicReference<>();
    final AtomicReference<PatchSet> patchSet = new AtomicReference<>();
    final AtomicReference<ChangeMessage> message = new AtomicReference<>();

    try (BatchUpdate u = batchUpdateFactory.create(dbProvider.get(),
        req.getChange().getProject(), TimeUtil.nowTs())) {
      u.addChangeOp(req.getControl(), new ChangeOp() {
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

          ChangeMessage m = newMessage(input, update.getUser(), c);
          cmUtil.addChangeMessage(db, update, m);

          change.set(c);
          message.set(m);
          patchSet.set(db.patchSets().get(c.currentPatchSetId()));
        }
      });
      u.addPostOp(new Callable<Void>() {
        @Override
        public Void call() throws OrmException {
          // TODO(dborowitz): send email while indexing.
          Change c = change.get();
          try {
            ReplyToChangeSender cm = abandonedSenderFactory.create(c);
            cm.setFrom(caller.getAccountId());
            cm.setChangeMessage(message.get());
            cm.send();
          } catch (Exception e) {
            log.error("Cannot email update for change " + id, e);
          }
          hooks.doChangeAbandonedHook(c,
              caller.getAccount(),
              patchSet.get(),
              Strings.emptyToNull(input.message),
              dbProvider.get());
          return null;
        }
      });
      u.execute();
    }
    return json.format(change.get());
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

  private ChangeMessage newMessage(AbandonInput input, IdentifiedUser caller,
      Change change) throws OrmException {
    StringBuilder msg = new StringBuilder();
    msg.append("Abandoned");
    if (!Strings.nullToEmpty(input.message).trim().isEmpty()) {
      msg.append("\n\n");
      msg.append(input.message.trim());
    }

    ChangeMessage message = new ChangeMessage(
        new ChangeMessage.Key(
            change.getId(),
            ChangeUtil.messageUUID(dbProvider.get())),
        caller.getAccountId(),
        change.getLastUpdatedOn(),
        change.currentPatchSetId());
    message.setMessage(msg.toString());
    return message;
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}
