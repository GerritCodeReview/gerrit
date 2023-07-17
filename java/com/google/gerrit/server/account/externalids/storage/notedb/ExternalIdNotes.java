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

package com.google.gerrit.server.account.externalids.storage.notedb;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdCache;
import com.google.gerrit.server.account.externalids.ExternalIdUpsertPreprocessor;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AuthConfig;
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
import java.util.function.Function;
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
 * accounts for which external IDs have been updated (see {@link
 * ExternalIdNotesLoader#updateExternalIdCacheAndMaybeReindexAccounts(ExternalIdNotes,
 * Collection)}).
 */
public class ExternalIdNotes extends VersionedMetaData {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int MAX_NOTE_SZ = 1 << 19;

  public abstract static class ExternalIdNotesLoader {
    protected final ExternalIdCache externalIdCache;
    protected final MetricMaker metricMaker;
    protected final AllUsersName allUsersName;
    protected final DynamicMap<ExternalIdUpsertPreprocessor> upsertPreprocessors;
    protected final ExternalIdFactoryNoteDbImpl externalIdFactory;
    protected final AuthConfig authConfig;

    protected ExternalIdNotesLoader(
        ExternalIdCache externalIdCache,
        MetricMaker metricMaker,
        AllUsersName allUsersName,
        DynamicMap<ExternalIdUpsertPreprocessor> upsertPreprocessors,
        ExternalIdFactoryNoteDbImpl externalIdFactory,
        AuthConfig authConfig) {
      this.externalIdCache = externalIdCache;
      this.metricMaker = metricMaker;
      this.allUsersName = allUsersName;
      this.upsertPreprocessors = upsertPreprocessors;
      this.externalIdFactory = externalIdFactory;
      this.authConfig = authConfig;
    }

    /**
     * Loads the external ID notes from the current tip of the {@code refs/meta/external-ids}
     * branch.
     *
     * @param allUsersRepo the All-Users repository
     */
    public abstract ExternalIdNotes load(Repository allUsersRepo)
        throws IOException, ConfigInvalidException;

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
    public abstract ExternalIdNotes load(Repository allUsersRepo, @Nullable ObjectId rev)
        throws IOException, ConfigInvalidException;

    /**
     * Updates the external ID cache. Subclasses of type {@link Factory} will also reindex the
     * accounts for which external IDs were modified, while subclasses of type {@link
     * FactoryNoReindex} will skip this.
     *
     * <p>Must only be called after committing changes.
     *
     * @param externalIdNotes the committed updates that should be applied to the cache. This first
     *     and last element must be the updates commited first and last, respectively.
     * @param accountsToSkipForReindex accounts that should not be reindexed. This is to avoid
     *     double reindexing when updated accounts will already be reindexed by
     *     ReindexAfterRefUpdate.
     */
    public void updateExternalIdCacheAndMaybeReindexAccounts(
        ExternalIdNotes externalIdNotes, Collection<Account.Id> accountsToSkipForReindex)
        throws IOException {
      checkState(externalIdNotes.oldRev != null, "no changes committed yet");

      // readOnly is ignored here (legacy behavior).

      // Aggregate all updates.
      ExternalIdCacheUpdates updates = new ExternalIdCacheUpdates();
      for (CacheUpdate cacheUpdate : externalIdNotes.cacheUpdates) {
        cacheUpdate.execute(updates);
      }

      // Reindex accounts (if the subclass implements reindexAccount()).
      if (!externalIdNotes.noReindex) {
        Streams.concat(updates.getAdded().stream(), updates.getRemoved().stream())
            .map(ExternalId::accountId)
            .filter(i -> !accountsToSkipForReindex.contains(i))
            .distinct()
            .forEach(this::reindexAccount);
      }

      // Reset instance state.
      externalIdNotes.cacheUpdates.clear();
      externalIdNotes.keysToAdd.clear();
      externalIdNotes.oldRev = null;
    }

    protected abstract void reindexAccount(Account.Id id);
  }

  @Singleton
  public static class Factory extends ExternalIdNotesLoader {

    private final Provider<AccountIndexer> accountIndexer;

    @Inject
    Factory(
        ExternalIdCache externalIdCache,
        Provider<AccountIndexer> accountIndexer,
        MetricMaker metricMaker,
        AllUsersName allUsersName,
        DynamicMap<ExternalIdUpsertPreprocessor> upsertPreprocessors,
        ExternalIdFactoryNoteDbImpl externalIdFactory,
        AuthConfig authConfig) {
      super(
          externalIdCache,
          metricMaker,
          allUsersName,
          upsertPreprocessors,
          externalIdFactory,
          authConfig);
      this.accountIndexer = accountIndexer;
    }

