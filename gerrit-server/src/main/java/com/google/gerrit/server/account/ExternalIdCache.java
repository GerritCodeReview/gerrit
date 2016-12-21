// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;

import java.util.Collection;

/** Caches external ids of all accounts */
public interface ExternalIdCache {
  void onCreate(AccountExternalId extId);
  void onCreate(Iterable<AccountExternalId> extId);
  void remove(AccountExternalId extId);
  void remove(Iterable<AccountExternalId> extId);
  void remove(Account.Id accountId, AccountExternalId.Key extIdKey);
  void remove(Account.Id accountId, Iterable<AccountExternalId.Key> extIdKeys);
  void update(AccountExternalId extId);
  Collection<AccountExternalId> byAccount(Account.Id accountId);
}
