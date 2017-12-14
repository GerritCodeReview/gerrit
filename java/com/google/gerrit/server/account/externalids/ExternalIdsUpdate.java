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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Runnables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/**
 * Updates externalIds in ReviewDb and NoteDb.
 *
 * <p>In NoteDb external IDs are stored in the All-Users repository in a Git Notes branch called
 * refs/meta/external-ids where the sha1 of the external ID is used as note name. Each note content
 * is a git config file that contains an external ID. It has exactly one externalId subsection with
 * an accountId and optionally email and password:
 *
 * <pre>
 * [externalId "username:jdoe"]
 *   accountId = 1003407
 *   email = jdoe@example.com
 *   password = bcrypt:4:LCbmSBDivK/hhGVQMfkDpA==:XcWn0pKYSVU/UJgOvhidkEtmqCp6oKB7
 * </pre>
 *
 * For NoteDb each method call results in one commit on refs/meta/external-ids branch.
 *
 * <p>On updating external IDs this class takes care to evict affected accounts from the account
 * cache and thus triggers reindex for them.
 */
public class ExternalIdsUpdate {
  /**
   * Factory to create an ExternalIdsUpdate instance for updating external IDs by the Gerrit server.
   *
   * <p>The Gerrit server identity will be used as author and committer for all commits that update
   * the external IDs.
   */
  @Singleton
  public static class Server {
    private final GitRepositoryManager repoManager;
    private final Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory;
    private final AccountCache accountCache;
    private final AllUsersName allUsersName;
    private final MetricMaker metricMaker;
    private final ExternalIds externalIds;
    private final ExternalIdCache externalIdCache;
    private final RetryHelper retryHelper;

    @Inject
    public Server(
        GitRepositoryManager repoManager,
        Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory,
        AccountCache accountCache,
        AllUsersName allUsersName,
        MetricMaker metricMaker,
        ExternalIds externalIds,
        ExternalIdCache externalIdCache,
        RetryHelper retryHelper) {
      this.repoManager = repoManager;
      this.metaDataUpdateServerFactory = metaDataUpdateServerFactory;
      this.accountCache = accountCache;
      this.allUsersName = allUsersName;
      this.metricMaker = metricMaker;
      this.externalIds = externalIds;
      this.externalIdCache = externalIdCache;
      this.retryHelper = retryHelper;
    }

    public ExternalIdsUpdate create() {
      return new ExternalIdsUpdate(
          repoManager,
          () -> metaDataUpdateServerFactory.get().create(allUsersName),
          accountCache,
          allUsersName,
          metricMaker,
          externalIds,
          externalIdCache,
          retryHelper);
    }
  }

  /**
   * Factory to create an ExternalIdsUpdate instance for updating external IDs by the Gerrit server.
   *
   * <p>Using this class no reindex will be performed for the affected accounts and they will also
   * not be evicted from the account cache.
   *
   * <p>The Gerrit server identity will be used as author and committer for all commits that update
   * the external IDs.
   */
  @Singleton
  public static class ServerNoReindex {
    private final GitRepositoryManager repoManager;
    private final Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory;
    private final AllUsersName allUsersName;
    private final MetricMaker metricMaker;
    private final ExternalIds externalIds;
    private final ExternalIdCache externalIdCache;
    private final RetryHelper retryHelper;

    @Inject
    public ServerNoReindex(
        GitRepositoryManager repoManager,
        Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory,
        AllUsersName allUsersName,
        MetricMaker metricMaker,
        ExternalIds externalIds,
        ExternalIdCache externalIdCache,
        RetryHelper retryHelper) {
      this.repoManager = repoManager;
      this.metaDataUpdateServerFactory = metaDataUpdateServerFactory;
      this.allUsersName = allUsersName;
      this.metricMaker = metricMaker;
      this.externalIds = externalIds;
      this.externalIdCache = externalIdCache;
      this.retryHelper = retryHelper;
    }

    public ExternalIdsUpdate create() {
      return new ExternalIdsUpdate(
          repoManager,
          () -> metaDataUpdateServerFactory.get().create(allUsersName),
          null,
          allUsersName,
          metricMaker,
          externalIds,
          externalIdCache,
          retryHelper);
    }
  }

  /**
   * Factory to create an ExternalIdsUpdate instance for updating external IDs by the current user.
   *
   * <p>The identity of the current user will be used as author for all commits that update the
   * external IDs. The Gerrit server identity will be used as committer.
   */
  @Singleton
  public static class User {
    private final GitRepositoryManager repoManager;
    private final Provider<MetaDataUpdate.User> metaDataUpdateUserFactory;
    private final AccountCache accountCache;
    private final AllUsersName allUsersName;
    private final MetricMaker metricMaker;
    private final ExternalIds externalIds;
    private final ExternalIdCache externalIdCache;
    private final RetryHelper retryHelper;

