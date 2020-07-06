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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.mail.send.AddToAttentionSetSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Map;
import java.util.function.Function;

/** Add a specified user to the attention set. */
public class AddToAttentionSetOp implements BatchUpdateOp {

  public interface Factory {
    AddToAttentionSetOp create(Account.Id attentionUserId, String reason, boolean sendEmails);
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final ChangeData.Factory changeDataFactory;
  private final MessageIdGenerator messageIdGenerator;
  private final AddToAttentionSetSender.Factory addToAttentionSetSender;

  private final Account.Id attentionUserId;
  private final String reason;

  private Change change;
  private boolean sendEmails;

  /**
   * Add a specified user to the attention set.
   *
   * @param attentionUserId the id of the user we want to add to the attention set.
   * @param reason The reason for adding that user.
   */
  @Inject
  AddToAttentionSetOp(
      ChangeData.Factory changeDataFactory,
      AddToAttentionSetSender.Factory addToAttentionSetSender,
      MessageIdGenerator messageIdGenerator,
      @Assisted Account.Id attentionUserId,
      @Assisted String reason,
      @Assisted boolean sendEmails) {
    this.changeDataFactory = changeDataFactory;
    this.addToAttentionSetSender = addToAttentionSetSender;
    this.messageIdGenerator = messageIdGenerator;
    this.attentionUserId = requireNonNull(attentionUserId, "user");
    this.reason = requireNonNull(reason, "reason");
    this.sendEmails = sendEmails;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException {
    ChangeData changeData = changeDataFactory.create(ctx.getNotes());
    Map<Account.Id, AttentionSetUpdate> attentionMap =
        changeData.attentionSet().stream()
            .collect(toImmutableMap(AttentionSetUpdate::account, Function.identity()));
    AttentionSetUpdate existingEntry = attentionMap.get(attentionUserId);
    if (existingEntry != null && existingEntry.operation() == AttentionSetUpdate.Operation.ADD) {
      // We still need to perform this update to ensure that we don't remove the user in a follow-up
      // operation, but no need to send an email about it.
      sendEmails = false;
    }

    change = ctx.getChange();

    ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
    update.addToPlannedAttentionSetUpdates(
        AttentionSetUpdate.createForWrite(
            attentionUserId, AttentionSetUpdate.Operation.ADD, reason));
    return true;
  }

  @Override
  public void postUpdate(Context ctx) {
    if (!sendEmails) {
      return;
    }
    NotifyResolver.Result notify = ctx.getNotify(change.getId());
    try {
      AddToAttentionSetSender cm = addToAttentionSetSender.create(ctx.getProject(), change.getId());
      AccountState accountState =
          ctx.getUser().isIdentifiedUser() ? ctx.getUser().asIdentifiedUser().state() : null;
      if (accountState != null) {
        cm.setFrom(accountState.account().id());
      }
      cm.setNotify(notify);
      cm.setAttentionSetUser(attentionUserId);
      cm.setReason(reason);
      cm.setMessageId(
          messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), change.currentPatchSetId()));
      cm.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot email update for change %s", change.getId());
    }
  }
}
