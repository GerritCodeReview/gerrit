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
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.extensions.events.ChangeRestored;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.gerrit.server.mail.send.RestoredSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Restore
    implements RestModifyView<ChangeResource, RestoreInput>, UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Restore.class);

  private final RestoredSender.Factory restoredSenderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangeRestored changeRestored;

  @Inject
  Restore(
      RestoredSender.Factory restoredSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      BatchUpdate.Factory batchUpdateFactory,
      ChangeRestored changeRestored) {
    this.restoredSenderFactory = restoredSenderFactory;
    this.dbProvider = dbProvider;
    this.json = json;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.batchUpdateFactory = batchUpdateFactory;
    this.changeRestored = changeRestored;
  }

  @Override
  public ChangeInfo apply(ChangeResource req, RestoreInput input)
      throws RestApiException, UpdateException, OrmException {
    ChangeControl ctl = req.getControl();
    if (!ctl.canRestore(dbProvider.get())) {
      throw new AuthException("restore not permitted");
    }

    Op op = new Op(input);
    try (BatchUpdate u =
        batchUpdateFactory.create(
            dbProvider.get(), req.getChange().getProject(), ctl.getUser(), TimeUtil.nowTs())) {
      u.addOp(req.getId(), op).execute();
    }
    return json.create(ChangeJson.NO_OPTIONS).format(op.change);
  }

  private class Op extends BatchUpdate.Op {
    private final RestoreInput input;

    private Change change;
    private PatchSet patchSet;
    private ChangeMessage message;

    private Op(RestoreInput input) {
      this.input = input;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws OrmException, ResourceConflictException {
      change = ctx.getChange();
      if (change == null || change.getStatus() != Status.ABANDONED) {
        throw new ResourceConflictException("change is " + status(change));
      }
      PatchSet.Id psId = change.currentPatchSetId();
      ChangeUpdate update = ctx.getUpdate(psId);
      patchSet = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
      change.setStatus(Status.NEW);
      change.setLastUpdatedOn(ctx.getWhen());
      update.setStatus(change.getStatus());

      message = newMessage(ctx);
      cmUtil.addChangeMessage(ctx.getDb(), update, message);
      return true;
    }

    private ChangeMessage newMessage(ChangeContext ctx) throws OrmException {
      StringBuilder msg = new StringBuilder();
      msg.append("Restored");
      if (!Strings.nullToEmpty(input.message).trim().isEmpty()) {
        msg.append("\n\n");
        msg.append(input.message.trim());
      }
      return ChangeMessagesUtil.newMessage(ctx, msg.toString(), ChangeMessagesUtil.TAG_RESTORE);
    }

    @Override
    public void postUpdate(Context ctx) throws OrmException {
      try {
        ReplyToChangeSender cm = restoredSenderFactory.create(ctx.getProject(), change.getId());
        cm.setFrom(ctx.getAccountId());
        cm.setChangeMessage(message.getMessage(), ctx.getWhen());
        cm.send();
      } catch (Exception e) {
        log.error("Cannot email update for change " + change.getId(), e);
      }
      changeRestored.fire(
          change, patchSet, ctx.getAccount(), Strings.emptyToNull(input.message), ctx.getWhen());
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
        .setVisible(resource.getChange().getStatus() == Status.ABANDONED && canRestore);
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}