    @Inject
    public User(
        GitRepositoryManager repoManager,
        Provider<MetaDataUpdate.User> metaDataUpdateUserFactory,
        AccountCache accountCache,
        AllUsersName allUsersName,
        MetricMaker metricMaker,
        ExternalIds externalIds,
        ExternalIdCache externalIdCache,
        RetryHelper retryHelper) {
      this.repoManager = repoManager;
      this.metaDataUpdateUserFactory = metaDataUpdateUserFactory;
      this.accountCache = accountCache;
      this.allUsersName = allUsersName;
      this.metricMaker = metricMaker;
      this.externalIds = externalIds;
      this.externalIdCache = externalIdCache;
      this.retryHelper = retryHelper;
    }

    public ExternalIdsUpdate create() {
      return new ExternalIdsUpdate(
          repoManager,
          () -> metaDataUpdateUserFactory.get().create(allUsersName),
          accountCache,
          allUsersName,
          metricMaker,
          externalIds,
          externalIdCache,
          retryHelper);
    }
  }

  private final GitRepositoryManager repoManager;
  private final MetaDataUpdateFactory metaDataUpdateFactory;
  @Nullable private final AccountCache accountCache;
  private final AllUsersName allUsersName;
  private final ExternalIds externalIds;
  private final ExternalIdCache externalIdCache;
  private final RetryHelper retryHelper;
  private final Runnable afterReadRevision;
  private final Counter0 updateCount;

  private ExternalIdsUpdate(
      GitRepositoryManager repoManager,
      MetaDataUpdateFactory metaDataUpdateFactory,
      @Nullable AccountCache accountCache,
      AllUsersName allUsersName,
      MetricMaker metricMaker,
      ExternalIds externalIds,
      ExternalIdCache externalIdCache,
      RetryHelper retryHelper) {
    this(
        repoManager,
        metaDataUpdateFactory,
        accountCache,
        allUsersName,
        metricMaker,
        externalIds,
        externalIdCache,
        retryHelper,
        Runnables.doNothing());
  }

  @VisibleForTesting
  public ExternalIdsUpdate(
      GitRepositoryManager repoManager,
      MetaDataUpdateFactory metaDataUpdateFactory,
      @Nullable AccountCache accountCache,
      AllUsersName allUsersName,
      MetricMaker metricMaker,
      ExternalIds externalIds,
      ExternalIdCache externalIdCache,
      RetryHelper retryHelper,
      Runnable afterReadRevision) {
    this.repoManager = checkNotNull(repoManager, "repoManager");
    this.metaDataUpdateFactory = checkNotNull(metaDataUpdateFactory, "metaDataUpdateFactory");
    this.accountCache = accountCache;
    this.allUsersName = checkNotNull(allUsersName, "allUsersName");
    this.externalIds = checkNotNull(externalIds, "externalIds");
    this.externalIdCache = checkNotNull(externalIdCache, "externalIdCache");
    this.retryHelper = checkNotNull(retryHelper, "retryHelper");
    this.afterReadRevision = checkNotNull(afterReadRevision, "afterReadRevision");
    this.updateCount =
        metricMaker.newCounter(
            "notedb/external_id_update_count",
            new Description("Total number of external ID updates.").setRate().setUnit("updates"));
  }

  /**
   * Inserts a new external ID.
   *
   * <p>If the external ID already exists, the insert fails with {@link OrmDuplicateKeyException}.
   */
  public void insert(ExternalId extId) throws IOException, ConfigInvalidException, OrmException {
    insert(Collections.singleton(extId));
  }

  /**
   * Inserts new external IDs.
   *
   * <p>If any of the external ID already exists, the insert fails with {@link
   * OrmDuplicateKeyException}.
   */
  public void insert(Collection<ExternalId> extIds)
      throws IOException, ConfigInvalidException, OrmException {
    updateNoteMap(n -> n.insert(extIds));
  }

  /**
   * Inserts or updates an external ID.
   *
   * <p>If the external ID already exists, it is overwritten, otherwise it is inserted.
   */
  public void upsert(ExternalId extId) throws IOException, ConfigInvalidException, OrmException {
    upsert(Collections.singleton(extId));
  }

  /**
   * Inserts or updates external IDs.
   *
   * <p>If any of the external IDs already exists, it is overwritten. New external IDs are inserted.
   */
  public void upsert(Collection<ExternalId> extIds)
      throws IOException, ConfigInvalidException, OrmException {
    updateNoteMap(n -> n.upsert(extIds));
  }

  /**
   * Deletes an external ID.
   *
   * @throws IllegalStateException is thrown if there is an existing external ID that has the same
   *     key, but otherwise doesn't match the specified external ID.
   */
  public void delete(ExternalId extId) throws IOException, ConfigInvalidException, OrmException {
    delete(Collections.singleton(extId));
  }

  /**
   * Deletes external IDs.
   *
   * @throws IllegalStateException is thrown if there is an existing external ID that has the same
   *     key as any of the external IDs that should be deleted, but otherwise doesn't match the that
   *     external ID.
   */
  public void delete(Collection<ExternalId> extIds)
      throws IOException, ConfigInvalidException, OrmException {
    updateNoteMap(n -> n.delete(extIds));
  }

