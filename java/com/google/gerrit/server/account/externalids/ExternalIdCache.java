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
import com.google.gerrit.entities.Account;
import java.io.IOException;
import java.util.Optional;

/**
 * Caches external IDs of all accounts. Note that the granularity is "revision" only, so each update
 * will cache a new value containing <b>all</b> external IDs.
 *
 * <p>On each cache access the SHA1 of the refs/meta/external-ids branch is read to verify that the
 * cache is up to date.
 *
 * <p>All returned collections are unmodifiable.
 */
public interface ExternalIdCache {
  Optional<ExternalId> byKey(ExternalId.Key key) throws IOException;

  ImmutableSet<ExternalId> byAccount(Account.Id accountId) throws IOException;

  ImmutableSetMultimap<Account.Id, ExternalId> allByAccount() throws IOException;

  ImmutableSetMultimap<String, ExternalId> byEmails(String... emails) throws IOException;

  ImmutableSetMultimap<String, ExternalId> allByEmail() throws IOException;

  default ImmutableSet<ExternalId> byEmail(String email) throws IOException {
    return byEmails(email).get(email);
  }
}
