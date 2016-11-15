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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.change.PutDescription.Input;
import com.google.gerrit.server.extensions.events.TopicEdited;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class PutDescription implements RestModifyView<RevisionResource, Input>,
    UiAction<RevisionResource> {
  private final Provider<ReviewDb> dbProvider;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final TopicEdited topicEdited;

  public static class Input {
    @DefaultInput
    public String description;
  }

  @Inject
  PutDescription(Provider<ReviewDb> dbProvider,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory,
      TopicEdited topicEdited) {
    this.dbProvider = dbProvider;
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
    this.topicEdited = topicEdited;
  }

  @Override
  public Response<String> apply(RevisionResource req, Input input)
      throws UpdateException, RestApiException {
    ChangeControl ctl = req.getControl();
    if (!ctl.canEditDescription()) {
      throw new AuthException("changing description not permitted");
    }
    Op op = new Op(input != null ? input : new Input(), req.getPatchSet());
    try (BatchUpdate u = batchUpdateFactory.create(dbProvider.get(),
        req.getChange().getProject(), ctl.getUser(), TimeUtil.nowTs())) {
      u.addOp(req.getChange().getId(), op);
      u.execute();
    }
    return Strings.isNullOrEmpty(op.newDescription)
        ? Response.<String> none()
        : Response.ok(op.newDescription);
  }

  private class Op extends BatchUpdate.Op {
    private final Input input;

    private Change change;
    private PatchSet ps;
    private String oldDescription;
    private String newDescription;

    Op(Input input, PatchSet ps) {
      this.input = input;
      this.ps = ps;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws OrmException {
      change = ctx.getChange();
      ChangeUpdate update = ctx.getUpdate(ps.getId());
      newDescription = Strings.nullToEmpty(input.description);
      oldDescription = Strings.nullToEmpty(ps.getDescription());
      if (oldDescription.equals(newDescription)) {
        return false;
      }
      String summary;
      if (oldDescription.isEmpty()) {
        summary = "Description set to " + newDescription;
      } else if (newDescription.isEmpty()) {
        summary = "Description " + oldDescription + " removed";
      } else {
        summary = String.format("Description changed from %s to %s",
            oldDescription, newDescription);
      }
      ps.setDescription(newDescription);
      update.setPsDescription(newDescription);

      ChangeMessage cmsg = ChangeMessagesUtil.newMessage(ctx.getDb(),
          ps.getId(), ctx.getUser(), ctx.getWhen(), summary,
          ChangeMessagesUtil.TAG_SET_DESCRIPTION);
      cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);
      return true;
    }

    /*
    @Override
    public void postUpdate(Context ctx) {
      if (change != null) {
        topicEdited.fire(change,
            ctx.getAccount(),
            oldDescription,
            ctx.getWhen());
      }
    }
    */
  }

  @Override
  public com.google.gerrit.extensions.webui.UiAction.Description getDescription(
      RevisionResource resource) {
    return new UiAction.Description()
        .setLabel("Edit Description")
        .setVisible(resource.getControl().canEditTopicName());
  }
}
