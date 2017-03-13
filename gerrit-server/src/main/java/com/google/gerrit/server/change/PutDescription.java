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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collections;

@Singleton
public class PutDescription
    implements RestModifyView<RevisionResource, PutDescription.Input>, UiAction<RevisionResource> {
  private final Provider<ReviewDb> dbProvider;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final PatchSetUtil psUtil;

  public static class Input {
    @DefaultInput public String description;
  }

  @Inject
  PutDescription(
      Provider<ReviewDb> dbProvider,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory,
      PatchSetUtil psUtil) {
    this.dbProvider = dbProvider;
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
    this.psUtil = psUtil;
  }

  @Override
  public Response<String> apply(RevisionResource rsrc, Input input)
      throws UpdateException, RestApiException {
    ChangeControl ctl = rsrc.getControl();
    if (!ctl.canEditDescription()) {
      throw new AuthException("changing description not permitted");
    }
    Op op = new Op(input != null ? input : new Input(), rsrc.getPatchSet().getId());
    try (BatchUpdate u =
        batchUpdateFactory.create(
            dbProvider.get(), rsrc.getChange().getProject(), ctl.getUser(), TimeUtil.nowTs())) {
      u.addOp(rsrc.getChange().getId(), op);
      u.execute();
    }
    return Strings.isNullOrEmpty(op.newDescription)
        ? Response.none()
        : Response.ok(op.newDescription);
  }

  private class Op implements BatchUpdateOp {
    private final Input input;
    private final PatchSet.Id psId;

    private String oldDescription;
    private String newDescription;

    Op(Input input, PatchSet.Id psId) {
      this.input = input;
      this.psId = psId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws OrmException {
      PatchSet ps = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
      ChangeUpdate update = ctx.getUpdate(psId);
      newDescription = Strings.nullToEmpty(input.description);
      oldDescription = Strings.nullToEmpty(ps.getDescription());
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

      ps.setDescription(newDescription);
      update.setPsDescription(newDescription);

      ctx.getDb().patchSets().update(Collections.singleton(ps));

      ChangeMessage cmsg =
          ChangeMessagesUtil.newMessage(
              psId, ctx.getUser(), ctx.getWhen(), summary, ChangeMessagesUtil.TAG_SET_DESCRIPTION);
      cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);
      return true;
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) {
    return new UiAction.Description()
        .setLabel("Edit Description")
        .setVisible(rsrc.getControl().canEditDescription());
  }
}
