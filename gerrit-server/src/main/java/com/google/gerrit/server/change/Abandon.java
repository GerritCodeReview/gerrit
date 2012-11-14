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

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.extensions.restapi.InvalidApiCallException;
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

import org.kohsuke.args4j.Option;

public class Abandon implements RestModifyView<ChangeResource, Input> {
  private final ChangeHooks hooks;
  private final AbandonedSender.Factory abandonedSenderFactory;
  private final Provider<ReviewDb> dbProvider;

  @Option(name = "--message", aliases = {"-m"}, usage = "optional message to append to change")
  private String message;

  public static class Input {
    public String message;
  }

  @Inject
  Abandon(ChangeHooks hooks,
      AbandonedSender.Factory abandonedSenderFactory,
      Provider<ReviewDb> dbProvider) {
    this.hooks = hooks;
    this.abandonedSenderFactory = abandonedSenderFactory;
    this.dbProvider = dbProvider;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public Object apply(ChangeResource req, Input input)
      throws InvalidApiCallException, Exception {
    if (input != null && input.message != null) {
      message = input.message;
    }

    final ChangeControl control = req.getControl();
    final Change change = req.getChange();
    final ReviewDb db = dbProvider.get();
    final ReviewResult result = new ReviewResult();
    result.setChangeId(req.getChange().getId());

    if (!control.canAbandon()) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.ABANDON_NOT_PERMITTED));
      return result;
    }

    // Create a message to accompany the abandoned change
    PatchSet.Id patchSetId = change.currentPatchSetId();
    IdentifiedUser currentUser = (IdentifiedUser) control.getCurrentUser();
    final ChangeMessage cmsg = new ChangeMessage(
        new ChangeMessage.Key(change.getId(), ChangeUtil.messageUUID(db)),
        currentUser.getAccountId(), patchSetId);
    final StringBuilder msgBuf =
        new StringBuilder("Patch Set " + patchSetId.get() + ": Abandoned");
    if (message != null && message.length() > 0) {
      msgBuf.append("\n\n");
      msgBuf.append(message);
    }
    cmsg.setMessage(msgBuf.toString());

    // Abandon the change
    final Change updatedChange = db.changes().atomicUpdate(
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
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.CHANGE_IS_CLOSED));
      return result;
    }

    ChangeUtil.updatedChange(db, currentUser, updatedChange, cmsg,
                             abandonedSenderFactory);
    hooks.doChangeAbandonedHook(updatedChange, currentUser.getAccount(),
                                message, db);
    return result;
  }
}
