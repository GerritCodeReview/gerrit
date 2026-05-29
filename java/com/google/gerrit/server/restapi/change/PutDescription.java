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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.base.Strings;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.DescriptionInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PutDescription
    implements RestModifyView<RevisionResource, DescriptionInput>, UiAction<RevisionResource> {
  private final BatchUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;

  @Inject
  PutDescription(
      BatchUpdate.Factory updateFactory, ChangeMessagesUtil cmUtil, PatchSetUtil psUtil) {
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
  }

  @Override
  public Response<String> apply(RevisionResource rsrc, DescriptionInput input)
      throws UpdateException, RestApiException, PermissionBackendException {
    rsrc.permissions().check(ChangePermission.EDIT_DESCRIPTION);

    Op op = new Op(input != null ? input : new DescriptionInput(), rsrc.getPatchSet().id());
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (BatchUpdate u =
          updateFactory.create(rsrc.getChange().getProject(), rsrc.getUser(), TimeUtil.now())) {
        u.addOp(rsrc.getChange().getId(), op);
        u.execute();
      }
    }
    return Strings.isNullOrEmpty(op.newDescription)
        ? Response.none()
        : Response.ok(op.newDescription);
  }

  private class Op implements BatchUpdateOp {
    private final DescriptionInput input;
    private final PatchSet.Id psId;

    private String oldDescription;
    private String newDescription;

    Op(DescriptionInput input, PatchSet.Id psId) {
      this.input = input;
      this.psId = psId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) {
      ChangeUpdate update = ctx.getUpdate(psId);
      newDescription = Strings.nullToEmpty(input.description);
      oldDescription = psUtil.get(ctx.getNotes(), psId).description().orElse("");
      if (oldDescription.equals(newDescription)) {
        return false;
      }
      update.setPsDescription(newDescription);

      String summary;
      if (oldDescription.isEmpty()) {
        summary =
            String.format("Description of patch set %d set to \"%s\"", psId.get(), newDescription);
      } else if (newDescription.isEmpty()) {
        summary =
            String.format(
                "Description \"%s\" removed from patch set %d", oldDescription, psId.get());
      } else {
        summary =
            String.format(
                "Description of patch set %d changed to \"%s\"", psId.get(), newDescription);
      }
      cmUtil.setChangeMessage(update, summary, ChangeMessagesUtil.TAG_SET_DESCRIPTION);
      return true;
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) {
    return new UiAction.Description()
        .setLabel("Edit Description")
        .setVisible(rsrc.permissions().testCond(ChangePermission.EDIT_DESCRIPTION));
  }
}
