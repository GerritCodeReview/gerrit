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
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PutTopic.Input;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
public class PutTopic implements RestModifyView<ChangeResource, Input>,
    UiAction<ChangeResource> {
  private final Provider<ReviewDb> dbProvider;
  private final ChangeIndexer indexer;
  private final ChangeHooks hooks;
  private final ChangeUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;

  public static class Input {
    @DefaultInput
    public String topic;
  }

  @Inject
  PutTopic(Provider<ReviewDb> dbProvider, ChangeIndexer indexer,
      ChangeHooks hooks, ChangeUpdate.Factory updateFactory,
      ChangeMessagesUtil cmUtil) {
    this.dbProvider = dbProvider;
    this.indexer = indexer;
    this.hooks = hooks;
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
  }

  @Override
  public Response<String> apply(ChangeResource req, Input input)
      throws AuthException, OrmException, IOException {
    if (input == null) {
      input = new Input();
    }

    ChangeControl control = req.getControl();
    Change change = req.getChange();
    if (!control.canEditTopicName()) {
      throw new AuthException("changing topic not permitted");
    }

    ReviewDb db = dbProvider.get();
    final String newTopicName = Strings.nullToEmpty(input.topic);
    String oldTopicName = Strings.nullToEmpty(change.getTopic());
    if (!oldTopicName.equals(newTopicName)) {
      String summary;
      if (oldTopicName.isEmpty()) {
        summary = "Topic set to " + newTopicName;
      } else if (newTopicName.isEmpty()) {
        summary = "Topic " + oldTopicName + " removed";
      } else {
        summary = String.format(
            "Topic changed from %s to %s",
            oldTopicName, newTopicName);
      }

      IdentifiedUser currentUser = ((IdentifiedUser) control.getCurrentUser());
      ChangeMessage cmsg = new ChangeMessage(
          new ChangeMessage.Key(change.getId(), ChangeUtil.messageUUID(db)),
          currentUser.getAccountId(), TimeUtil.nowTs(),
          change.currentPatchSetId());
      cmsg.setMessage(summary);
      ChangeUpdate update;

      db.changes().beginTransaction(change.getId());
      try {
        change = db.changes().atomicUpdate(change.getId(),
          new AtomicUpdate<Change>() {
            @Override
            public Change update(Change change) {
              change.setTopic(Strings.emptyToNull(newTopicName));
              ChangeUtil.updated(change);
              return change;
            }
          });

        //TODO(yyonas): atomic update was not propagated
        update = updateFactory.create(control);
        cmUtil.addChangeMessage(db, update, cmsg);

        db.commit();
      } finally {
        db.rollback();
      }
      update.commit();
      indexer.index(db, change);
      hooks.doTopicChangedHook(change, currentUser.getAccount(),
          oldTopicName, db);
    }
    return Strings.isNullOrEmpty(newTopicName)
        ? Response.<String>none()
        : Response.ok(newTopicName);
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    return new UiAction.Description()
      .setLabel("Edit Topic")
      .setVisible(resource.getControl().canEditTopicName());
  }
}