    @Override
    public ExternalIdNotes load(Repository allUsersRepo)
        throws IOException, ConfigInvalidException {
      return new ExternalIdNotes(
              metricMaker,
              allUsersName,
              allUsersRepo,
              upsertPreprocessors,
              externalIdFactory,
              authConfig.isUserNameCaseInsensitiveMigrationMode())
          .load();
    }

    @Override
    public ExternalIdNotes load(Repository allUsersRepo, @Nullable ObjectId rev)
        throws IOException, ConfigInvalidException {
      return new ExternalIdNotes(
              metricMaker,
              allUsersName,
              allUsersRepo,
              upsertPreprocessors,
              externalIdFactory,
              authConfig.isUserNameCaseInsensitiveMigrationMode())
          .load(rev);
    }

    @Override
    protected void reindexAccount(Account.Id id) {
      accountIndexer.get().index(id);
    }
  }

  @Singleton
  public static class FactoryNoReindex extends ExternalIdNotesLoader {

    @Inject
    FactoryNoReindex(
        ExternalIdCache externalIdCache,
        MetricMaker metricMaker,
        AllUsersName allUsersName,
        DynamicMap<ExternalIdUpsertPreprocessor> upsertPreprocessors,
        ExternalIdFactoryNoteDbImpl externalIdFactory,
        AuthConfig authConfig) {
      super(
          externalIdCache,
          metricMaker,
          allUsersName,
          upsertPreprocessors,
          externalIdFactory,
          authConfig);
    }

    @Override
    public ExternalIdNotes load(Repository allUsersRepo)
        throws IOException, ConfigInvalidException {
      return new ExternalIdNotes(
              metricMaker,
              allUsersName,
              allUsersRepo,
              upsertPreprocessors,
              externalIdFactory,
              authConfig.isUserNameCaseInsensitiveMigrationMode())
          .setNoReindex()
          .load();
    }

    @Override
    public ExternalIdNotes load(Repository allUsersRepo, @Nullable ObjectId rev)
        throws IOException, ConfigInvalidException {
      return new ExternalIdNotes(
              metricMaker,
              allUsersName,
              allUsersRepo,
              upsertPreprocessors,
              externalIdFactory,
              authConfig.isUserNameCaseInsensitiveMigrationMode())
          .setNoReindex()
          .load(rev);
    }

    @Override
    protected void reindexAccount(Account.Id id) {
      // Do not reindex.
    }
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
      AllUsersName allUsersName,
      Repository allUsersRepo,
      @Nullable ObjectId rev,
      ExternalIdFactoryNoteDbImpl externalIdFactory,
      boolean isUserNameCaseInsensitiveMigrationMode)
      throws IOException, ConfigInvalidException {
    return new ExternalIdNotes(
            new DisabledMetricMaker(),
            allUsersName,
            allUsersRepo,
            DynamicMap.emptyMap(),
            externalIdFactory,
            isUserNameCaseInsensitiveMigrationMode)
        .setReadOnly()
        .setNoReindex()
        .load(rev);
  }

  /**
   * Loads the external ID notes for updates. The external ID notes are loaded from the current tip
   * of the {@code refs/meta/external-ids} branch.
   *
   * <p>Use this only from init, schema upgrades and tests.
   *
   * <p>Metrics are disabled.
   *
   * @return {@link ExternalIdNotes} instance that doesn't updates caches on save
   */
  public static ExternalIdNotes load(
      AllUsersName allUsersName,
      Repository allUsersRepo,
      ExternalIdFactoryNoteDbImpl externalIdFactory,
      boolean isUserNameCaseInsensitiveMigrationMode)
      throws IOException, ConfigInvalidException {
    return new ExternalIdNotes(
            new DisabledMetricMaker(),
            allUsersName,
            allUsersRepo,
            DynamicMap.emptyMap(),
            externalIdFactory,
            isUserNameCaseInsensitiveMigrationMode)
        .setNoReindex()
        .load();
  }

  private final AllUsersName allUsersName;
  private final Counter0 updateCount;
  private final Repository repo;
  private final DynamicMap<ExternalIdUpsertPreprocessor> upsertPreprocessors;
  private final CallerFinder callerFinder;
  private final ExternalIdFactoryNoteDbImpl externalIdFactory;

