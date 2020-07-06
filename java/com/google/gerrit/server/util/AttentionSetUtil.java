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

package com.google.gerrit.server.util;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.AttentionSetUpdate.Operation;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.mail.send.AddToAttentionSetSender;
import com.google.gerrit.server.mail.send.AttentionSetSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.RemoveFromAttentionSetSender;
import com.google.gerrit.server.update.Context;
import java.util.Collection;

/** Common helpers for dealing with attention set data structures. */
public class AttentionSetUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Returns only updates where the user was added. */
  public static ImmutableSet<AttentionSetUpdate> additionsOnly(
      Collection<AttentionSetUpdate> updates) {
    return updates.stream()
        .filter(u -> u.operation() == Operation.ADD)
        .collect(ImmutableSet.toImmutableSet());
  }

  /**
   * Validates the input for AttentionSetInput. This must be called for all inputs that relate to
   * adding or removing attention set entries, except for {@link
   * com.google.gerrit.server.restapi.change.RemoveFromAttentionSet}.
   */
  public static void validateInput(AttentionSetInput input) throws BadRequestException {
    input.user = Strings.nullToEmpty(input.user).trim();
    if (input.user.isEmpty()) {
      throw new BadRequestException("missing field: user");
    }
    input.reason = Strings.nullToEmpty(input.reason).trim();
    if (input.reason.isEmpty()) {
      throw new BadRequestException("missing field: reason");
    }
  }

  /**
   * Sends an email when adding users to the attention set or removing them from it.
   *
   * @param sender sender in charge of sending the email, can be {@link AddToAttentionSetSender} or
   *     {@link RemoveFromAttentionSetSender}.
   * @param ctx context for sending the email.
   * @param change the change that the user was added/removed in.
   * @param reason reason for adding/removing the user.
   * @param messageId messageId for tracking the email.
   * @param attentionUserId the user added/removed.
   */
  public static void sendEmail(
      AttentionSetSender sender,
      Context ctx,
      Change change,
      String reason,
      MessageIdGenerator.MessageId messageId,
      Account.Id attentionUserId) {
    try {
      AccountState accountState =
          ctx.getUser().isIdentifiedUser() ? ctx.getUser().asIdentifiedUser().state() : null;
      if (accountState != null) {
        sender.setFrom(accountState.account().id());
      }
      sender.setNotify(ctx.getNotify(change.getId()));
      sender.setAttentionSetUser(attentionUserId);
      sender.setReason(reason);
      sender.setMessageId(messageId);
      sender.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot email update for change %s", change.getId());
    }
  }

  private AttentionSetUtil() {}
}
