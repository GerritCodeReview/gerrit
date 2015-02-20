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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
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
import com.google.gerrit.server.git.BatchUpdate.ChangeOp;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

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
      throws AuthException, UpdateException, RestApiException, OrmException,
      IOException {
    if (input == null) {
      input = new Input();
    }
    final String inputTopic = input.topic;

    ChangeControl control = req.getControl();
    if (!control.canEditTopicName()) {
      throw new AuthException("changing topic not permitted");
    }

    final Change.Id id = req.getChange().getId();
    final IdentifiedUser caller = (IdentifiedUser) control.getCurrentUser();
    final AtomicReference<Change> change = new AtomicReference<>();
    final AtomicReference<String> oldTopicName = new AtomicReference<>();
    final AtomicReference<String> newTopicName = new AtomicReference<>();

    try (BatchUpdate u = batchUpdateFactory.create(dbProvider.get(),
        req.getChange().getProject(), TimeUtil.nowTs())) {
      u.addChangeOp(new ChangeOp(req.getControl()) {
        @Override
        public void call(ReviewDb db, ChangeUpdate update) throws OrmException,
            ResourceConflictException {
          Change c = db.changes().get(id);
          String n = Strings.nullToEmpty(inputTopic);
          String o = Strings.nullToEmpty(c.getTopic());
          if (o.equals(n)) {
            return;
          }
          String summary;
          if (o.isEmpty()) {
            summary = "Topic set to " + n;
          } else if (n.isEmpty()) {
            summary = "Topic " + o + " removed";
          } else {
            summary = String.format("Topic changed from %s to %s", o, n);
          }
          c.setTopic(Strings.emptyToNull(n));
          ChangeUtil.updated(c);
          db.changes().update(Collections.singleton(c));

          ChangeMessage cmsg = new ChangeMessage(
              new ChangeMessage.Key(id, ChangeUtil.messageUUID(db)),
              // TODO(dborowitz): Use time from batch.
              caller.getAccountId(), TimeUtil.nowTs(),
              c.currentPatchSetId());
          cmsg.setMessage(summary);
          cmUtil.addChangeMessage(db, update, cmsg);

          change.set(c);
          oldTopicName.set(o);
          newTopicName.set(n);
        }
      });
      u.addPostOp(new Callable<Void>() {
        @Override
        public Void call() throws OrmException {
          Change c = change.get();
          if (c != null) {
            hooks.doTopicChangedHook(change.get(), caller.getAccount(),
                oldTopicName.get(), dbProvider.get());
          }
          return null;
        }
      });
      u.execute();
    }
    String n = newTopicName.get();
    return Strings.isNullOrEmpty(n) ? Response.<String> none() : Response.ok(n);
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    return new UiAction.Description()
      .setLabel("Edit Topic")
      .setVisible(resource.getControl().canEditTopicName());
  }
}