  private NoteMap noteMap;
  private ObjectId oldRev;

  /** Staged note map updates that should be executed on save. */
  private final List<NoteMapUpdate> noteMapUpdates = new ArrayList<>();

  /** Staged cache updates that should be executed after external ID changes have been committed. */
  private final List<CacheUpdate> cacheUpdates = new ArrayList<>();

  /**
   * When performing batch updates (cf. {@link AccountsUpdate#updateBatch(List)} we need to ensure
   * the batch does not introduce duplicates. In addition to checking against the status quo in
   * {@link #noteMap} (cf. {@link #checkExternalIdKeysDontExist(Collection)}), which is sufficient
   * for single updates, we also need to check for duplicates among the batch updates. As the actual
   * updates are computed lazily just before applying them, we unfortunately need to track keys
   * explicitly here even though they are already implicit in the lambdas that constitute the
   * updates.
   */
  private final Set<ExternalId.Key> keysToAdd = new HashSet<>();

  private Runnable afterReadRevision;
  private boolean readOnly = false;
  private boolean noReindex = false;
  private boolean isUserNameCaseInsensitiveMigrationMode = false;
  protected final Function<ExternalId, ObjectId> defaultNoteIdResolver =
      (extId) -> {
        ObjectId noteId = extId.key().sha1();
        try {
          if (isUserNameCaseInsensitiveMigrationMode && !noteMap.contains(noteId)) {
            noteId = extId.key().caseSensitiveSha1();
          }
        } catch (IOException e) {
          return noteId;
        }
        return noteId;
      };

  private ExternalIdNotes(
      MetricMaker metricMaker,
      AllUsersName allUsersName,
      Repository allUsersRepo,
      DynamicMap<ExternalIdUpsertPreprocessor> upsertPreprocessors,
      ExternalIdFactoryNoteDbImpl externalIdFactory,
      boolean isUserNameCaseInsensitiveMigrationMode) {
    this.updateCount =
        metricMaker.newCounter(
            "notedb/external_id_update_count",
            new Description("Total number of external ID updates.").setRate().setUnit("updates"));
    this.allUsersName = requireNonNull(allUsersName, "allUsersRepo");
    this.repo = requireNonNull(allUsersRepo, "allUsersRepo");
    this.upsertPreprocessors = upsertPreprocessors;
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
    this.externalIdFactory = externalIdFactory;
    this.isUserNameCaseInsensitiveMigrationMode = isUserNameCaseInsensitiveMigrationMode;
  }

  public ExternalIdNotes setAfterReadRevision(Runnable afterReadRevision) {
    this.afterReadRevision = afterReadRevision;
    return this;
  }

  private ExternalIdNotes setReadOnly() {
    readOnly = true;
    return this;
  }

  private ExternalIdNotes setNoReindex() {
    noReindex = true;
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
    ObjectId noteId = getNoteId(key);
    if (noteMap.contains(noteId)) {

      try (RevWalk rw = new RevWalk(repo)) {
        ObjectId noteDataId = noteMap.get(noteId);
        byte[] raw = readNoteData(rw, noteDataId);
        return Optional.of(externalIdFactory.parse(noteId.name(), raw, noteDataId));
      }
    }
    return Optional.empty();
  }

