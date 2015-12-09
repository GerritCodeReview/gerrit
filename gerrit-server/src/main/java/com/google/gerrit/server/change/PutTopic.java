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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PutTopic.Input;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.Collections;

@Singleton
public class PutTopic implements RestModifyView<ChangeResource, Input>,
    UiAction<ChangeResource> {
  private final Provider<ReviewDb> dbProvider;
  private final ChangeHooks hooks;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;

  public static class Input {
    @DefaultInput
    public String topic;
  }

  @Inject
  PutTopic(Provider<ReviewDb> dbProvider,
      ChangeHooks hooks,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory) {
    this.dbProvider = dbProvider;
    this.hooks = hooks;
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  @Override
  public Response<String> apply(ChangeResource req, Input input)
      throws UpdateException, RestApiException {
    ChangeControl ctl = req.getControl();
    if (!ctl.canEditTopicName()) {
      throw new AuthException("changing topic not permitted");
    }

    Op op = new Op(ctl, input != null ? input : new Input());
    try (BatchUpdate u = batchUpdateFactory.create(dbProvider.get(),
        req.getChange().getProject(), ctl.getUser(), TimeUtil.nowTs())) {
      u.addOp(req.getId(), op);
      u.execute();
    }
    return Strings.isNullOrEmpty(op.newTopicName)
        ? Response.<String> none()
        : Response.ok(op.newTopicName);
  }

  private class Op extends BatchUpdate.Op {
    private final Input input;
    private final IdentifiedUser caller;

    private Change change;
    private String oldTopicName;
    private String newTopicName;

    public Op(ChangeControl ctl, Input input) {
      this.input = input;
      this.caller = ctl.getUser().asIdentifiedUser();
    }

    @Override
    public void updateChange(ChangeContext ctx) throws OrmException {
      change = ctx.getChange();
      newTopicName = Strings.nullToEmpty(input.topic);
      oldTopicName = Strings.nullToEmpty(change.getTopic());
      if (oldTopicName.equals(newTopicName)) {
        return;
      }
      String summary;
      if (oldTopicName.isEmpty()) {
        summary = "Topic set to " + newTopicName;
      } else if (newTopicName.isEmpty()) {
        summary = "Topic " + oldTopicName + " removed";
      } else {
        summary = String.format("Topic changed from %s to %s",
            oldTopicName, newTopicName);
      }
      change.setTopic(Strings.emptyToNull(newTopicName));
      ctx.getChangeUpdate().setTopic(change.getTopic());
      ChangeUtil.updated(change);
      ctx.getDb().changes().update(Collections.singleton(change));

      ChangeMessage cmsg = new ChangeMessage(
          new ChangeMessage.Key(
              change.getId(),
              ChangeUtil.messageUUID(ctx.getDb())),
          caller.getAccountId(), ctx.getWhen(),
          change.currentPatchSetId());
      cmsg.setMessage(summary);
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getChangeUpdate(), cmsg);
    }

    @Override
    public void postUpdate(Context ctx) throws OrmException {
      if (change != null) {
        hooks.doTopicChangedHook(change, caller.getAccount(),
            oldTopicName, ctx.getDb());
      }
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    return new UiAction.Description()
      .setLabel("Edit Topic")
      .setVisible(resource.getControl().canEditTopicName());
  }
}
