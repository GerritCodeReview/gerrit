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
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

/** Caches external ids of all accounts */
public interface ExternalIdCache {
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

  void onReplace(ObjectId newNotesRev, Iterable<ExternalId> toRemove, Iterable<ExternalId> toAdd)
      throws IOException;

  void onRemove(ObjectId newNotesRev, Iterable<ExternalId> extId) throws IOException;

  void onRemove(ObjectId newNotesRev, Account.Id accountId, Iterable<ExternalId.Key> extIdKeys)
      throws IOException;

  Collection<ExternalId> byAccount(Account.Id accountId) throws IOException;

  default void onCreate(ObjectId newNotesRev, ExternalId extId) throws IOException {
    onCreate(newNotesRev, Collections.singleton(extId));
  }

  default void onRemove(ObjectId newNotesRev, ExternalId extId) throws IOException {
    onRemove(newNotesRev, Collections.singleton(extId));
  }

  default void onRemove(ObjectId newNotesRev, Account.Id accountId, ExternalId.Key extIdKey)
      throws IOException {
    onRemove(newNotesRev, accountId, Collections.singleton(extIdKey));
  }

  default void onUpdate(ObjectId newNotesRev, ExternalId updatedExtId) throws IOException {
    onUpdate(newNotesRev, Collections.singleton(updatedExtId));
  }

  default Set<ExternalId> byAccount(Account.Id accountId, String scheme) throws IOException {
    return byAccount(accountId).stream().filter(e -> e.key().isScheme(scheme)).collect(toSet());
  }
}
