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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.account.ExternalId.Key.toAccountExternalIdKeys;
import static com.google.gerrit.server.account.ExternalId.toAccountExternalIds;
import static java.util.stream.Collectors.toSet;

import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

// Updates externalIds in ReviewDb.
public class ExternalIdsUpdate {
  /**
   * Factory to create an ExternalIdsUpdate instance for updating external IDs by the Gerrit server.
   */
  @Singleton
  public static class Server {
    private final AccountCache accountCache;

    @Inject
    public Server(AccountCache accountCache) {
      this.accountCache = accountCache;
    }

    public ExternalIdsUpdate create() {
      return new ExternalIdsUpdate(accountCache);
    }
  }

  @Singleton
  public static class User {
    private final AccountCache accountCache;

    @Inject
    public User(AccountCache accountCache) {
      this.accountCache = accountCache;
    }

    public ExternalIdsUpdate create() {
      return new ExternalIdsUpdate(accountCache);
    }
  }

  @VisibleForTesting
  public static RetryerBuilder<Void> retryerBuilder() {
    return RetryerBuilder.<Void>newBuilder()
        .retryIfException(e -> e instanceof LockFailureException)
        .withWaitStrategy(
            WaitStrategies.join(
                WaitStrategies.exponentialWait(2, TimeUnit.SECONDS),
                WaitStrategies.randomWait(50, TimeUnit.MILLISECONDS)))
        .withStopStrategy(StopStrategies.stopAfterDelay(10, TimeUnit.SECONDS));
  }

  private final AccountCache accountCache;

  @VisibleForTesting
  public ExternalIdsUpdate(AccountCache accountCache) {
    this.accountCache = accountCache;
  }

  /**
   * Inserts a new external ID.
   *
   * <p>If the external ID already exists, the insert fails with {@link OrmDuplicateKeyException}.
   */
  public void insert(ReviewDb db, ExternalId extId) throws IOException, OrmException {
    insert(db, Collections.singleton(extId));
  }

  /**
   * Inserts new external IDs.
   *
   * <p>If any of the external ID already exists, the insert fails with {@link
   * OrmDuplicateKeyException}.
   */
  public void insert(ReviewDb db, Collection<ExternalId> extIds) throws IOException, OrmException {
    db.accountExternalIds().insert(toAccountExternalIds(extIds));
    evictAccounts(extIds);
  }

  /**
   * Inserts or updates an external ID.
   *
   * <p>If the external ID already exists, it is overwritten, otherwise it is inserted.
   */
  public void upsert(ReviewDb db, ExternalId extId) throws IOException, OrmException {
    upsert(db, Collections.singleton(extId));
  }

  /**
   * Inserts or updates external IDs.
   *
   * <p>If any of the external IDs already exists, it is overwritten. New external IDs are inserted.
   */
  public void upsert(ReviewDb db, Collection<ExternalId> extIds) throws IOException, OrmException {
    db.accountExternalIds().upsert(toAccountExternalIds(extIds));
    evictAccounts(extIds);
  }

  /**
   * Deletes an external ID.
   *
   * <p>The deletion fails with {@link IllegalStateException} if there is an existing external ID
   * that has the same key, but otherwise doesn't match the specified external ID.
   */
  public void delete(ReviewDb db, ExternalId extId) throws IOException, OrmException {
    delete(db, Collections.singleton(extId));
  }

  /**
   * Deletes external IDs.
   *
   * <p>The deletion fails with {@link IllegalStateException} if there is an existing external ID
   * that has the same key as any of the external IDs that should be deleted, but otherwise doesn't
   * match the that external ID.
   */
  public void delete(ReviewDb db, Collection<ExternalId> extIds) throws IOException, OrmException {
    db.accountExternalIds().delete(toAccountExternalIds(extIds));
    evictAccounts(extIds);
  }

  /**
   * Delete an external ID by key.
   *
   * <p>The external ID is only deleted if it belongs to the specified account. If it belongs to
   * another account the deletion fails with {@link IllegalStateException}.
   */
  public void delete(ReviewDb db, Account.Id accountId, ExternalId.Key extIdKey)
      throws IOException, OrmException {
    delete(db, accountId, Collections.singleton(extIdKey));
  }

