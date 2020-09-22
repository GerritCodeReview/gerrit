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

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Change.Status;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.RestoreInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.extensions.events.ChangeRestored;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.gerrit.server.mail.send.RestoredSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.AsyncPostUpdateOp;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class Restore
    implements RestModifyView<ChangeResource, RestoreInput>, UiAction<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchUpdate.Factory updateFactory;
  private final RestoredSender.Factory restoredSenderFactory;
  private final ChangeJson.Factory json;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ChangeRestored changeRestored;
  private final ProjectCache projectCache;
  private final MessageIdGenerator messageIdGenerator;

  @Inject
  Restore(
      BatchUpdate.Factory updateFactory,
      RestoredSender.Factory restoredSenderFactory,
      ChangeJson.Factory json,
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      ChangeRestored changeRestored,
      ProjectCache projectCache,
      MessageIdGenerator messageIdGenerator) {
    this.updateFactory = updateFactory;
    this.restoredSenderFactory = restoredSenderFactory;
    this.json = json;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.changeRestored = changeRestored;
    this.projectCache = projectCache;
    this.messageIdGenerator = messageIdGenerator;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, RestoreInput input)
      throws RestApiException, UpdateException, PermissionBackendException, IOException {
    // Not allowed to restore if the current patch set is locked.
    psUtil.checkPatchSetNotLocked(rsrc.getNotes());

    rsrc.permissions().check(ChangePermission.RESTORE);
    projectCache
        .get(rsrc.getProject())
        .orElseThrow(illegalState(rsrc.getProject()))
        .checkStatePermitsWrite();

    Op op = new Op(input);
    try (BatchUpdate u =
        updateFactory.create(rsrc.getChange().getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      u.addOp(rsrc.getId(), op).execute();
    }
    return Response.ok(json.noOptions().format(op.change));
  }

  private class Op implements BatchUpdateOp, AsyncPostUpdateOp {
    private final RestoreInput input;

    private Change change;
    private PatchSet patchSet;
    private ChangeMessage message;

    private Op(RestoreInput input) {
      this.input = input;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws ResourceConflictException {
      change = ctx.getChange();
      if (change == null || !change.isAbandoned()) {
        throw new ResourceConflictException("change is " + ChangeUtil.status(change));
      }
      PatchSet.Id psId = change.currentPatchSetId();
      ChangeUpdate update = ctx.getUpdate(psId);
      patchSet = psUtil.get(ctx.getNotes(), psId);
      change.setStatus(Status.NEW);
      change.setLastUpdatedOn(ctx.getWhen());
      update.setStatus(change.getStatus());

      message = newMessage(ctx);
      cmUtil.addChangeMessage(update, message);
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
    public void postUpdate(Context ctx) {
      changeRestored.fire(
          change, patchSet, ctx.getAccount(), Strings.emptyToNull(input.message), ctx.getWhen());
    }

    @Override
    public void asyncPostUpdate(Context ctx) {
      try {
        ReplyToChangeSender emailSender =
            restoredSenderFactory.create(ctx.getProject(), change.getId());
        emailSender.setFrom(ctx.getAccountId());
        emailSender.setChangeMessage(message.getMessage(), ctx.getWhen());
        emailSender.setMessageId(
            messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), change.currentPatchSetId()));
        emailSender.send();
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Cannot email update for change %s", change.getId());
      }
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
    if (!change.isAbandoned()) {
      return description;
    }

    try {
      if (!projectCache.get(rsrc.getProject()).map(ProjectState::statePermitsRead).orElse(false)) {
        return description;
      }
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log(
          "Failed to check if project state permits write: %s", rsrc.getProject());
      return description;
    }

    try {
      if (psUtil.isPatchSetLocked(rsrc.getNotes())) {
        return description;
      }
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log(
          "Failed to check if the current patch set of change %s is locked", change.getId());
      return description;
    }

    boolean visible = rsrc.permissions().testOrFalse(ChangePermission.RESTORE);
    return description.setVisible(visible);
  }
}
