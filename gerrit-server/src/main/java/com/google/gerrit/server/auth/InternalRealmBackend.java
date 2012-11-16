// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.auth;

import com.google.common.base.Optional;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.auth.AuthUser.UUID;
import com.google.gerrit.server.auth.UserData.Builder;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Search user data in internal DB
 */
@Singleton
public class InternalRealmBackend implements RealmBackend {
  private AccountCache accountCache;

  @Inject
  InternalRealmBackend(AccountCache accountCache) {
    this.accountCache = accountCache;
  }

  @Override
  public boolean handles(UUID uuid) {
    return uuid.get().startsWith(AccountExternalId.SCHEME_GERRIT);
  }

  @Override
  public Optional<UserData> getUserData(AuthUser user) {
    AccountState accountState = accountCache.getByUsername(user.getUsername());
    if (accountState != null && accountState.getAccount() != null) {
      Account account = accountState.getAccount();
      Builder builder = new UserData.Builder(user.getUsername());
      builder.setDisplayName(account.getFullName());
      builder.addEmailAddress(account.getPreferredEmail());
      builder.setExternalId(user.getUUID().get());

      return Optional.of(builder.build());
    }
    return null;
  }

}
