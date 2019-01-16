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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.extensions.events.WorkInProgressStateChanged;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/* Set work in progress or ready for review state on a change */
public class WorkInProgressOp implements BatchUpdateOp {
  public static class Input {
    @Nullable public String message;

    @Nullable public NotifyHandling notify;

    public Input() {}

    public Input(String message) {
      this.message = message;
    }
  }

  public interface Factory {
    WorkInProgressOp create(boolean workInProgress, Input in);
  }

  private final ChangeMessagesUtil cmUtil;
  private final EmailReviewComments.Factory email;
  private final PatchSetUtil psUtil;
  private final boolean workInProgress;
  private final Input in;
  private final WorkInProgressStateChanged stateChanged;

  private boolean sendEmail = true;
  private Change change;
  private ChangeNotes notes;
  private PatchSet ps;
  private ChangeMessage cmsg;

  @Inject
  WorkInProgressOp(
      ChangeMessagesUtil cmUtil,
      EmailReviewComments.Factory email,
      PatchSetUtil psUtil,
      WorkInProgressStateChanged stateChanged,
      @Assisted boolean workInProgress,
      @Assisted Input in) {
    this.cmUtil = cmUtil;
    this.email = email;
    this.psUtil = psUtil;
    this.stateChanged = stateChanged;
    this.workInProgress = workInProgress;
    this.in = in;
  }

  public void suppressEmail() {
    this.sendEmail = false;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) {
    change = ctx.getChange();
    notes = ctx.getNotes();
    ps = psUtil.get(ctx.getNotes(), change.currentPatchSetId());
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    change.setWorkInProgress(workInProgress);
    if (!change.hasReviewStarted() && !workInProgress) {
      change.setReviewStarted(true);
    }
    change.setLastUpdatedOn(ctx.getWhen());
    update.setWorkInProgress(workInProgress);
    addMessage(ctx, update);
    return true;
  }

  private void addMessage(ChangeContext ctx, ChangeUpdate update) {
    Change c = ctx.getChange();
    StringBuilder buf =
        new StringBuilder(c.isWorkInProgress() ? "Set Work In Progress" : "Set Ready For Review");

    String m = Strings.nullToEmpty(in == null ? null : in.message).trim();
    if (!m.isEmpty()) {
      buf.append("\n\n");
      buf.append(m);
    }

    cmsg =
        ChangeMessagesUtil.newMessage(
            ctx,
            buf.toString(),
            c.isWorkInProgress()
                ? ChangeMessagesUtil.TAG_SET_WIP
                : ChangeMessagesUtil.TAG_SET_READY);

    cmUtil.addChangeMessage(update, cmsg);
  }

  @Override
  public void postUpdate(Context ctx) {
    stateChanged.fire(change, ps, ctx.getAccount(), ctx.getWhen());
    NotifyResolver.Result notify = ctx.getNotify(change.getId());
    if (workInProgress
        || notify.handling().compareTo(NotifyHandling.OWNER_REVIEWERS) < 0
        || !sendEmail) {
      return;
    }
    email
        .create(
            notify,
            notes,
            ps,
            ctx.getIdentifiedUser(),
            cmsg,
            ImmutableList.of(),
            cmsg.getMessage(),
            ImmutableList.of())
        .sendAsync();
  }
}