  protected ObjectId getNoteId(ExternalId.Key key) throws IOException {
    ObjectId noteId = key.sha1();

    if (!noteMap.contains(noteId) && isUserNameCaseInsensitiveMigrationMode) {
      noteId = key.caseSensitiveSha1();
    }

    return noteId;
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
          b.add(externalIdFactory.parse(note.getName(), raw, note.getData()));
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
        (rw, n) -> {
          for (ExternalId extId : extIds) {
            ExternalId insertedExtId = upsert(rw, inserter, noteMap, extId);
            preprocessUpsert(insertedExtId);
            newExtIds.add(insertedExtId);
          }
        });
    cacheUpdates.add(cu -> cu.add(newExtIds));
    incrementalDuplicateDetection(extIds);
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
        (rw, n) -> {
          for (ExternalId extId : extIds) {
            ExternalId updatedExtId = upsert(rw, inserter, noteMap, extId);
            preprocessUpsert(updatedExtId);
            updatedExtIds.add(updatedExtId);
          }
        });
    cacheUpdates.add(cu -> cu.remove(removedExtIds).add(updatedExtIds));
    incrementalDuplicateDetection(extIds);
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
        (rw, n) -> {
          for (ExternalId extId : extIds) {
            remove(rw, noteMap, extId);
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
        (rw, n) -> {
          for (ExternalId.Key extIdKey : extIdKeys) {
            ExternalId removedExtId = remove(rw, noteMap, extIdKey, accountId);
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
        (rw, n) -> {
          for (ExternalId.Key extIdKey : extIdKeys) {
            ExternalId extId = remove(rw, noteMap, extIdKey, null);
            removedExtIds.add(extId);
          }
        });
    cacheUpdates.add(cu -> cu.remove(removedExtIds));
  }

  public void replace(
      Account.Id accountId, Collection<ExternalId.Key> toDelete, Collection<ExternalId> toAdd)
      throws IOException, DuplicateExternalIdKeyException {
    replace(accountId, toDelete, toAdd, defaultNoteIdResolver);
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
      Account.Id accountId,
      Collection<ExternalId.Key> toDelete,
      Collection<ExternalId> toAdd,
      Function<ExternalId, ObjectId> noteIdResolver)
      throws IOException, DuplicateExternalIdKeyException {
    checkLoaded();
    checkSameAccount(toAdd, accountId);
    checkExternalIdKeysDontExist(ExternalId.Key.from(toAdd), toDelete);

    Set<ExternalId> removedExtIds = new HashSet<>();
    Set<ExternalId> updatedExtIds = new HashSet<>();
    noteMapUpdates.add(
        (rw, n) -> {
          for (ExternalId.Key extIdKey : toDelete) {
            ExternalId removedExtId = remove(rw, noteMap, extIdKey, accountId);
            if (removedExtId != null) {
              removedExtIds.add(removedExtId);
            }
          }

          for (ExternalId extId : toAdd) {
            ExternalId insertedExtId = upsert(rw, inserter, noteMap, extId, noteIdResolver);
            preprocessUpsert(insertedExtId);
            updatedExtIds.add(insertedExtId);
          }
        });
    cacheUpdates.add(cu -> cu.add(updatedExtIds).remove(removedExtIds));
    incrementalDuplicateDetection(toAdd);
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
        (rw, n) -> {
          for (ExternalId.Key extIdKey : toDelete) {
            ExternalId removedExtId = remove(rw, noteMap, extIdKey, null);
            removedExtIds.add(removedExtId);
          }

          for (ExternalId extId : toAdd) {
            ExternalId insertedExtId = upsert(rw, inserter, noteMap, extId);
            preprocessUpsert(insertedExtId);
            updatedExtIds.add(insertedExtId);
          }
        });
    cacheUpdates.add(cu -> cu.add(updatedExtIds).remove(removedExtIds));
    incrementalDuplicateDetection(toAdd);
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
  public void replace(
      Collection<ExternalId> toDelete,
      Collection<ExternalId> toAdd,
      Function<ExternalId, ObjectId> noteIdResolver)
      throws IOException, DuplicateExternalIdKeyException {
    Account.Id accountId = checkSameAccount(Iterables.concat(toDelete, toAdd));
    if (accountId == null) {
      // toDelete and toAdd are empty -> nothing to do
      return;
    }

    replace(
        accountId, toDelete.stream().map(ExternalId::key).collect(toSet()), toAdd, noteIdResolver);
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
  @CanIgnoreReturnValue
  public RevCommit commit(MetaDataUpdate update) throws IOException {
    oldRev = ObjectIds.copyOrZero(revision);
    RevCommit commit = super.commit(update);
    updateCount.increment();
    return commit;
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
      for (NoteMapUpdate noteMapUpdate : noteMapUpdates) {
        try {
          noteMapUpdate.execute(rw, noteMap);
        } catch (DuplicateExternalIdKeyException e) {
          throw new IOException(e);
        }
      }
      noteMapUpdates.clear();

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
  @CanIgnoreReturnValue
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

  private void incrementalDuplicateDetection(Collection<ExternalId> externalIds) {
    externalIds.stream()
        .map(ExternalId::key)
        .forEach(
            key -> {
              if (!keysToAdd.add(key)) {
                throw new DuplicateExternalIdKeyException(key);
              }
            });
  }

  /**
   * Inserts or updates a new external ID and sets it in the note map.
   *
   * <p>If the external ID already exists, it is overwritten.
   */
  private ExternalId upsert(RevWalk rw, ObjectInserter ins, NoteMap noteMap, ExternalId extId)
      throws IOException, ConfigInvalidException {
    return upsert(rw, ins, noteMap, extId, defaultNoteIdResolver);
  }

  /**
   * Inserts or updates a new external ID and sets it in the note map.
   *
   * <p>If the external ID already exists, it is overwritten.
   */
  private ExternalId upsert(
      RevWalk rw,
      ObjectInserter ins,
      NoteMap noteMap,
      ExternalId extId,
      Function<ExternalId, ObjectId> noteIdResolver)
      throws IOException, ConfigInvalidException {
    ObjectId noteId = extId.key().sha1();
    Config c = new Config();
    ObjectId resolvedNoteId = noteIdResolver.apply(extId);
    if (noteMap.contains(resolvedNoteId)) {
      noteId = resolvedNoteId;
      ObjectId noteDataId = noteMap.get(noteId);
      byte[] raw = readNoteData(rw, noteDataId);
      try {
        c = new BlobBasedConfig(null, raw);
      } catch (ConfigInvalidException e) {
        throw new ConfigInvalidException(
            String.format("Invalid external id config for note %s: %s", noteId, e.getMessage()));
      }
    }
    extId.writeToConfig(c);
    byte[] raw = c.toText().getBytes(UTF_8);
    ObjectId noteData = ins.insert(OBJ_BLOB, raw);
    noteMap.set(noteId, noteData);
    return externalIdFactory.create(extId, noteData);
  }

  /**
   * Removes an external ID from the note map.
   *
   * @throws IllegalStateException is thrown if there is an existing external ID that has the same
   *     key, but otherwise doesn't match the specified external ID.
   */
  private void remove(RevWalk rw, NoteMap noteMap, ExternalId extId)
      throws IOException, ConfigInvalidException {
    ObjectId noteId = getNoteId(extId.key());

    if (!noteMap.contains(noteId)) {
      return;
    }

    ObjectId noteDataId = noteMap.get(noteId);
    byte[] raw = readNoteData(rw, noteDataId);
    ExternalId actualExtId = externalIdFactory.parse(noteId.name(), raw, noteDataId);
    checkState(
        extId.equals(actualExtId),
        "external id %s should be removed, but it doesn't match the actual external id %s",
        extId.toString(),
        actualExtId.toString());
    noteMap.remove(noteId);
  }

  /**
   * Removes an external ID from the note map by external ID key.
   *
   * @throws IllegalStateException is thrown if an expected account ID is provided and an external
   *     ID with the specified key exists, but belongs to another account.
   * @return the external ID that was removed, {@code null} if no external ID with the specified key
   *     exists
   */
  @Nullable
  private ExternalId remove(
      RevWalk rw, NoteMap noteMap, ExternalId.Key extIdKey, Account.Id expectedAccountId)
      throws IOException, ConfigInvalidException {
    ObjectId noteId = getNoteId(extIdKey);

    if (!noteMap.contains(noteId)) {
      return null;
    }

    ObjectId noteDataId = noteMap.get(noteId);
    byte[] raw = readNoteData(rw, noteDataId);
    ExternalId extId = externalIdFactory.parse(noteId.name(), raw, noteDataId);
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
    return extId;
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

  private void preprocessUpsert(ExternalId extId) {
    upsertPreprocessors.forEach(p -> p.get().upsert(extId));
  }

  @FunctionalInterface
  private interface NoteMapUpdate {
    void execute(RevWalk rw, NoteMap noteMap)
        throws IOException, ConfigInvalidException, DuplicateExternalIdKeyException;
  }

  @FunctionalInterface
  private interface CacheUpdate {
    void execute(ExternalIdCacheUpdates cacheUpdates) throws IOException;
  }

  private static class ExternalIdCacheUpdates {
    final Set<ExternalId> added = new HashSet<>();
    final Set<ExternalId> removed = new HashSet<>();

    @CanIgnoreReturnValue
    ExternalIdCacheUpdates add(Collection<ExternalId> extIds) {
      this.added.addAll(extIds);
      return this;
    }

    Set<ExternalId> getAdded() {
      return ImmutableSet.copyOf(added);
    }

    @CanIgnoreReturnValue
    ExternalIdCacheUpdates remove(Collection<ExternalId> extIds) {
      this.removed.addAll(extIds);
      return this;
    }

    Set<ExternalId> getRemoved() {
      return ImmutableSet.copyOf(removed);
    }
  }
}
