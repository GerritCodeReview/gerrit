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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.reviewdb.client.Account;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Caches external IDs of all accounts.
 *
 * <p>On each cache access the SHA1 of the refs/meta/external-ids branch is read to verify that the
 * cache is up to date.
 */
interface ExternalIdCache {
  void onCreate(ObjectId oldNotesRev, ObjectId newNotesRev, Collection<ExternalId> extId)
      throws IOException;

  void onUpdate(ObjectId oldNotesRev, ObjectId newNotesRev, Collection<ExternalId> extId)
      throws IOException;

  void onReplace(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Account.Id accountId,
      Collection<ExternalId> toRemove,
      Collection<ExternalId> toAdd)
      throws IOException;

  void onReplaceByKeys(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Account.Id accountId,
      Collection<ExternalId.Key> toRemove,
      Collection<ExternalId> toAdd)
      throws IOException;

  void onReplaceByKeys(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Collection<ExternalId.Key> toRemove,
      Collection<ExternalId> toAdd)
      throws IOException;

  void onReplace(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Collection<ExternalId> toRemove,
      Collection<ExternalId> toAdd)
      throws IOException;

  void onRemove(ObjectId oldNotesRev, ObjectId newNotesRev, Collection<ExternalId> extId)
      throws IOException;

  void onRemoveByKeys(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Account.Id accountId,
      Collection<ExternalId.Key> extIdKeys)
      throws IOException;

  void onRemoveByKeys(
      ObjectId oldNotesRev, ObjectId newNotesRev, Collection<ExternalId.Key> extIdKeys)
      throws IOException;

  Set<ExternalId> byAccount(Account.Id accountId) throws IOException;

  ImmutableSetMultimap<Account.Id, ExternalId> allByAccount() throws IOException;

  ImmutableSetMultimap<String, ExternalId> byEmails(String... emails) throws IOException;

  ImmutableSetMultimap<String, ExternalId> allByEmail() throws IOException;

  default ImmutableSet<ExternalId> byEmail(String email) throws IOException {
    return byEmails(email).get(email);
  }

  default void onCreate(ObjectId oldNotesRev, ObjectId newNotesRev, ExternalId extId)
      throws IOException {
    onCreate(oldNotesRev, newNotesRev, Collections.singleton(extId));
  }

  default void onRemove(ObjectId oldNotesRev, ObjectId newNotesRev, ExternalId extId)
      throws IOException {
    onRemove(oldNotesRev, newNotesRev, Collections.singleton(extId));
  }

  default void onRemoveByKey(
      ObjectId oldNotesRev, ObjectId newNotesRev, Account.Id accountId, ExternalId.Key extIdKey)
      throws IOException {
    onRemoveByKeys(oldNotesRev, newNotesRev, accountId, Collections.singleton(extIdKey));
  }

  default void onUpdate(ObjectId oldNotesRev, ObjectId newNotesRev, ExternalId updatedExtId)
      throws IOException {
    onUpdate(oldNotesRev, newNotesRev, Collections.singleton(updatedExtId));
  }
}
