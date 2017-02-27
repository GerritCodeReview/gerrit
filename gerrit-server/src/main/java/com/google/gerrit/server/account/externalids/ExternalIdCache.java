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

package com.google.gerrit.server.account.externalids;

import com.google.gerrit.reviewdb.client.Account;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

/** Caches external IDs of all accounts */
interface ExternalIdCache {
  void onCreate(ObjectId newNotesRev, Iterable<ExternalId> extId) throws IOException;

  void onUpdate(ObjectId newNotesRev, Iterable<ExternalId> extId) throws IOException;

  void onReplace(
      ObjectId newNotesRev,
      Account.Id accountId,
      Iterable<ExternalId> toRemove,
      Iterable<ExternalId> toAdd)
      throws IOException;

  void onReplaceByKeys(
      ObjectId newNotesRev,
      Account.Id accountId,
      Iterable<ExternalId.Key> toRemove,
      Iterable<ExternalId> toAdd)
      throws IOException;

  void onReplaceByKeys(
      ObjectId newNotesRev, Iterable<ExternalId.Key> toRemove, Iterable<ExternalId> toAdd)
      throws IOException;

  void onReplace(ObjectId newNotesRev, Iterable<ExternalId> toRemove, Iterable<ExternalId> toAdd)
      throws IOException;

  void onRemove(ObjectId newNotesRev, Iterable<ExternalId> extId) throws IOException;

  void onRemoveByKeys(
      ObjectId newNotesRev, Account.Id accountId, Iterable<ExternalId.Key> extIdKeys)
      throws IOException;

  void onRemoveByKeys(ObjectId newNotesRev, Iterable<ExternalId.Key> extIdKeys) throws IOException;

  Set<ExternalId> byAccount(Account.Id accountId) throws IOException;

  Set<ExternalId> byEmail(String email) throws IOException;

  default void onCreate(ObjectId newNotesRev, ExternalId extId) throws IOException {
    onCreate(newNotesRev, Collections.singleton(extId));
  }

  default void onRemove(ObjectId newNotesRev, ExternalId extId) throws IOException {
    onRemove(newNotesRev, Collections.singleton(extId));
  }

  default void onRemoveByKey(ObjectId newNotesRev, Account.Id accountId, ExternalId.Key extIdKey)
      throws IOException {
    onRemoveByKeys(newNotesRev, accountId, Collections.singleton(extIdKey));
  }

  default void onUpdate(ObjectId newNotesRev, ExternalId updatedExtId) throws IOException {
    onUpdate(newNotesRev, Collections.singleton(updatedExtId));
  }
}
