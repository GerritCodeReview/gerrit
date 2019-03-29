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

import com.google.common.collect.SetMultimap;
import com.google.gerrit.reviewdb.client.Account;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Caches external IDs of all accounts.
 *
 * <p>On each cache access the SHA1 of the refs/meta/external-ids branch is read to verify that the
 * cache is up to date.
 *
 * <p>All returned collections are unmodifiable.
 */
interface ExternalIdCache {
  void onReplace(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Collection<ExternalId> toRemove,
      Collection<ExternalId> toAdd);

  Set<ExternalId> byAccount(Account.Id accountId) throws IOException;

  Set<ExternalId> byAccount(Account.Id accountId, ObjectId rev) throws IOException;

  SetMultimap<String, ExternalId> byEmails(String... emails) throws IOException;

  default Set<ExternalId> byEmail(String email) throws IOException {
    return byEmails(email).get(email);
  }
}
