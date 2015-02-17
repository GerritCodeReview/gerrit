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
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeOp;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.ReplyToChangeSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class Abandon implements RestModifyView<ChangeResource, AbandonInput>,
    UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Abandon.class);

  private final ChangeHooks hooks;
  private final AbandonedSender.Factory abandonedSenderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson json;
  private final ChangeIndexer indexer;
  private final ChangeUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;

  @Inject
  Abandon(ChangeHooks hooks,
      AbandonedSender.Factory abandonedSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson json,
      ChangeIndexer indexer,
      ChangeUpdate.Factory updateFactory,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory) {
    this.hooks = hooks;
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.dbProvider = dbProvider;
    this.json = json;
    this.indexer = indexer;
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  public AtomicReference<Change> apply(final ChangeResource req,
      final AbandonInput input, BatchUpdate u) throws AuthException,
      ResourceConflictException {

    final ChangeControl control = req.getControl();
    final IdentifiedUser caller = (IdentifiedUser) control.getCurrentUser();
    final Change change = req.getChange();
    if (!control.canAbandon()) {
      throw new AuthException("abandon not permitted");
    } else if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is " + status(change));
    } else if (change.getStatus() == Change.Status.DRAFT) {
      throw new ResourceConflictException("draft changes cannot be abandoned");
    }

    final AtomicReference<Change> updatedChange = new AtomicReference<>();

    u.addChangeOp(new ChangeOp(control) {
      @Override
      public void call(ReviewDb db, ChangeUpdate update) throws Exception {
        updatedChange.set(
          db.changes().atomicUpdate(
            change.getId(),
            new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
              if (change.getStatus().isOpen()) {
                change.setStatus(Change.Status.ABANDONED);
                ChangeUtil.updated(change);
                return change;
              }
              return null;
            }
          })
        );
        if (updatedChange.get() == null) {
          throw new ResourceConflictException("change is "
              + status(db.changes().get(req.getChange().getId())));
        }

        //TODO(yyonas): atomic update was not propagated
        update = updateFactory.create(control, change.getLastUpdatedOn());
        ChangeMessage message = newMessage(input, caller, change);
        cmUtil.addChangeMessage(db, update, message);

        update.commit();

        CheckedFuture<?, IOException> indexFuture =
            indexer.indexAsync(change.getId());
        try {
          ReplyToChangeSender cm = abandonedSenderFactory.create(change);
          cm.setFrom(caller.getAccountId());
          cm.setChangeMessage(message);
          cm.send();
        } catch (Exception e) {
          log.error("Cannot email update for change " + change.getChangeId(), e);
        }
        indexFuture.checkedGet();
        hooks.doChangeAbandonedHook(change,
            caller.getAccount(),
            dbProvider.get().patchSets().get(change.currentPatchSetId()),
            Strings.emptyToNull(input.message),
            dbProvider.get());
      }
    });

    return updatedChange;
  }

  @Override
  public ChangeInfo apply(final ChangeResource req, final AbandonInput input)
      throws AuthException, ResourceConflictException, OrmException,
      IOException {
    ReviewDb db = dbProvider.get();
    AtomicReference<Change> updatedChange;
    try (BatchUpdate u = batchUpdateFactory.create(
        db, req.getChange().getDest().getParentKey(),
        TimeUtil.nowTs())) {
      updatedChange = apply(req, input, u);
    }
    return json.format(updatedChange.get());
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
