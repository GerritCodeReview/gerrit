// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.auth.ldap;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AutoAccountCreator;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class LdapAutoAccountCreator implements AutoAccountCreator {
  private final AccountCache accountCache;
  private final AccountManager accountManager;

  @Inject
  LdapAutoAccountCreator(AccountCache accountCache, AccountManager accountManager) {
    this.accountCache = accountCache;
    this.accountManager = accountManager;
  }

  @Override
  public Optional<Account.Id> createAccount(String userId) throws IOException, OrmException {
    if (!ExternalId.isValidUsername(userId)) {
      return Optional.empty();
    }

    try {
      AuthRequest req = AuthRequest.forUser(userId);
      req.setSkipAuthentication(true);
      return accountCache
          .get(accountManager.authenticate(req).getAccountId())
          .map(accountState -> accountState.getAccount().getId());
    } catch (AccountException e) {
      throw new OrmException(String.format("Failed to auto-create account for %s", userId), e);
    }
  }
}