  /**
   * Delete an external ID by key.
   *
   * @throws IllegalStateException is thrown if the external ID does not belong to the specified
   *     account.
   */
  public void delete(Account.Id accountId, ExternalId.Key extIdKey)
      throws IOException, ConfigInvalidException, OrmException {
    delete(accountId, Collections.singleton(extIdKey));
  }

  /**
   * Delete external IDs by external ID key.
   *
   * @throws IllegalStateException is thrown if any of the external IDs does not belong to the
   *     specified account.
   */
  public void delete(Account.Id accountId, Collection<ExternalId.Key> extIdKeys)
      throws IOException, ConfigInvalidException, OrmException {
    updateNoteMap(n -> n.delete(accountId, extIdKeys));
  }

  /**
   * Delete external IDs by external ID key.
   *
   * <p>The external IDs are deleted regardless of which account they belong to.
   */
  public void deleteByKeys(Collection<ExternalId.Key> extIdKeys)
      throws IOException, ConfigInvalidException, OrmException {
    updateNoteMap(n -> n.deleteByKeys(extIdKeys));
  }

  /** Deletes all external IDs of the specified account. */
  public void deleteAll(Account.Id accountId)
      throws IOException, ConfigInvalidException, OrmException {
    delete(externalIds.byAccount(accountId));
  }

  /**
   * Replaces external IDs for an account by external ID keys.
   *
   * <p>Deletion of external IDs is done before adding the new external IDs. This means if an
   * external ID key is specified for deletion and an external ID with the same key is specified to
   * be added, the old external ID with that key is deleted first and then the new external ID is
   * added (so the external ID for that key is replaced).
   *
   * @throws IllegalStateException is thrown if any of the specified external IDs does not belong to
   *     the specified account.
   */
  public void replace(
      Account.Id accountId, Collection<ExternalId.Key> toDelete, Collection<ExternalId> toAdd)
      throws IOException, ConfigInvalidException, OrmException {
    updateNoteMap(n -> n.replace(accountId, toDelete, toAdd));
  }

  /**
   * Replaces external IDs for an account by external ID keys.
   *
   * <p>Deletion of external IDs is done before adding the new external IDs. This means if an
   * external ID key is specified for deletion and an external ID with the same key is specified to
   * be added, the old external ID with that key is deleted first and then the new external ID is
   * added (so the external ID for that key is replaced).
   *
   * <p>The external IDs are replaced regardless of which account they belong to.
   */
  public void replaceByKeys(Collection<ExternalId.Key> toDelete, Collection<ExternalId> toAdd)
      throws IOException, ConfigInvalidException, OrmException {
    updateNoteMap(n -> n.replaceByKeys(toDelete, toAdd));
  }

  /**
   * Replaces an external ID.
   *
   * @throws IllegalStateException is thrown if the specified external IDs belong to different
   *     accounts.
   */
  public void replace(ExternalId toDelete, ExternalId toAdd)
      throws IOException, ConfigInvalidException, OrmException {
    replace(Collections.singleton(toDelete), Collections.singleton(toAdd));
  }

  /**
   * Replaces external IDs.
   *
   * <p>Deletion of external IDs is done before adding the new external IDs. This means if an
   * external ID is specified for deletion and an external ID with the same key is specified to be
   * added, the old external ID with that key is deleted first and then the new external ID is added
   * (so the external ID for that key is replaced).
   *
   * @throws IllegalStateException is thrown if the specified external IDs belong to different
   *     accounts.
   */
  public void replace(Collection<ExternalId> toDelete, Collection<ExternalId> toAdd)
      throws IOException, ConfigInvalidException, OrmException {
    updateNoteMap(n -> n.replace(toDelete, toAdd));
  }

  private void updateNoteMap(ExternalIdUpdater updater)
      throws IOException, ConfigInvalidException, OrmException {
    retryHelper.<ExternalIdUpdater, Object>execute(
        updater,
        i -> {
          try (Repository repo = repoManager.openRepository(allUsersName)) {
            ExternalIdNotes extIdNotes =
                new ExternalIdNotes(externalIdCache, accountCache, repo)
                    .setAfterReadRevision(afterReadRevision)
                    .load();
            i.update(extIdNotes);
            try (MetaDataUpdate metaDataUpdate = metaDataUpdateFactory.create()) {
              extIdNotes.commit(metaDataUpdate);
            }
            updateCount.increment();
            return null;
          }
        });
  }

  @FunctionalInterface
  private static interface ExternalIdUpdater {
    void update(ExternalIdNotes extIdsNotes)
        throws IOException, ConfigInvalidException, OrmException;
  }

  @VisibleForTesting
  @FunctionalInterface
  public static interface MetaDataUpdateFactory {
    MetaDataUpdate create() throws IOException;
  }
}
