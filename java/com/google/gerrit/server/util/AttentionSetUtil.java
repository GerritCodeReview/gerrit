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
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.AttentionSetUpdate.Operation;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AttentionSetInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.notedb.ChangeNotes;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Common helpers for dealing with attention set data structures. */
public class AttentionSetUtil {

  /** Returns only updates where the user was added. */
  public static ImmutableSet<AttentionSetUpdate> additionsOnly(
      Collection<AttentionSetUpdate> updates) {
    return updates.stream()
        .filter(u -> u.operation() == Operation.ADD)
        .collect(ImmutableSet.toImmutableSet());
  }

  /** Returns only updates where the user was removed. */
  public static ImmutableSet<AttentionSetUpdate> removalsOnly(
      Collection<AttentionSetUpdate> updates) {
    return updates.stream()
        .filter(u -> u.operation() == Operation.REMOVE)
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
   * Returns the {@code Account.Id} of {@code user} if the user is active on the change, and exists.
   * If the user doesn't exist or is not active on the change, the same exception is thrown to
   * disallow probing for account existence based on exception type.
   */
  public static Account.Id resolveAccount(
      AccountResolver accountResolver, ChangeNotes changeNotes, String user)
      throws ConfigInvalidException, IOException, BadRequestException {
    // We will throw this exception if the account doesn't exist, or if the account is not active.
    // This is purposely the same exception so that users can't probe for account existence based on
    // the thrown exception.
    BadRequestException possibleExceptionForNotFoundOrInactiveAccount =
        new BadRequestException(
            String.format(
                "%s doesn't exist or is not active on the change as an owner, uploader, "
                    + "reviewer, or cc so they can't be added to the attention set",
                user));
    Account.Id attentionUserId;
    try {
      attentionUserId = accountResolver.resolveIgnoreVisibility(user).asUnique().account().id();
    } catch (AccountResolver.UnresolvableAccountException ex) {
      possibleExceptionForNotFoundOrInactiveAccount.initCause(ex);
      throw possibleExceptionForNotFoundOrInactiveAccount;
    }
    if (!isActiveOnTheChange(changeNotes, attentionUserId)) {
      throw possibleExceptionForNotFoundOrInactiveAccount;
    }
    return attentionUserId;
  }

  /**
   * Returns whether {@code attentionUserId} is active on a change. Activity is defined as being a
   * part of the reviewers, an uploader, or an owner of a change.
   */
  private static boolean isActiveOnTheChange(ChangeNotes changeNotes, Account.Id attentionUserId) {
    return changeNotes.getChange().getOwner().equals(attentionUserId)
        || changeNotes.getCurrentPatchSet().uploader().equals(attentionUserId)
        || changeNotes.getReviewers().all().stream().anyMatch(id -> id.equals(attentionUserId));
  }

  /**
   * Returns {@link AttentionSetInfo} from {@link AttentionSetUpdate} with {@link AccountInfo}
   * fields filled by {@code accountLoader}.
   */
  public static AttentionSetInfo createAttentionSetInfo(
      AttentionSetUpdate attentionSetUpdate, AccountLoader accountLoader) {
    // Only one account is expected in attention set reason. If there are multiple, do not return
    // anything instead of failing the request.
    ImmutableSet<Account.Id> accountsInTemplate =
        AccountTemplateUtil.parseTemplates(attentionSetUpdate.reason());
    AccountInfo reasonAccount =
        accountsInTemplate.size() == 1
            ? accountLoader.get(Iterables.getOnlyElement(accountsInTemplate))
            : null;
    return new AttentionSetInfo(
        accountLoader.get(attentionSetUpdate.account()),
        Timestamp.from(attentionSetUpdate.timestamp()),
        attentionSetUpdate.reason(),
        reasonAccount);
  }

  private AttentionSetUtil() {}
}
