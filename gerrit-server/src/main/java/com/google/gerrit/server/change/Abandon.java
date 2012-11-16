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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.Abandon.Input;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class Abandon implements RestModifyView<ChangeResource, Input> {
  private final ChangeHooks hooks;
  private final AbandonedSender.Factory abandonedSenderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson json;

  public static class Input {
    public String message;
  }

  @Inject
  Abandon(ChangeHooks hooks,
      AbandonedSender.Factory abandonedSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson json) {
    this.hooks = hooks;
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.dbProvider = dbProvider;
    this.json = json;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public Object apply(ChangeResource req, Input input)
      throws BadRequestException, AuthException,
      ResourceConflictException, Exception {
    ChangeControl control = req.getControl();
    Change change = req.getChange();
    if (!control.canAbandon()) {
      throw new AuthException("abandon not permitted");
    } else if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is " + status(change));
    }

    // Create a message to accompany the abandoned change
    ReviewDb db = dbProvider.get();
    PatchSet.Id patchSetId = change.currentPatchSetId();
    IdentifiedUser currentUser = (IdentifiedUser) control.getCurrentUser();
    String message = Strings.emptyToNull(input.message);
    ChangeMessage cmsg = new ChangeMessage(
        new ChangeMessage.Key(change.getId(), ChangeUtil.messageUUID(db)),
        currentUser.getAccountId(), patchSetId);
    StringBuilder msg = new StringBuilder();
    msg.append(String.format("Patch Set %d: Abandoned", patchSetId.get()));
    if (message != null) {
      msg.append("\n\n");
      msg.append(message);
    }
    cmsg.setMessage(msg.toString());

    // Abandon the change
    Change updatedChange = db.changes().atomicUpdate(
      change.getId(),
      new AtomicUpdate<Change>() {
        @Override
        public Change update(Change change) {
          if (change.getStatus().isOpen()) {
            change.setStatus(Change.Status.ABANDONED);
            ChangeUtil.updated(change);
            return change;
          }
          return null;
        }
      });
    if (updatedChange == null) {
      throw new ResourceConflictException("change is "
          + status(db.changes().get(change.getId())));
    }

    ChangeUtil.updatedChange(db, currentUser, updatedChange, cmsg,
                             abandonedSenderFactory);
    hooks.doChangeAbandonedHook(updatedChange, currentUser.getAccount(),
                                message, db);
    return json.format(change.getId());
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}
