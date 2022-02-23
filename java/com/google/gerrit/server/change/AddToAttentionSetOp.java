// Copyright (C) 2020 The Android Open Source Project
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

import static java.util.Objects.requireNonNull;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.extensions.events.AttentionSetObserver;
import com.google.gerrit.server.mail.send.AddToAttentionSetSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.util.AttentionSetEmail;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/** Add a specified user to the attention set. */
public class AddToAttentionSetOp implements BatchUpdateOp {
  public interface Factory {
    AddToAttentionSetOp create(Account.Id attentionUserId, String reason, boolean notify);
  }

  private final ChangeData.Factory changeDataFactory;
  private final AddToAttentionSetSender.Factory addToAttentionSetSender;
  private final AttentionSetEmail.Factory attentionSetEmailFactory;
  private final AttentionSetObserver attentionSetObserver;

  private final Account.Id attentionUserId;
  private final String reason;

  private Change change;
  private boolean notify;

  /**
   * Add a specified user to the attention set.
   *
   * @param attentionUserId the id of the user we want to add to the attention set.
   * @param reason the reason for adding that user.
   * @param notify whether or not to send emails if the operation is successful.
   */
  @Inject
  AddToAttentionSetOp(
      ChangeData.Factory changeDataFactory,
      AddToAttentionSetSender.Factory addToAttentionSetSender,
      AttentionSetEmail.Factory attentionSetEmailFactory,
      AttentionSetObserver attentionSetObserver,
      @Assisted Account.Id attentionUserId,
      @Assisted String reason,
      @Assisted boolean notify) {
    this.changeDataFactory = changeDataFactory;
    this.addToAttentionSetSender = addToAttentionSetSender;
    this.attentionSetEmailFactory = attentionSetEmailFactory;
    this.attentionSetObserver = attentionSetObserver;

    this.attentionUserId = requireNonNull(attentionUserId, "user");
    this.reason = requireNonNull(reason, "reason");
    this.notify = notify;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException {
    ChangeData changeData = changeDataFactory.create(ctx.getNotes());
    if (changeData.attentionSet().stream()
        .anyMatch(
            u ->
                u.account().equals(attentionUserId)
                    && u.operation() == AttentionSetUpdate.Operation.ADD)) {
      // We still need to perform this update to ensure that we don't remove the user in a follow-up
      // operation, but no need to send an email about it.
      notify = false;
    }

    change = ctx.getChange();

    ChangeUpdate changeUpdate = ctx.getUpdate(ctx.getChange().currentPatchSetId());
    AttentionSetUpdate attentionUpdate =
        AttentionSetUpdate.createForWrite(
            attentionUserId, AttentionSetUpdate.Operation.ADD, reason);
    changeUpdate.addToPlannedAttentionSetUpdates(attentionUpdate);
    attentionSetObserver.fire(
        changeDataFactory.create(change), ctx.getAccount(), attentionUpdate, ctx.getWhen());
    return true;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    if (!notify) {
      return;
    }
    attentionSetEmailFactory
        .create(
            addToAttentionSetSender.create(ctx.getProject(), change.getId()),
            ctx,
            change,
            reason,
            attentionUserId)
        .sendAsync();
  }
}
