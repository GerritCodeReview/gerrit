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

import static java.util.stream.Collectors.toSet;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;

import java.util.Collection;
import java.util.Collections;

/** Caches external ids of all accounts */
public interface ExternalIdCache {
  void onCreate(Iterable<AccountExternalId> extId);
  void onRemove(Iterable<AccountExternalId> extId);
  void onRemove(Account.Id accountId,
      Iterable<AccountExternalId.Key> extIdKeys);
  void onUpdate(AccountExternalId extId);
  Collection<AccountExternalId> byAccount(Account.Id accountId);

  default void onCreate(AccountExternalId extId) {
    onCreate(Collections.singleton(extId));
  }

  default void onRemove(AccountExternalId extId) {
    onRemove(Collections.singleton(extId));
  }

  default void onRemove(Account.Id accountId, AccountExternalId.Key extIdKey) {
    onRemove(accountId, Collections.singleton(extIdKey));
  }

  default Collection<AccountExternalId> byAccount(Account.Id accountId,
      String scheme) {
    return byAccount(accountId).stream().filter(e -> e.isScheme(scheme))
        .collect(toSet());
  }
}
