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

package com.google.gerrit.server.git.validators;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

public class AccountValidator {

  private final Provider<IdentifiedUser> self;
  private final OutgoingEmailValidator emailValidator;
  private final ExternalIds externalIds;

  @Inject
  public AccountValidator(
      Provider<IdentifiedUser> self,
      OutgoingEmailValidator emailValidator,
      ExternalIds externalIds) {
    this.self = self;
    this.emailValidator = emailValidator;
    this.externalIds = externalIds;
  }

  public List<String> validate(
      Account.Id accountId, RevWalk rw, @Nullable ObjectId oldId, ObjectId newId)
      throws IOException {
    Account oldAccount = null;
    if (oldId != null && !ObjectId.zeroId().equals(oldId)) {
      try {
        oldAccount = loadAccount(accountId, rw, oldId);
      } catch (ConfigInvalidException e) {
        // ignore, maybe the new commit is repairing it now
      }
    }

    Account newAccount;
    try {
      newAccount = loadAccount(accountId, rw, newId);
    } catch (ConfigInvalidException e) {
      return ImmutableList.of(
          String.format(
              "commit '%s' has an invalid '%s' file for account '%s': %s",
              newId.name(), AccountConfig.ACCOUNT_CONFIG, accountId.get(), e.getMessage()));
    }

    List<String> messages = new ArrayList<>();
    if (accountId.equals(self.get().getAccountId()) && !newAccount.isActive()) {
      messages.add("cannot deactivate own account");
    }

    if (newAccount.getPreferredEmail() != null
        && (oldAccount == null
            || !newAccount.getPreferredEmail().equals(oldAccount.getPreferredEmail()))) {
      if (!emailValidator.isValid(newAccount.getPreferredEmail())) {
        messages.add(
            String.format(
                "invalid preferred email '%s' for account '%s'",
                newAccount.getPreferredEmail(), accountId.get()));
      }

      if (!externalIds
          .byAccount(accountId)
          .stream()
          .anyMatch(e -> newAccount.getPreferredEmail().equals(e.email()))) {
        messages.add(
            String.format(
                "account '%s' has no external ID for its preferred email '%s'",
                accountId.get(), newAccount.getPreferredEmail()));
      }
    }

    return ImmutableList.copyOf(messages);
  }

  private Account loadAccount(Account.Id accountId, RevWalk rw, ObjectId commit)
      throws IOException, ConfigInvalidException {
    rw.reset();
    AccountConfig accountConfig = new AccountConfig(null, accountId);
    accountConfig.load(rw, commit);
    return accountConfig.getAccount();
  }
}
