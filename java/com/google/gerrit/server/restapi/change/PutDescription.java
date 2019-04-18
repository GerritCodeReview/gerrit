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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.DescriptionInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PutDescription
    extends RetryingRestModifyView<RevisionResource, DescriptionInput, Response<String>>
    implements UiAction<RevisionResource> {
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;

  @Inject
  PutDescription(ChangeMessagesUtil cmUtil, RetryHelper retryHelper, PatchSetUtil psUtil) {
    super(retryHelper);
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
  }

  @Override
  protected Response<String> applyImpl(
      BatchUpdate.Factory updateFactory, RevisionResource rsrc, DescriptionInput input)
      throws UpdateException, RestApiException, PermissionBackendException {
    rsrc.permissions().check(ChangePermission.EDIT_DESCRIPTION);

    Op op = new Op(input != null ? input : new DescriptionInput(), rsrc.getPatchSet().getId());
    try (BatchUpdate u =
        updateFactory.create(rsrc.getChange().getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      u.addOp(rsrc.getChange().getId(), op);
      u.execute();
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
      oldDescription = Strings.nullToEmpty(psUtil.get(ctx.getNotes(), psId).getDescription());
      if (oldDescription.equals(newDescription)) {
        return false;
      }
      String summary;
      if (oldDescription.isEmpty()) {
        summary = "Description set to \"" + newDescription + "\"";
      } else if (newDescription.isEmpty()) {
        summary = "Description \"" + oldDescription + "\" removed";
      } else {
        summary = "Description changed to \"" + newDescription + "\"";
      }

      update.setPsDescription(newDescription);

      ChangeMessage cmsg =
          ChangeMessagesUtil.newMessage(
              psId, ctx.getUser(), ctx.getWhen(), summary, ChangeMessagesUtil.TAG_SET_DESCRIPTION);
      cmUtil.addChangeMessage(update, cmsg);
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
