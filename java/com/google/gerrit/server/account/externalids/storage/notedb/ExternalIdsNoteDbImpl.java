// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids.storage.notedb;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdCache;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Class to access external IDs.
 *
 * <p>The external IDs are either read from NoteDb or retrieved from the cache.
 */
@Singleton
public class ExternalIdsNoteDbImpl implements ExternalIds {
  private final ExternalIdReader externalIdReader;
  private final ExternalIdCache externalIdCache;
  private final AuthConfig authConfig;
  private final ExternalIdKeyFactory externalIdKeyFactory;

  @Inject
  ExternalIdsNoteDbImpl(
      ExternalIdReader externalIdReader,
      ExternalIdCache externalIdCache,
      ExternalIdKeyFactory externalIdKeyFactory,
      AuthConfig authConfig) {
    this.externalIdReader = externalIdReader;
    this.externalIdCache = externalIdCache;
    this.externalIdKeyFactory = externalIdKeyFactory;
    this.authConfig = authConfig;
  }

  @Override
  public ImmutableSet<ExternalId> all() throws IOException, ConfigInvalidException {
    return externalIdReader.all();
  }

  /** Returns all external IDs from the specified revision of the refs/meta/external-ids branch. */
  public ImmutableSet<ExternalId> all(ObjectId rev) throws IOException, ConfigInvalidException {
    return externalIdReader.all(rev);
  }

  @Override
  public Optional<ExternalId> get(ExternalId.Key key) throws IOException {
    Optional<ExternalId> externalId = Optional.empty();
    if (authConfig.isUserNameCaseInsensitiveMigrationMode()) {
      externalId =
          externalIdCache.byKey(externalIdKeyFactory.create(key.scheme(), key.id(), false));
    }
    if (!externalId.isPresent()) {
      externalId = externalIdCache.byKey(key);
    }
    return externalId;
  }

  @Override
  public ImmutableSet<ExternalId> byAccount(Account.Id accountId) throws IOException {
    return externalIdCache.byAccount(accountId);
  }

  @Override
  public ImmutableSet<ExternalId> byAccount(Account.Id accountId, String scheme)
      throws IOException {
    return byAccount(accountId).stream()
        .filter(e -> e.key().isScheme(scheme))
        .collect(toImmutableSet());
  }

  /** Returns the external IDs of the specified account. */
  public ImmutableSet<ExternalId> byAccount(Account.Id accountId, ObjectId rev) throws IOException {
    Optional<ExternalIdCacheImpl> cache =
        ExternalIdCacheImpl.asExternalIdCacheImpl(externalIdCache);
    if (cache.isEmpty()) {
      throw new IllegalStateException(
          "byAccount(Account.Id, ObjectId) should only be called with a revision-based cache.");
    }
    return cache.get().byAccount(accountId, rev);
  }

  @Override
  public ImmutableSetMultimap<Account.Id, ExternalId> allByAccount() throws IOException {
    return externalIdCache.allByAccount();
  }

  @Override
  public ImmutableSet<ExternalId> byEmail(String email) throws IOException {
    return externalIdCache.byEmail(email);
  }

  @Override
  public ImmutableSetMultimap<String, ExternalId> byEmails(String... emails) throws IOException {
    return externalIdCache.byEmails(emails);
  }
}
