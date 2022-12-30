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

import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.FROM_ID;
import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.MESSAGE_ID;
import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.TIMESTAMP;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Change.Status;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.extensions.api.changes.RestoreInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.extensions.events.ChangeRestored;
import com.google.gerrit.server.mail.EmailTaskDispatcher;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
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
  private final EmailTaskDispatcher emailTaskDispatcher;
  private final ChangeJson.Factory json;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ChangeRestored changeRestored;
  private final ProjectCache projectCache;
  private final MessageIdGenerator messageIdGenerator;

  @Inject
  Restore(
      BatchUpdate.Factory updateFactory,
      EmailTaskDispatcher emailTaskDispatcher,
      ChangeJson.Factory json,
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      ChangeRestored changeRestored,
      ProjectCache projectCache,
      MessageIdGenerator messageIdGenerator) {
    this.updateFactory = updateFactory;
    this.emailTaskDispatcher = emailTaskDispatcher;
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
        updateFactory.create(rsrc.getChange().getProject(), rsrc.getUser(), TimeUtil.now())) {
      u.addOp(rsrc.getId(), op).execute();
    }
    return Response.ok(json.noOptions().format(op.change));
  }

  private class Op implements BatchUpdateOp {
    private final RestoreInput input;

    private Change change;
    private PatchSet patchSet;
    private String mailMessage;

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

      mailMessage = cmUtil.setChangeMessage(ctx, commentMessage(), ChangeMessagesUtil.TAG_RESTORE);
      return true;
    }

    private String commentMessage() {
      StringBuilder msg = new StringBuilder();
      msg.append("Restored");
      if (!Strings.nullToEmpty(input.message).trim().isEmpty()) {
        msg.append("\n\n");
        msg.append(input.message.trim());
      }
      return msg.toString();
    }

    @Override
    public void postUpdate(PostUpdateContext ctx) {
      try {
        String messageId =
            messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), change.currentPatchSetId()).id();
        emailTaskDispatcher.dispatch(
            EmailTask.newBuilder()
                .setEventType(EmailTask.Type.RESTORE)
                .setProject(ProjectNameKeyProtoConverter.INSTANCE.toProto(ctx.getProject()))
                .setChangeId(ChangeIdProtoConverter.INSTANCE.toProto(change.getId()))
                .addHeader(header(FROM_ID, ctx.getAccountId().toString()))
                .setMessage(mailMessage)
                .addHeader(header(TIMESTAMP, String.valueOf(ctx.getWhen().toEpochMilli())))
                .addHeader(header(MESSAGE_ID, messageId))
                .build());
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Cannot email update for change %s", change.getId());
      }
      changeRestored.fire(
          ctx.getChangeData(change),
          patchSet,
          ctx.getAccount(),
          Strings.emptyToNull(input.message),
          ctx.getWhen());
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) throws IOException {
    UiAction.Description description =
        new UiAction.Description()
            .setLabel("Restore")
            .setTitle("Restore the change")
            .setVisible(false);

    Change change = rsrc.getChange();
    if (!change.isAbandoned()) {
      return description;
    }
    if (!projectCache.get(rsrc.getProject()).map(ProjectState::statePermitsRead).orElse(false)) {
      return description;
    }
    if (psUtil.isPatchSetLocked(rsrc.getNotes())) {
      return description;
    }
    boolean visible = rsrc.permissions().testOrFalse(ChangePermission.RESTORE);
    return description.setVisible(visible);
  }

  private EmailTask.Header header(EmailTask.Header.HeaderName headerName, String value) {
    return EmailTask.Header.newBuilder().setName(headerName).setValue(value).build();
  }
}
