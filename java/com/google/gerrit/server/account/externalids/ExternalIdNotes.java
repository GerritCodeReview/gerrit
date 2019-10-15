// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.logging.CallerFinder;
import com.google.gerrit.server.update.RetryHelper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * {@link VersionedMetaData} subclass to update external IDs.
 *
 * <p>This is a low-level API. Read/write of external IDs should be done through {@link
 * com.google.gerrit.server.account.AccountsUpdate} or {@link
 * com.google.gerrit.server.account.AccountConfig}.
 *
 * <p>On load the note map from {@code refs/meta/external-ids} is read, but the external IDs are not
 * parsed yet (see {@link #onLoad()}).
 *
 * <p>After loading the note map callers can access single or all external IDs. Only now the
 * requested external IDs are parsed.
 *
 * <p>After loading the note map callers can stage various external ID updates (insert, upsert,
 * delete, replace).
 *
 * <p>On save the staged external ID updates are performed (see {@link #onSave(CommitBuilder)}).
 *
 * <p>After committing the external IDs a cache update can be requested which also reindexes the
 * accounts for which external IDs have been updated (see {@link #updateCaches()}).
 */
public class ExternalIdNotes extends VersionedMetaData {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int MAX_NOTE_SZ = 1 << 19;

  public interface ExternalIdNotesLoader {
    /**
     * Loads the external ID notes from the current tip of the {@code refs/meta/external-ids}
     * branch.
     *
     * @param allUsersRepo the All-Users repository
     */
    ExternalIdNotes load(Repository allUsersRepo) throws IOException, ConfigInvalidException;

    /**
     * Loads the external ID notes from the specified revision of the {@code refs/meta/external-ids}
     * branch.
     *
     * @param allUsersRepo the All-Users repository
     * @param rev the revision from which the external ID notes should be loaded, if {@code null}
     *     the external ID notes are loaded from the current tip, if {@link ObjectId#zeroId()} it's
     *     assumed that the {@code refs/meta/external-ids} branch doesn't exist and the loaded
     *     external IDs will be empty
     */
    ExternalIdNotes load(Repository allUsersRepo, @Nullable ObjectId rev)
        throws IOException, ConfigInvalidException;
  }

  @Singleton
  public static class Factory implements ExternalIdNotesLoader {
    private final ExternalIdCache externalIdCache;
    private final AccountCache accountCache;
    private final Provider<AccountIndexer> accountIndexer;
    private final MetricMaker metricMaker;
    private final AllUsersName allUsersName;

    @Inject
    Factory(
        ExternalIdCache externalIdCache,
        AccountCache accountCache,
        Provider<AccountIndexer> accountIndexer,
        MetricMaker metricMaker,
        AllUsersName allUsersName) {
      this.externalIdCache = externalIdCache;
      this.accountCache = accountCache;
      this.accountIndexer = accountIndexer;
      this.metricMaker = metricMaker;
      this.allUsersName = allUsersName;
    }

    @Override
    public ExternalIdNotes load(Repository allUsersRepo)
        throws IOException, ConfigInvalidException {
      return new ExternalIdNotes(
              externalIdCache,
              accountCache,
              accountIndexer,
              metricMaker,
              allUsersName,
              allUsersRepo)
          .load();
    }

    @Override
    public ExternalIdNotes load(Repository allUsersRepo, @Nullable ObjectId rev)
        throws IOException, ConfigInvalidException {
      return new ExternalIdNotes(
              externalIdCache,
              accountCache,
              accountIndexer,
              metricMaker,
              allUsersName,
              allUsersRepo)
          .load(rev);
    }
  }

  @Singleton
  public static class FactoryNoReindex implements ExternalIdNotesLoader {
    private final ExternalIdCache externalIdCache;
    private final MetricMaker metricMaker;
    private final AllUsersName allUsersName;

    @Inject
    FactoryNoReindex(
        ExternalIdCache externalIdCache, MetricMaker metricMaker, AllUsersName allUsersName) {
      this.externalIdCache = externalIdCache;
      this.metricMaker = metricMaker;
      this.allUsersName = allUsersName;
    }

    @Override
    public ExternalIdNotes load(Repository allUsersRepo)
        throws IOException, ConfigInvalidException {
      return new ExternalIdNotes(
              externalIdCache, null, null, metricMaker, allUsersName, allUsersRepo)
          .load();
    }

    @Override
    public ExternalIdNotes load(Repository allUsersRepo, @Nullable ObjectId rev)
        throws IOException, ConfigInvalidException {
      return new ExternalIdNotes(
              externalIdCache, null, null, metricMaker, allUsersName, allUsersRepo)
          .load(rev);
    }
  }

  /**
   * Loads the external ID notes for reading only. The external ID notes are loaded from the current
   * tip of the {@code refs/meta/external-ids} branch.
   *
   * @return read-only {@link ExternalIdNotes} instance
   */
  public static ExternalIdNotes loadReadOnly(AllUsersName allUsersName, Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    return new ExternalIdNotes(
            new DisabledExternalIdCache(),
            null,
            null,
            new DisabledMetricMaker(),
            allUsersName,
            allUsersRepo)
        .setReadOnly()
        .load();
  }

  /**
   * Loads the external ID notes for reading only. The external ID notes are loaded from the
   * specified revision of the {@code refs/meta/external-ids} branch.
   *
   * @param rev the revision from which the external ID notes should be loaded, if {@code null} the
   *     external ID notes are loaded from the current tip, if {@link ObjectId#zeroId()} it's
   *     assumed that the {@code refs/meta/external-ids} branch doesn't exist and the loaded
   *     external IDs will be empty
   * @return read-only {@link ExternalIdNotes} instance
   */
  public static ExternalIdNotes loadReadOnly(
      AllUsersName allUsersName, Repository allUsersRepo, @Nullable ObjectId rev)
      throws IOException, ConfigInvalidException {
    return new ExternalIdNotes(
            new DisabledExternalIdCache(),
            null,
            null,
            new DisabledMetricMaker(),
            allUsersName,
            allUsersRepo)
        .setReadOnly()
        .load(rev);
  }

  /**
   * Loads the external ID notes for updates without cache evictions. The external ID notes are
   * loaded from the current tip of the {@code refs/meta/external-ids} branch.
   *
   * <p>Use this only from init, schema upgrades and tests.
   *
   * <p>Metrics are disabled.
   *
   * @return {@link ExternalIdNotes} instance that doesn't updates caches on save
   */
  public static ExternalIdNotes loadNoCacheUpdate(
      AllUsersName allUsersName, Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    return new ExternalIdNotes(
            new DisabledExternalIdCache(),
            null,
            null,
            new DisabledMetricMaker(),
            allUsersName,
            allUsersRepo)
        .load();
  }

  private final ExternalIdCache externalIdCache;
  @Nullable private final AccountCache accountCache;
  @Nullable private final Provider<AccountIndexer> accountIndexer;
  private final AllUsersName allUsersName;
  private final Counter0 updateCount;
  private final Repository repo;
  private final CallerFinder callerFinder;

  private NoteMap noteMap;
  private ObjectId oldRev;

  // Staged note map updates that should be executed on save.
  private List<NoteMapUpdate> noteMapUpdates = new ArrayList<>();

  // Staged cache updates that should be executed after external ID changes have been committed.
  private List<CacheUpdate> cacheUpdates = new ArrayList<>();

  private Runnable afterReadRevision;
  private boolean readOnly = false;

  private ExternalIdNotes(
      ExternalIdCache externalIdCache,
      @Nullable AccountCache accountCache,
      @Nullable Provider<AccountIndexer> accountIndexer,
      MetricMaker metricMaker,
      AllUsersName allUsersName,
      Repository allUsersRepo) {
    this.externalIdCache = requireNonNull(externalIdCache, "externalIdCache");
    this.accountCache = accountCache;
    this.accountIndexer = accountIndexer;
    this.updateCount =
        metricMaker.newCounter(
            "notedb/external_id_update_count",
            new Description("Total number of external ID updates.").setRate().setUnit("updates"));
    this.allUsersName = requireNonNull(allUsersName, "allUsersRepo");
    this.repo = requireNonNull(allUsersRepo, "allUsersRepo");
    this.callerFinder =
        CallerFinder.builder()
            // 1. callers that come through ExternalIds
            .addTarget(ExternalIds.class)

            // 2. callers that come through AccountsUpdate
            .addTarget(AccountsUpdate.class)
            .addIgnoredPackage("com.github.rholder.retry")
            .addIgnoredClass(RetryHelper.class)

            // 3. direct callers
            .addTarget(ExternalIdNotes.class)
            .build();
  }

  public ExternalIdNotes setAfterReadRevision(Runnable afterReadRevision) {
    this.afterReadRevision = afterReadRevision;
    return this;
  }

  private ExternalIdNotes setReadOnly() {
    this.readOnly = true;
    return this;
  }

  public Repository getRepository() {
    return repo;
  }

  @Override
  protected String getRefName() {
    return RefNames.REFS_EXTERNAL_IDS;
  }

  /**
   * Loads the external ID notes from the current tip of the {@code refs/meta/external-ids} branch.
   *
   * @return {@link ExternalIdNotes} instance for chaining
   */
  private ExternalIdNotes load() throws IOException, ConfigInvalidException {
    load(allUsersName, repo);
    return this;
  }

  /**
   * Loads the external ID notes from the specified revision of the {@code refs/meta/external-ids}
   * branch.
   *
   * @param rev the revision from which the external ID notes should be loaded, if {@code null} the
   *     external ID notes are loaded from the current tip, if {@link ObjectId#zeroId()} it's
   *     assumed that the {@code refs/meta/external-ids} branch doesn't exist and the loaded
   *     external IDs will be empty
   * @return {@link ExternalIdNotes} instance for chaining
   */
  ExternalIdNotes load(@Nullable ObjectId rev) throws IOException, ConfigInvalidException {
    if (rev == null) {
      return load();
    }
    if (ObjectId.zeroId().equals(rev)) {
      load(allUsersName, repo, null);
      return this;
    }
    load(allUsersName, repo, rev);
    return this;
  }

  /**
   * Parses and returns the specified external ID.
   *
   * @param key the key of the external ID
   * @return the external ID, {@code Optional.empty()} if it doesn't exist
   */
  public Optional<ExternalId> get(ExternalId.Key key) throws IOException, ConfigInvalidException {
    checkLoaded();
    ObjectId noteId = key.sha1();
    if (!noteMap.contains(noteId)) {
      return Optional.empty();
    }

    try (RevWalk rw = new RevWalk(repo)) {
      ObjectId noteDataId = noteMap.get(noteId);
      byte[] raw = readNoteData(rw, noteDataId);
      return Optional.of(ExternalId.parse(noteId.name(), raw, noteDataId));
    }
  }

  /**
   * Parses and returns the specified external IDs.
   *
   * @param keys the keys of the external IDs
   * @return the external IDs
   */
  public Set<ExternalId> get(Collection<ExternalId.Key> keys)
      throws IOException, ConfigInvalidException {
    checkLoaded();
    HashSet<ExternalId> externalIds = Sets.newHashSetWithExpectedSize(keys.size());
    for (ExternalId.Key key : keys) {
      get(key).ifPresent(externalIds::add);
    }
    return externalIds;
  }

  /**
   * Parses and returns all external IDs.
   *
   * <p>Invalid external IDs are ignored.
   *
   * @return all external IDs
   */
  public ImmutableSet<ExternalId> all() throws IOException {
    checkLoaded();
    try (RevWalk rw = new RevWalk(repo)) {
      ImmutableSet.Builder<ExternalId> b = ImmutableSet.builder();
      for (Note note : noteMap) {
        byte[] raw = readNoteData(rw, note.getData());
        try {
          b.add(ExternalId.parse(note.getName(), raw, note.getData()));
        } catch (ConfigInvalidException | RuntimeException e) {
          logger.atSevere().withCause(e).log(
              "Ignoring invalid external ID note %s", note.getName());
        }
      }
      return b.build();
    }
  }

  NoteMap getNoteMap() {
    checkLoaded();
    return noteMap;
  }

  static byte[] readNoteData(RevWalk rw, ObjectId noteDataId) throws IOException {
    return rw.getObjectReader().open(noteDataId, OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
  }

  /**
   * Inserts a new external ID.
   *
   * @throws IOException on IO error while checking if external ID already exists
   * @throws DuplicateExternalIdKeyException if the external ID already exists
   */
  public void insert(ExternalId extId) throws IOException, DuplicateExternalIdKeyException {
    insert(Collections.singleton(extId));
  }

  /**
   * Inserts new external IDs.
   *
   * @throws IOException on IO error while checking if external IDs already exist
   * @throws DuplicateExternalIdKeyException if any of the external ID already exists
   */
  public void insert(Collection<ExternalId> extIds)
      throws IOException, DuplicateExternalIdKeyException {
    checkLoaded();
    checkExternalIdsDontExist(extIds);

    Set<ExternalId> newExtIds = new HashSet<>();
    noteMapUpdates.add(
        (rw, n, f) -> {
          for (ExternalId extId : extIds) {
            ExternalId insertedExtId = upsert(rw, inserter, noteMap, f, extId);
            newExtIds.add(insertedExtId);
          }
        });
    cacheUpdates.add(cu -> cu.add(newExtIds));
  }

  /**
   * Inserts or updates an external ID.
   *
   * <p>If the external ID already exists, it is overwritten, otherwise it is inserted.
   */
  public void upsert(ExternalId extId) throws IOException, ConfigInvalidException {
    upsert(Collections.singleton(extId));
  }

  /**
   * Inserts or updates external IDs.
   *
   * <p>If any of the external IDs already exists, it is overwritten. New external IDs are inserted.
   */
  public void upsert(Collection<ExternalId> extIds) throws IOException, ConfigInvalidException {
    checkLoaded();
    Set<ExternalId> removedExtIds = get(ExternalId.Key.from(extIds));
    Set<ExternalId> updatedExtIds = new HashSet<>();
    noteMapUpdates.add(
        (rw, n, f) -> {
          for (ExternalId extId : extIds) {
            ExternalId updatedExtId = upsert(rw, inserter, noteMap, f, extId);
            updatedExtIds.add(updatedExtId);
          }
        });
    cacheUpdates.add(cu -> cu.remove(removedExtIds).add(updatedExtIds));
  }

  /**
   * Deletes an external ID.
   *
   * @throws IllegalStateException is thrown if there is an existing external ID that has the same
   *     key, but otherwise doesn't match the specified external ID.
   */
  public void delete(ExternalId extId) {
    delete(Collections.singleton(extId));
  }

  /**
   * Deletes external IDs.
   *
   * @throws IllegalStateException is thrown if there is an existing external ID that has the same
   *     key as any of the external IDs that should be deleted, but otherwise doesn't match the that
   *     external ID.
   */
  public void delete(Collection<ExternalId> extIds) {
    checkLoaded();
    Set<ExternalId> removedExtIds = new HashSet<>();
    noteMapUpdates.add(
        (rw, n, f) -> {
          for (ExternalId extId : extIds) {
            remove(rw, noteMap, f, extId);
            removedExtIds.add(extId);
          }
        });
    cacheUpdates.add(cu -> cu.remove(removedExtIds));
  }

  /**
   * Delete an external ID by key.
   *
   * @throws IllegalStateException is thrown if the external ID does not belong to the specified
   *     account.
   */
  public void delete(Account.Id accountId, ExternalId.Key extIdKey) {
    delete(accountId, Collections.singleton(extIdKey));
  }

  /**
   * Delete external IDs by external ID key.
   *
   * @throws IllegalStateException is thrown if any of the external IDs does not belong to the
   *     specified account.
   */
  public void delete(Account.Id accountId, Collection<ExternalId.Key> extIdKeys) {
    checkLoaded();
    Set<ExternalId> removedExtIds = new HashSet<>();
    noteMapUpdates.add(
        (rw, n, f) -> {
          for (ExternalId.Key extIdKey : extIdKeys) {
            ExternalId removedExtId = remove(rw, noteMap, f, extIdKey, accountId);
            removedExtIds.add(removedExtId);
          }
        });
    cacheUpdates.add(cu -> cu.remove(removedExtIds));
  }

  /**
   * Delete external IDs by external ID key.
   *
   * <p>The external IDs are deleted regardless of which account they belong to.
   */
  public void deleteByKeys(Collection<ExternalId.Key> extIdKeys) {
    checkLoaded();
    Set<ExternalId> removedExtIds = new HashSet<>();
    noteMapUpdates.add(
        (rw, n, f) -> {
          for (ExternalId.Key extIdKey : extIdKeys) {
            ExternalId extId = remove(rw, noteMap, f, extIdKey, null);
            removedExtIds.add(extId);
          }
        });
    cacheUpdates.add(cu -> cu.remove(removedExtIds));
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
      throws IOException, DuplicateExternalIdKeyException {
    checkLoaded();
    checkSameAccount(toAdd, accountId);
    checkExternalIdKeysDontExist(ExternalId.Key.from(toAdd), toDelete);

    Set<ExternalId> removedExtIds = new HashSet<>();
    Set<ExternalId> updatedExtIds = new HashSet<>();
    noteMapUpdates.add(
        (rw, n, f) -> {
          for (ExternalId.Key extIdKey : toDelete) {
            ExternalId removedExtId = remove(rw, noteMap, f, extIdKey, accountId);
            if (removedExtId != null) {
              removedExtIds.add(removedExtId);
            }
          }

          for (ExternalId extId : toAdd) {
            ExternalId insertedExtId = upsert(rw, inserter, noteMap, f, extId);
            updatedExtIds.add(insertedExtId);
          }
        });
    cacheUpdates.add(cu -> cu.add(updatedExtIds).remove(removedExtIds));
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
      throws IOException, DuplicateExternalIdKeyException {
    checkLoaded();
    checkExternalIdKeysDontExist(ExternalId.Key.from(toAdd), toDelete);

    Set<ExternalId> removedExtIds = new HashSet<>();
    Set<ExternalId> updatedExtIds = new HashSet<>();
    noteMapUpdates.add(
        (rw, n, f) -> {
          for (ExternalId.Key extIdKey : toDelete) {
            ExternalId removedExtId = remove(rw, noteMap, f, extIdKey, null);
            removedExtIds.add(removedExtId);
          }

          for (ExternalId extId : toAdd) {
            ExternalId insertedExtId = upsert(rw, inserter, noteMap, f, extId);
            updatedExtIds.add(insertedExtId);
          }
        });
    cacheUpdates.add(cu -> cu.add(updatedExtIds).remove(removedExtIds));
  }

  /**
   * Replaces an external ID.
   *
   * @throws IllegalStateException is thrown if the specified external IDs belong to different
   *     accounts.
   */
  public void replace(ExternalId toDelete, ExternalId toAdd)
      throws IOException, DuplicateExternalIdKeyException {
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
      throws IOException, DuplicateExternalIdKeyException {
    Account.Id accountId = checkSameAccount(Iterables.concat(toDelete, toAdd));
    if (accountId == null) {
      // toDelete and toAdd are empty -> nothing to do
      return;
    }

    replace(accountId, toDelete.stream().map(ExternalId::key).collect(toSet()), toAdd);
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (revision != null) {
      logger.atFine().log(
          "Reading external ID note map (caller: %s)", callerFinder.findCallerLazy());
      noteMap = NoteMap.read(reader, revision);
    } else {
      noteMap = NoteMap.newEmptyMap();
    }

    if (afterReadRevision != null) {
      afterReadRevision.run();
    }
  }

  @Override
  public RevCommit commit(MetaDataUpdate update) throws IOException {
    oldRev = ObjectIds.copyOrZero(revision);
    RevCommit commit = super.commit(update);
    updateCount.increment();
    return commit;
  }

  /**
   * Updates the caches (external ID cache, account cache) and reindexes the accounts for which
   * external IDs were modified.
   *
   * <p>Must only be called after committing changes.
   *
   * <p>No-op if this instance was created by {@link #loadNoCacheUpdate(AllUsersName, Repository)}.
   *
   * <p>No eviction from account cache and no reindex if this instance was created by {@link
   * FactoryNoReindex}.
   */
  public void updateCaches() throws IOException {
    updateCaches(ImmutableSet.of());
  }

  /**
   * Updates the caches (external ID cache, account cache) and reindexes the accounts for which
   * external IDs were modified.
   *
   * <p>Must only be called after committing changes.
   *
   * <p>No-op if this instance was created by {@link #loadNoCacheUpdate(AllUsersName, Repository)}.
   *
   * <p>No eviction from account cache if this instance was created by {@link FactoryNoReindex}.
   *
   * @param accountsToSkip set of accounts that should not be evicted from the account cache, in
   *     this case the caller must take care to evict them otherwise
   */
  public void updateCaches(Collection<Account.Id> accountsToSkip) throws IOException {
    checkState(oldRev != null, "no changes committed yet");

    ExternalIdCacheUpdates externalIdCacheUpdates = new ExternalIdCacheUpdates();
    for (CacheUpdate cacheUpdate : cacheUpdates) {
      cacheUpdate.execute(externalIdCacheUpdates);
    }

    externalIdCache.onReplace(
        oldRev,
        getRevision(),
        externalIdCacheUpdates.getRemoved(),
        externalIdCacheUpdates.getAdded());

    if (accountCache != null || accountIndexer != null) {
      for (Account.Id id :
          Streams.concat(
                  externalIdCacheUpdates.getAdded().stream(),
                  externalIdCacheUpdates.getRemoved().stream())
              .map(ExternalId::accountId)
              .filter(i -> !accountsToSkip.contains(i))
              .collect(toSet())) {
        if (accountCache != null) {
          accountCache.evict(id);
        }
        if (accountIndexer != null) {
          accountIndexer.get().index(id);
        }
      }
    }

    cacheUpdates.clear();
    oldRev = null;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    checkState(!readOnly, "Updating external IDs is disabled");

    if (noteMapUpdates.isEmpty()) {
      return false;
    }

    logger.atFine().log("Updating external IDs");

    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Update external IDs\n");
    }

    try (RevWalk rw = new RevWalk(reader)) {
      Set<String> footers = new HashSet<>();
      for (NoteMapUpdate noteMapUpdate : noteMapUpdates) {
        try {
          noteMapUpdate.execute(rw, noteMap, footers);
        } catch (DuplicateExternalIdKeyException e) {
          throw new IOException(e);
        }
      }
      noteMapUpdates.clear();
      if (!footers.isEmpty()) {
        commit.setMessage(
            footers.stream()
                .sorted()
                .collect(joining("\n", commit.getMessage().trim() + "\n\n", "")));
      }

      RevTree oldTree = revision != null ? rw.parseTree(revision) : null;
      ObjectId newTreeId = noteMap.writeTree(inserter);
      if (newTreeId.equals(oldTree)) {
        return false;
      }

      commit.setTreeId(newTreeId);
      return true;
    }
  }

  /**
   * Checks that all specified external IDs belong to the same account.
   *
   * @return the ID of the account to which all specified external IDs belong.
   */
  private static Account.Id checkSameAccount(Iterable<ExternalId> extIds) {
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
          "external id %s belongs to account %s, but expected account %s",
          extId.key().get(),
          extId.accountId().get(),
          accountId.get());
    }
    return accountId;
  }

  /**
   * Insert or updates an new external ID and sets it in the note map.
   *
   * <p>If the external ID already exists it is overwritten.
   */
  private static ExternalId upsert(
      RevWalk rw, ObjectInserter ins, NoteMap noteMap, Set<String> footers, ExternalId extId)
      throws IOException, ConfigInvalidException {
    ObjectId noteId = extId.key().sha1();
    Config c = new Config();
    if (noteMap.contains(extId.key().sha1())) {
      ObjectId noteDataId = noteMap.get(noteId);
      byte[] raw = readNoteData(rw, noteDataId);
      try {
        c = new BlobBasedConfig(null, raw);
        ExternalId oldExtId = ExternalId.parse(noteId.name(), c, noteDataId);
        addFooters(footers, oldExtId);
      } catch (ConfigInvalidException e) {
        throw new ConfigInvalidException(
            String.format("Invalid external id config for note %s: %s", noteId, e.getMessage()));
      }
    }
    extId.writeToConfig(c);
    byte[] raw = c.toText().getBytes(UTF_8);
    ObjectId noteData = ins.insert(OBJ_BLOB, raw);
    noteMap.set(noteId, noteData);
    ExternalId newExtId = ExternalId.create(extId, noteData);
    addFooters(footers, newExtId);
    return newExtId;
  }

  /**
   * Removes an external ID from the note map.
   *
   * @throws IllegalStateException is thrown if there is an existing external ID that has the same
   *     key, but otherwise doesn't match the specified external ID.
   */
  private static ExternalId remove(
      RevWalk rw, NoteMap noteMap, Set<String> footers, ExternalId extId)
      throws IOException, ConfigInvalidException {
    ObjectId noteId = extId.key().sha1();
    if (!noteMap.contains(noteId)) {
      return null;
    }

    ObjectId noteDataId = noteMap.get(noteId);
    byte[] raw = readNoteData(rw, noteDataId);
    ExternalId actualExtId = ExternalId.parse(noteId.name(), raw, noteDataId);
    checkState(
        extId.equals(actualExtId),
        "external id %s should be removed, but it doesn't match the actual external id %s",
        extId.toString(),
        actualExtId.toString());
    noteMap.remove(noteId);
    addFooters(footers, actualExtId);
    return actualExtId;
  }

  /**
   * Removes an external ID from the note map by external ID key.
   *
   * @throws IllegalStateException is thrown if an expected account ID is provided and an external
   *     ID with the specified key exists, but belongs to another account.
   * @return the external ID that was removed, {@code null} if no external ID with the specified key
   *     exists
   */
  private static ExternalId remove(
      RevWalk rw,
      NoteMap noteMap,
      Set<String> footers,
      ExternalId.Key extIdKey,
      Account.Id expectedAccountId)
      throws IOException, ConfigInvalidException {
    ObjectId noteId = extIdKey.sha1();
    if (!noteMap.contains(noteId)) {
      return null;
    }

    ObjectId noteDataId = noteMap.get(noteId);
    byte[] raw = readNoteData(rw, noteDataId);
    ExternalId extId = ExternalId.parse(noteId.name(), raw, noteDataId);
    if (expectedAccountId != null) {
      checkState(
          expectedAccountId.equals(extId.accountId()),
          "external id %s should be removed for account %s,"
              + " but external id belongs to account %s",
          extIdKey.get(),
          expectedAccountId.get(),
          extId.accountId().get());
    }
    noteMap.remove(noteId);
    addFooters(footers, extId);
    return extId;
  }

  private static void addFooters(Set<String> footers, ExternalId extId) {
    footers.add("Account: " + extId.accountId().get());
    if (extId.email() != null) {
      footers.add("Email: " + extId.email());
    }
  }

  private void checkExternalIdsDontExist(Collection<ExternalId> extIds)
      throws DuplicateExternalIdKeyException, IOException {
    checkExternalIdKeysDontExist(ExternalId.Key.from(extIds));
  }

  private void checkExternalIdKeysDontExist(
      Collection<ExternalId.Key> extIdKeysToAdd, Collection<ExternalId.Key> extIdKeysToDelete)
      throws DuplicateExternalIdKeyException, IOException {
    HashSet<ExternalId.Key> newKeys = new HashSet<>(extIdKeysToAdd);
    newKeys.removeAll(extIdKeysToDelete);
    checkExternalIdKeysDontExist(newKeys);
  }

  private void checkExternalIdKeysDontExist(Collection<ExternalId.Key> extIdKeys)
      throws IOException, DuplicateExternalIdKeyException {
    for (ExternalId.Key extIdKey : extIdKeys) {
      if (noteMap.contains(extIdKey.sha1())) {
        throw new DuplicateExternalIdKeyException(extIdKey);
      }
    }
  }

  private void checkLoaded() {
    checkState(noteMap != null, "External IDs not loaded yet");
  }

  @FunctionalInterface
  private interface NoteMapUpdate {
    void execute(RevWalk rw, NoteMap noteMap, Set<String> footers)
        throws IOException, ConfigInvalidException, DuplicateExternalIdKeyException;
  }

  @FunctionalInterface
  private interface CacheUpdate {
    void execute(ExternalIdCacheUpdates cacheUpdates) throws IOException;
  }

  private static class ExternalIdCacheUpdates {
    private final Set<ExternalId> added = new HashSet<>();
    private final Set<ExternalId> removed = new HashSet<>();

    ExternalIdCacheUpdates add(Collection<ExternalId> extIds) {
      this.added.addAll(extIds);
      return this;
    }

    public Set<ExternalId> getAdded() {
      return ImmutableSet.copyOf(added);
    }

    ExternalIdCacheUpdates remove(Collection<ExternalId> extIds) {
      this.removed.addAll(extIds);
      return this;
    }

    public Set<ExternalId> getRemoved() {
      return ImmutableSet.copyOf(removed);
    }
  }
}
