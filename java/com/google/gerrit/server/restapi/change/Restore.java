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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.PatchSetUtil.isPatchSetLocked;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.RestoreInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.extensions.events.ChangeRestored;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.gerrit.server.mail.send.RestoredSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Restore extends RetryingRestModifyView<ChangeResource, RestoreInput, ChangeInfo>
    implements UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Restore.class);

  private final RestoredSender.Factory restoredSenderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ChangeRestored changeRestored;
  private final ProjectCache projectCache;
  private final ApprovalsUtil approvalsUtil;

  @Inject
  Restore(
      RestoredSender.Factory restoredSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      RetryHelper retryHelper,
      ChangeRestored changeRestored,
      ProjectCache projectCache,
      ApprovalsUtil approvalsUtil) {
    super(retryHelper);
    this.restoredSenderFactory = restoredSenderFactory;
    this.dbProvider = dbProvider;
    this.json = json;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.changeRestored = changeRestored;
    this.projectCache = projectCache;
    this.approvalsUtil = approvalsUtil;
  }

  @Override
  protected ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, RestoreInput input)
      throws RestApiException, UpdateException, OrmException, PermissionBackendException,
          IOException {
    // Not allowed to restore if the current patch set is locked.
    if (isPatchSetLocked(
        approvalsUtil, projectCache, dbProvider.get(), rsrc.getNotes(), rsrc.getUser())) {
      throw new ResourceConflictException(
          String.format("The current patch set of change %s is locked", rsrc.getId()));
    }

    rsrc.permissions().database(dbProvider).check(ChangePermission.RESTORE);
    projectCache.checkedGet(rsrc.getProject()).checkStatePermitsWrite();

    Op op = new Op(input);
    try (BatchUpdate u =
        updateFactory.create(
            dbProvider.get(), rsrc.getChange().getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      u.addOp(rsrc.getId(), op).execute();
    }
    return json.noOptions().format(op.change);
  }

  private class Op implements BatchUpdateOp {
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
        throw new ResourceConflictException("change is " + ChangeUtil.status(change));
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

    private ChangeMessage newMessage(ChangeContext ctx) {
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
  public UiAction.Description getDescription(ChangeResource rsrc) {
    UiAction.Description description =
        new UiAction.Description()
            .setLabel("Restore")
            .setTitle("Restore the change")
            .setVisible(false);

    Change change = rsrc.getChange();
    if (change.getStatus() != Status.ABANDONED) {
      return description;
    }

    try {
      if (!projectCache.checkedGet(rsrc.getProject()).statePermitsWrite()) {
        return description;
      }
    } catch (IOException e) {
      log.error("Failed to check if project state permits write: " + rsrc.getProject(), e);
      return description;
    }

    try {
      if (isPatchSetLocked(
          approvalsUtil, projectCache, dbProvider.get(), rsrc.getNotes(), rsrc.getUser())) {
        return description;
      }
    } catch (OrmException | IOException e) {
      log.error(
          String.format(
              "Failed to check if the current patch set of change %s is locked", change.getId()),
          e);
      return description;
    }

    boolean visible = rsrc.permissions().database(dbProvider).testOrFalse(ChangePermission.RESTORE);
    return description.setVisible(visible);
  }
}
