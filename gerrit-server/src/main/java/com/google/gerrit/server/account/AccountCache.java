// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;

import java.util.Set;

/** Caches important (but small) account state to avoid database hits. */
public interface AccountCache {
  // Accounts indexed by unique internal identity.

  public ListenableFuture<AccountState> get(Account.Id accountId);

  public ListenableFuture<Account> getAccount(Account.Id accountId);

  public ListenableFuture<Void> evictAsync(Account.Id accountId);


  // Accounts indexed by external identity (OpenID, HTTP/LDAP/SSH username).

  public ListenableFuture<AccountExternalId> get(AccountExternalId.Key key);

  public ListenableFuture<Void> evictAsync(AccountExternalId.Key id);


  // Accounts indexed by email address.

  public ListenableFuture<Set<Account.Id>> byEmail(String email);

  public ListenableFuture<Void> evictEmailAsync(String email);
}