  /**
   * Delete external IDs by external ID key.
   *
   * <p>The external IDs are only deleted if they belongs to the specified account. If any of the
   * external IDs belongs to another account the deletion fails with {@link IllegalStateException}.
   */
  public void delete(ReviewDb db, Account.Id accountId, Collection<ExternalId.Key> extIdKeys)
      throws IOException, OrmException {
    db.accountExternalIds().deleteKeys(toAccountExternalIdKeys(extIdKeys));
    accountCache.evict(accountId);
  }

  /** Deletes all external IDs of the specified account. */
  public void deleteAll(ReviewDb db, Account.Id accountId) throws IOException, OrmException {
    delete(db, ExternalId.from(db.accountExternalIds().byAccount(accountId).toList()));
  }

  /**
   * Replaces external IDs for an account by external ID keys.
   *
   * <p>Deletion of external IDs is done before adding the new external IDs. This means if an
   * external ID key is specified for deletion and an external ID with the same key is specified to
   * be added, the old external ID with that key is deleted first and then the new external ID is
   * added (so the external ID for that key is replaced).
   *
   * <p>If any of the specified external IDs belongs to another account the replacement fails with
   * {@link IllegalStateException}.
   */
  public void replace(
      ReviewDb db,
      Account.Id accountId,
      Collection<ExternalId.Key> toDelete,
      Collection<ExternalId> toAdd)
      throws IOException, OrmException {
    checkSameAccount(toAdd, accountId);

    db.accountExternalIds().deleteKeys(toAccountExternalIdKeys(toDelete));
    db.accountExternalIds().insert(toAccountExternalIds(toAdd));
    accountCache.evict(accountId);
  }

  /**
   * Replaces an external ID.
   *
   * <p>If the specified external IDs belongs to different accounts the replacement fails with
   * {@link IllegalStateException}.
   */
  public void replace(ReviewDb db, ExternalId toDelete, ExternalId toAdd)
      throws IOException, OrmException {
    replace(db, Collections.singleton(toDelete), Collections.singleton(toAdd));
  }

  /**
   * Replaces external IDs.
   *
   * <p>Deletion of external IDs is done before adding the new external IDs. This means if an
   * external ID is specified for deletion and an external ID with the same key is specified to be
   * added, the old external ID with that key is deleted first and then the new external ID is added
   * (so the external ID for that key is replaced).
   *
   * <p>If the specified external IDs belong to different accounts the replacement fails with {@link
   * IllegalStateException}.
   */
  public void replace(ReviewDb db, Collection<ExternalId> toDelete, Collection<ExternalId> toAdd)
      throws IOException, OrmException {
    Account.Id accountId = checkSameAccount(Iterables.concat(toDelete, toAdd));
    if (accountId == null) {
      // toDelete and toAdd are empty -> nothing to do
      return;
    }

    replace(db, accountId, toDelete.stream().map(e -> e.key()).collect(toSet()), toAdd);
  }

  /**
   * Checks that all specified external IDs belong to the same account.
   *
   * @return the ID of the account to which all specified external IDs belong.
   */
  public static Account.Id checkSameAccount(Iterable<ExternalId> extIds) {
    return checkSameAccount(extIds, null);
  }

  /**
   * Checks that all specified external IDs belong to specified account. If no account is specified
   * it is checked that all specified external IDs belong to the same account.
   *
   * @return the ID of the account to which all specified external IDs belong.
   */
  public static Account.Id checkSameAccount(
      Iterable<ExternalId> extIds, @Nullable Account.Id accountId) {
    for (ExternalId extId : extIds) {
      if (accountId == null) {
        accountId = extId.accountId();
        continue;
      }
      checkState(
          accountId.equals(extId.accountId()),
          "external id %s belongs to account %s, expected account %s",
          extId.key().get(),
          extId.accountId().get(),
          accountId.get());
    }
    return accountId;
  }

  private void evictAccounts(Collection<ExternalId> extIds) throws IOException {
    for (Account.Id id : extIds.stream().map(ExternalId::accountId).collect(toSet())) {
      accountCache.evict(id);
    }
  }
}
