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
import com.google.gerrit.extensions.api.changes.RestoreInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.mail.ReplyToChangeSender;
import com.google.gerrit.server.mail.RestoredSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

@Singleton
public class Restore implements RestModifyView<ChangeResource, RestoreInput>,
    UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Restore.class);

  private final ChangeHooks hooks;
  private final RestoredSender.Factory restoredSenderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;

  @Inject
  Restore(ChangeHooks hooks,
      RestoredSender.Factory restoredSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory) {
    this.hooks = hooks;
    this.restoredSenderFactory = restoredSenderFactory;
    this.dbProvider = dbProvider;
    this.json = json;
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  @Override
  public ChangeInfo apply(ChangeResource req, RestoreInput input)
      throws RestApiException, UpdateException, OrmException {
    ChangeControl ctl = req.getControl();
    if (!ctl.canRestore(dbProvider.get())) {
      throw new AuthException("restore not permitted");
    }

    Op op = new Op(input);
    try (BatchUpdate u = batchUpdateFactory.create(dbProvider.get(),
        req.getChange().getProject(), ctl.getUser(), TimeUtil.nowTs())) {
      u.addOp(req.getId(), op).execute();
    }
    return json.create(ChangeJson.NO_OPTIONS).format(op.change);
  }

  private class Op extends BatchUpdate.Op {
    private final RestoreInput input;

    private Change change;
    private PatchSet patchSet;
    private ChangeMessage message;
    private IdentifiedUser caller;

    private Op(RestoreInput input) {
      this.input = input;
    }

    @Override
    public void updateChange(ChangeContext ctx) throws OrmException,
        ResourceConflictException {
      caller = ctx.getUser().asIdentifiedUser();
      change = ctx.getChange();
      if (change == null || change.getStatus() != Status.ABANDONED) {
        throw new ResourceConflictException("change is " + status(change));
      }
      patchSet = ctx.getDb().patchSets().get(change.currentPatchSetId());
      change.setStatus(Status.NEW);
      change.setLastUpdatedOn(ctx.getWhen());
      ctx.getDb().changes().update(Collections.singleton(change));

      message = newMessage(ctx.getDb());
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getChangeUpdate(), message);
    }

    private ChangeMessage newMessage(ReviewDb db) throws OrmException {
      StringBuilder msg = new StringBuilder();
      msg.append("Restored");
      if (!Strings.nullToEmpty(input.message).trim().isEmpty()) {
        msg.append("\n\n");
        msg.append(input.message.trim());
      }

      ChangeMessage message = new ChangeMessage(
          new ChangeMessage.Key(
              change.getId(),
              ChangeUtil.messageUUID(db)),
          caller.getAccountId(),
          change.getLastUpdatedOn(),
          change.currentPatchSetId());
      message.setMessage(msg.toString());
      return message;
    }

    @Override
    public void postUpdate(Context ctx) throws OrmException {
      try {
        ReplyToChangeSender cm = restoredSenderFactory.create(change.getId());
        cm.setFrom(caller.getAccountId());
        cm.setChangeMessage(message);
        cm.send();
      } catch (Exception e) {
        log.error("Cannot email update for change " + change.getId(), e);
      }
      hooks.doChangeRestoredHook(change,
          caller.getAccount(),
          patchSet,
          Strings.emptyToNull(input.message),
          ctx.getDb());
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    boolean canRestore = false;
    try {
      canRestore = resource.getControl().canRestore(dbProvider.get());
    } catch (OrmException e) {
      log.error("Cannot check canRestore status. Assuming false.", e);
    }
    return new UiAction.Description()
      .setLabel("Restore")
      .setTitle("Restore the change")
      .setVisible(resource.getChange().getStatus() == Status.ABANDONED
          && canRestore);
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}
