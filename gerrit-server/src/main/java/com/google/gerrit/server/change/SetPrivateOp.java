// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gwtorm.server.OrmException;

class SetPrivateOp implements BatchUpdateOp {
  private final ChangeMessagesUtil cmUtil;
  private final boolean isPrivate;

  SetPrivateOp(ChangeMessagesUtil cmUtil, boolean isPrivate) {
    this.cmUtil = cmUtil;
    this.isPrivate = isPrivate;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws ResourceConflictException, OrmException {
    Change change = ctx.getChange();
    if (change.getStatus() == Change.Status.MERGED) {
      throw new ResourceConflictException("change is merged");
    }
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    change.setPrivate(isPrivate);
    change.setLastUpdatedOn(ctx.getWhen());
    update.setPrivate(isPrivate);
    addMessage(ctx, update);
    return true;
  }

  private void addMessage(ChangeContext ctx, ChangeUpdate update) throws OrmException {
    Change c = ctx.getChange();
    ChangeMessage cmsg =
        ChangeMessagesUtil.newMessage(
            ctx,
            c.isPrivate() ? "Set private" : "Unset private",
            c.isPrivate()
                ? ChangeMessagesUtil.TAG_SET_PRIVATE
                : ChangeMessagesUtil.TAG_UNSET_PRIVATE);
    cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);
  }
}
