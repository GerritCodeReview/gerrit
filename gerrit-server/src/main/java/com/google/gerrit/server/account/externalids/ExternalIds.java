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

import static java.util.stream.Collectors.toSet;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Class to access external IDs.
 *
 * <p>The external IDs are either read from NoteDb or retrieved from the cache.
 */
@Singleton
public class ExternalIds {
  private final ExternalIdReader externalIdReader;
  private final ExternalIdCache externalIdCache;

  @Inject
  public ExternalIds(ExternalIdReader externalIdReader, ExternalIdCache externalIdCache) {
    this.externalIdReader = externalIdReader;
    this.externalIdCache = externalIdCache;
  }

  /** Returns all external IDs. */
  public Set<ExternalId> all(ReviewDb db) throws IOException, OrmException {
    return externalIdReader.all(db);
  }

  /** Returns all external IDs from the specified revision of the refs/meta/external-ids branch. */
  public Set<ExternalId> all(ObjectId rev) throws IOException {
    return externalIdReader.all(rev);
  }

  /** Returns the specified external ID. */
  @Nullable
  public ExternalId get(ReviewDb db, ExternalId.Key key)
      throws IOException, ConfigInvalidException, OrmException {
    return externalIdReader.get(db, key);
  }

  /** Returns the specified external ID from the given revision. */
  @Nullable
  public ExternalId get(ExternalId.Key key, ObjectId rev)
      throws IOException, ConfigInvalidException {
    return externalIdReader.get(key, rev);
  }

  /** Returns the external IDs of the specified account. */
  public Set<ExternalId> byAccount(ReviewDb db, Account.Id accountId)
      throws IOException, OrmException {
    if (externalIdReader.readFromGit()) {
      return externalIdCache.byAccount(accountId);
    }

    return ExternalId.from(db.accountExternalIds().byAccount(accountId).toList());
  }

  /** Returns the external IDs of the specified account that have the given scheme. */
  public Set<ExternalId> byAccount(ReviewDb db, Account.Id accountId, String scheme)
      throws IOException, OrmException {
    return byAccount(db, accountId).stream().filter(e -> e.key().isScheme(scheme)).collect(toSet());
  }

  public Set<ExternalId> byEmail(ReviewDb db, String email) throws IOException, OrmException {
    if (externalIdReader.readFromGit()) {
      return externalIdCache.byEmail(email);
    }

    return ExternalId.from(db.accountExternalIds().byEmailAddress(email).toList());
  }
}
