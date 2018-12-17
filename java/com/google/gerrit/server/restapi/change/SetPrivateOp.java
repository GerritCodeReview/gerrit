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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.OrmException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.extensions.events.PrivateStateChanged;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class SetPrivateOp implements BatchUpdateOp {
  public static class Input {
    String message;

    public Input() {}

    public Input(String message) {
      this.message = message;
    }
  }

  public interface Factory {
    SetPrivateOp create(ChangeMessagesUtil cmUtil, boolean isPrivate, Input input);
  }

  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final boolean isPrivate;
  private final Input input;
  private final PrivateStateChanged privateStateChanged;

  private Change change;
  private PatchSet ps;

  @Inject
  SetPrivateOp(
      PrivateStateChanged privateStateChanged,
      PatchSetUtil psUtil,
      @Assisted ChangeMessagesUtil cmUtil,
      @Assisted boolean isPrivate,
      @Assisted Input input) {
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.isPrivate = isPrivate;
    this.input = input;
    this.privateStateChanged = privateStateChanged;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws ResourceConflictException, OrmException {
    change = ctx.getChange();
    ChangeNotes notes = ctx.getNotes();
    ps = psUtil.get(notes, change.currentPatchSetId());
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    change.setPrivate(isPrivate);
    change.setLastUpdatedOn(ctx.getWhen());
    update.setPrivate(isPrivate);
    addMessage(ctx, update);
    return true;
  }

  @Override
  public void postUpdate(Context ctx) {
    privateStateChanged.fire(change, ps, ctx.getAccount(), ctx.getWhen());
  }

  private void addMessage(ChangeContext ctx, ChangeUpdate update) {
    Change c = ctx.getChange();
    StringBuilder buf = new StringBuilder(c.isPrivate() ? "Set private" : "Unset private");

    String m = Strings.nullToEmpty(input == null ? null : input.message).trim();
    if (!m.isEmpty()) {
      buf.append("\n\n");
      buf.append(m);
    }

    ChangeMessage cmsg =
        ChangeMessagesUtil.newMessage(
            ctx,
            buf.toString(),
            c.isPrivate()
                ? ChangeMessagesUtil.TAG_SET_PRIVATE
                : ChangeMessagesUtil.TAG_UNSET_PRIVATE);
    cmUtil.addChangeMessage(update, cmsg);
  }
}
