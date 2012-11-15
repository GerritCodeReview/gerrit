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
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.Restore.Input;
import com.google.gerrit.server.mail.RestoredSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class Restore implements RestModifyView<ChangeResource, Input> {
  private final ChangeHooks hooks;
  private final RestoredSender.Factory restoredSenderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson json;

  public static class Input {
    @DefaultInput
    public String message;
  }

  @Inject
  Restore(ChangeHooks hooks,
      RestoredSender.Factory restoredSenderFactory,
      Provider<ReviewDb> dbProvider,
      ChangeJson json) {
    this.hooks = hooks;
    this.restoredSenderFactory = restoredSenderFactory;
    this.dbProvider = dbProvider;
    this.json = json;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public Object apply(ChangeResource req, Input input)
      throws Exception {
    ChangeControl control = req.getControl();
    Change change = req.getChange();
    if (!control.canRestore()) {
      throw new AuthException("restore not permitted");
    } else if (change.getStatus() != Status.ABANDONED) {
      throw new ResourceConflictException("change is " + status(change));
    }

    // Create a message to accompany the restore change
    ReviewDb db = dbProvider.get();
    PatchSet.Id patchSetId = change.currentPatchSetId();
    IdentifiedUser currentUser = (IdentifiedUser) control.getCurrentUser();
    String message = Strings.emptyToNull(input.message);
    final ChangeMessage cmsg = new ChangeMessage(
        new ChangeMessage.Key(change.getId(), ChangeUtil.messageUUID(db)),
        currentUser.getAccountId(), patchSetId);
    StringBuilder msg = new StringBuilder();
    msg.append(String.format("Patch Set %d: Restored", patchSetId.get()));
    if (message != null) {
      msg.append("\n\n");
      msg.append(message);
    }
    cmsg.setMessage(msg.toString());

    // Restore the change
    final Change updatedChange = db.changes().atomicUpdate(
      change.getId(),
      new AtomicUpdate<Change>() {
        @Override
        public Change update(Change change) {
          if (change.getStatus() == Change.Status.ABANDONED) {
            change.setStatus(Change.Status.NEW);
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
                             restoredSenderFactory);
    hooks.doChangeRestoredHook(updatedChange, currentUser.getAccount(),
                                message, db);
    return json.format(change.getId());
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }
}
