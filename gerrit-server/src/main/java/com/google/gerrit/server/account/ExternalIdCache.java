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

import java.util.Collection;
import java.util.Collections;

/** Caches external ids of all accounts */
public interface ExternalIdCache {
  void onCreate(Iterable<ExternalId> extId);

  void onRemove(Iterable<ExternalId> extId);
  void onRemove(Account.Id accountId, Iterable<ExternalId.Key> extIdKeys);

  void onUpdate(Iterable<ExternalId> extId);

  void onReplace(Account.Id accountId, Iterable<ExternalId> toRemove,
      Iterable<ExternalId> toAdd);
  void onReplaceByKeys(Account.Id accountId, Iterable<ExternalId.Key> toRemove,
      Iterable<ExternalId> toAdd);

  Collection<ExternalId> byAccount(Account.Id accountId);
  Collection<ExternalId> byAccount(Account.Id accountId, String scheme);

  default void onCreate(ExternalId extId) {
    onCreate(Collections.singleton(extId));
  }

  default void onRemove(ExternalId extId) {
    onRemove(Collections.singleton(extId));
  }

  default void onRemove(Account.Id accountId, ExternalId.Key extIdKey) {
    onRemove(accountId, Collections.singleton(extIdKey));
  }

  default void onUpdate(ExternalId updatedExtId) {
    onUpdate(Collections.singleton(updatedExtId));
  }
}
