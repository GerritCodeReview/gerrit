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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.mail.send.ReadyForReviewSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gwtorm.server.OrmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Set work in progress or ready for review state on a change */
public class WorkInProgressOp implements BatchUpdateOp {
  private static final Logger log = LoggerFactory.getLogger(WorkInProgressOp.class);

  public static class Input {
    String message;

    public Input() {}

    public Input(String message) {
      this.message = message;
    }
  }

  private final ChangeMessagesUtil cmUtil;
  private final ReadyForReviewSender.Factory readyForReviewSenderFactory;
  private final boolean workInProgress;
  private final Input in;
  private Change change;
  private ChangeMessage cmsg;

  WorkInProgressOp(
      ChangeMessagesUtil cmUtil,
      ReadyForReviewSender.Factory readyForReviewSenderFactory,
      boolean workInProgress,
      Input in) {
    this.cmUtil = cmUtil;
    this.readyForReviewSenderFactory = readyForReviewSenderFactory;
    this.workInProgress = workInProgress;
    this.in = in;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws OrmException {
    change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    change.setWorkInProgress(workInProgress);
    if (!workInProgress) {
      change.setReviewStarted(true);
    }
    change.setLastUpdatedOn(ctx.getWhen());
    update.setWorkInProgress(workInProgress);
    addMessage(ctx, update);
    return true;
  }

  private void addMessage(ChangeContext ctx, ChangeUpdate update) throws OrmException {
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

    cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);
  }

  @Override
  public void postUpdate(Context ctx) throws Exception {
    if (workInProgress) {
      return;
    }

    try {
      ReadyForReviewSender cm =
          readyForReviewSenderFactory.create(ctx.getProject(), change.getId());
      cm.setFrom(ctx.getAccount().getId());
      cm.setChangeMessage(cmsg.getMessage(), cmsg.getWrittenOn());
      cm.send();
    } catch (Exception e) {
      log.error("Cannot send email for ready for review");
    }
  }
}
