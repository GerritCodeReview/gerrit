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
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.account.externalids.ExternalId.Key.toAccountExternalIdKeys;
import static com.google.gerrit.server.account.externalids.ExternalId.toAccountExternalIds;
import static com.google.gerrit.server.account.externalids.ExternalIdReader.MAX_NOTE_SZ;
import static com.google.gerrit.server.account.externalids.ExternalIdReader.readNoteMap;
import static com.google.gerrit.server.account.externalids.ExternalIdReader.readRevision;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Runnables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

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
 */
public class ExternalIdsUpdate {
  private static final String COMMIT_MSG = "Update external IDs";

  /**
   * Factory to create an ExternalIdsUpdate instance for updating external IDs by the Gerrit server.
   *
   * <p>The Gerrit server identity will be used as author and committer for all commits that update
   * the external IDs.
   */
  @Singleton
  public static class Server {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsersName;
    private final ExternalIds externalIds;
    private final ExternalIdCache externalIdCache;
    private final Provider<PersonIdent> serverIdent;

    @Inject
    public Server(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        ExternalIds externalIds,
        ExternalIdCache externalIdCache,
        @GerritPersonIdent Provider<PersonIdent> serverIdent) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.externalIds = externalIds;
      this.externalIdCache = externalIdCache;
      this.serverIdent = serverIdent;
    }

    public ExternalIdsUpdate create() {
      PersonIdent i = serverIdent.get();
      return new ExternalIdsUpdate(repoManager, allUsersName, externalIds, externalIdCache, i, i);
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
    private final AllUsersName allUsersName;
    private final ExternalIds externalIds;
    private final ExternalIdCache externalIdCache;
    private final Provider<PersonIdent> serverIdent;
    private final Provider<IdentifiedUser> identifiedUser;

    @Inject
    public User(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        ExternalIds externalIds,
        ExternalIdCache externalIdCache,
        @GerritPersonIdent Provider<PersonIdent> serverIdent,
        Provider<IdentifiedUser> identifiedUser) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.externalIds = externalIds;
      this.externalIdCache = externalIdCache;
      this.serverIdent = serverIdent;
      this.identifiedUser = identifiedUser;
    }

    public ExternalIdsUpdate create() {
      PersonIdent i = serverIdent.get();
      return new ExternalIdsUpdate(
          repoManager,
          allUsersName,
          externalIds,
          externalIdCache,
          createPersonIdent(i, identifiedUser.get()),
          i);
    }

    private PersonIdent createPersonIdent(PersonIdent ident, IdentifiedUser user) {
      return user.newCommitterIdent(ident.getWhen(), ident.getTimeZone());
    }
  }

  @VisibleForTesting
  public static RetryerBuilder<ObjectId> retryerBuilder() {
    return RetryerBuilder.<ObjectId>newBuilder()
        .retryIfException(e -> e instanceof LockFailureException)
        .withWaitStrategy(
            WaitStrategies.join(
                WaitStrategies.exponentialWait(2, TimeUnit.SECONDS),
                WaitStrategies.randomWait(50, TimeUnit.MILLISECONDS)))
        .withStopStrategy(StopStrategies.stopAfterDelay(10, TimeUnit.SECONDS));
  }

  private static final Retryer<ObjectId> RETRYER = retryerBuilder().build();

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final ExternalIds externalIds;
  private final ExternalIdCache externalIdCache;
  private final PersonIdent committerIdent;
  private final PersonIdent authorIdent;
  private final Runnable afterReadRevision;
  private final Retryer<ObjectId> retryer;

  private ExternalIdsUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      ExternalIds externalIds,
      ExternalIdCache externalIdCache,
      PersonIdent committerIdent,
      PersonIdent authorIdent) {
    this(
        repoManager,
        allUsersName,
        externalIds,
        externalIdCache,
        committerIdent,
        authorIdent,
        Runnables.doNothing(),
        RETRYER);
  }

  @VisibleForTesting
  public ExternalIdsUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      ExternalIds externalIds,
      ExternalIdCache externalIdCache,
      PersonIdent committerIdent,
      PersonIdent authorIdent,
      Runnable afterReadRevision,
      Retryer<ObjectId> retryer) {
    this.repoManager = checkNotNull(repoManager, "repoManager");
    this.allUsersName = checkNotNull(allUsersName, "allUsersName");
    this.committerIdent = checkNotNull(committerIdent, "committerIdent");
    this.externalIds = checkNotNull(externalIds, "externalIds");
    this.externalIdCache = checkNotNull(externalIdCache, "externalIdCache");
    this.authorIdent = checkNotNull(authorIdent, "authorIdent");
    this.afterReadRevision = checkNotNull(afterReadRevision, "afterReadRevision");
    this.retryer = checkNotNull(retryer, "retryer");
  }

  /**
   * Inserts a new external ID.
   *
   * <p>If the external ID already exists, the insert fails with {@link OrmDuplicateKeyException}.
   */
  public void insert(ReviewDb db, ExternalId extId)
      throws IOException, ConfigInvalidException, OrmException {
    insert(db, Collections.singleton(extId));
  }

  /**
   * Inserts new external IDs.
   *
   * <p>If any of the external ID already exists, the insert fails with {@link
   * OrmDuplicateKeyException}.
   */
  public void insert(ReviewDb db, Collection<ExternalId> extIds)
      throws IOException, ConfigInvalidException, OrmException {
    db.accountExternalIds().insert(toAccountExternalIds(extIds));

    ObjectId newRev =
        updateNoteMap(
            o -> {
              for (ExternalId extId : extIds) {
                insert(o.rw(), o.ins(), o.noteMap(), extId);
              }
            });
    externalIdCache.onCreate(newRev, extIds);
  }

  /**
   * Inserts or updates an external ID.
   *
   * <p>If the external ID already exists, it is overwritten, otherwise it is inserted.
   */
  public void upsert(ReviewDb db, ExternalId extId)
      throws IOException, ConfigInvalidException, OrmException {
    upsert(db, Collections.singleton(extId));
  }

  /**
   * Inserts or updates external IDs.
   *
   * <p>If any of the external IDs already exists, it is overwritten. New external IDs are inserted.
   */
  public void upsert(ReviewDb db, Collection<ExternalId> extIds)
      throws IOException, ConfigInvalidException, OrmException {
    db.accountExternalIds().upsert(toAccountExternalIds(extIds));

    ObjectId newRev =
        updateNoteMap(
            o -> {
              for (ExternalId extId : extIds) {
                upsert(o.rw(), o.ins(), o.noteMap(), extId);
              }
            });
    externalIdCache.onUpdate(newRev, extIds);
  }

  /**
   * Deletes an external ID.
   *
   * @throws IllegalStateException is thrown if there is an existing external ID that has the same
   *     key, but otherwise doesn't match the specified external ID.
   */
  public void delete(ReviewDb db, ExternalId extId)
      throws IOException, ConfigInvalidException, OrmException {
    delete(db, Collections.singleton(extId));
  }

  /**
   * Deletes external IDs.
   *
   * @throws IllegalStateException is thrown if there is an existing external ID that has the same
   *     key as any of the external IDs that should be deleted, but otherwise doesn't match the that
   *     external ID.
   */
  public void delete(ReviewDb db, Collection<ExternalId> extIds)
      throws IOException, ConfigInvalidException, OrmException {
    db.accountExternalIds().delete(toAccountExternalIds(extIds));

    ObjectId newRev =
        updateNoteMap(
            o -> {
              for (ExternalId extId : extIds) {
                remove(o.rw(), o.noteMap(), extId);
              }
            });
    externalIdCache.onRemove(newRev, extIds);
  }

  /**
   * Delete an external ID by key.
   *
   * @throws IllegalStateException is thrown if the external ID does not belong to the specified
   *     account.
   */
  public void delete(ReviewDb db, Account.Id accountId, ExternalId.Key extIdKey)
      throws IOException, ConfigInvalidException, OrmException {
    delete(db, accountId, Collections.singleton(extIdKey));
  }

  /**
   * Delete external IDs by external ID key.
   *
   * @throws IllegalStateException is thrown if any of the external IDs does not belong to the
   *     specified account.
   */
  public void delete(ReviewDb db, Account.Id accountId, Collection<ExternalId.Key> extIdKeys)
      throws IOException, ConfigInvalidException, OrmException {
    db.accountExternalIds().deleteKeys(toAccountExternalIdKeys(extIdKeys));

    ObjectId newRev =
        updateNoteMap(
            o -> {
              for (ExternalId.Key extIdKey : extIdKeys) {
                remove(o.rw(), o.noteMap(), extIdKey, accountId);
              }
            });
    externalIdCache.onRemoveByKeys(newRev, accountId, extIdKeys);
  }

  /**
   * Delete external IDs by external ID key.
   *
   * <p>The external IDs are deleted regardless of which account they belong to.
   */
  public void deleteByKeys(ReviewDb db, Collection<ExternalId.Key> extIdKeys)
      throws IOException, ConfigInvalidException, OrmException {
    db.accountExternalIds().deleteKeys(toAccountExternalIdKeys(extIdKeys));

    ObjectId newRev =
        updateNoteMap(
            o -> {
              for (ExternalId.Key extIdKey : extIdKeys) {
                remove(o.rw(), o.noteMap(), extIdKey, null);
              }
            });
    externalIdCache.onRemoveByKeys(newRev, extIdKeys);
  }

  /** Deletes all external IDs of the specified account. */
  public void deleteAll(ReviewDb db, Account.Id accountId)
      throws IOException, ConfigInvalidException, OrmException {
    delete(db, externalIds.byAccount(db, accountId));
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
      ReviewDb db,
      Account.Id accountId,
      Collection<ExternalId.Key> toDelete,
      Collection<ExternalId> toAdd)
      throws IOException, ConfigInvalidException, OrmException {
    checkSameAccount(toAdd, accountId);

    db.accountExternalIds().deleteKeys(toAccountExternalIdKeys(toDelete));
    db.accountExternalIds().insert(toAccountExternalIds(toAdd));

    ObjectId newRev =
        updateNoteMap(
            o -> {
              for (ExternalId.Key extIdKey : toDelete) {
                remove(o.rw(), o.noteMap(), extIdKey, accountId);
              }

              for (ExternalId extId : toAdd) {
                insert(o.rw(), o.ins(), o.noteMap(), extId);
              }
            });
    externalIdCache.onReplaceByKeys(newRev, accountId, toDelete, toAdd);
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
  public void replaceByKeys(
      ReviewDb db, Collection<ExternalId.Key> toDelete, Collection<ExternalId> toAdd)
      throws IOException, ConfigInvalidException, OrmException {
    db.accountExternalIds().deleteKeys(toAccountExternalIdKeys(toDelete));
    db.accountExternalIds().insert(toAccountExternalIds(toAdd));

    ObjectId newRev =
        updateNoteMap(
            o -> {
              for (ExternalId.Key extIdKey : toDelete) {
                remove(o.rw(), o.noteMap(), extIdKey, null);
              }

              for (ExternalId extId : toAdd) {
                insert(o.rw(), o.ins(), o.noteMap(), extId);
              }
            });
    externalIdCache.onReplaceByKeys(newRev, toDelete, toAdd);
  }

  /**
   * Replaces an external ID.
   *
   * @throws IllegalStateException is thrown if the specified external IDs belong to different
   *     accounts.
   */
  public void replace(ReviewDb db, ExternalId toDelete, ExternalId toAdd)
      throws IOException, ConfigInvalidException, OrmException {
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
   * @throws IllegalStateException is thrown if the specified external IDs belong to different
   *     accounts.
   */
  public void replace(ReviewDb db, Collection<ExternalId> toDelete, Collection<ExternalId> toAdd)
      throws IOException, ConfigInvalidException, OrmException {
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

  /**
   * Inserts a new external ID and sets it in the note map.
   *
   * <p>If the external ID already exists, the insert fails with {@link OrmDuplicateKeyException}.
   */
  public static void insert(RevWalk rw, ObjectInserter ins, NoteMap noteMap, ExternalId extId)
      throws OrmDuplicateKeyException, ConfigInvalidException, IOException {
    if (noteMap.contains(extId.key().sha1())) {
      throw new OrmDuplicateKeyException(
          String.format("external id %s already exists", extId.key().get()));
    }
    upsert(rw, ins, noteMap, extId);
  }

  /**
   * Insert or updates an new external ID and sets it in the note map.
   *
   * <p>If the external ID already exists it is overwritten.
   */
  public static void upsert(RevWalk rw, ObjectInserter ins, NoteMap noteMap, ExternalId extId)
      throws IOException, ConfigInvalidException {
    ObjectId noteId = extId.key().sha1();
    Config c = new Config();
    if (noteMap.contains(extId.key().sha1())) {
      byte[] raw =
          rw.getObjectReader().open(noteMap.get(noteId), OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
      try {
        c.fromText(new String(raw, UTF_8));
      } catch (ConfigInvalidException e) {
        throw new ConfigInvalidException(
            String.format("Invalid external id config for note %s: %s", noteId, e.getMessage()));
      }
    }
    extId.writeToConfig(c);
    byte[] raw = c.toText().getBytes(UTF_8);
    ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
    noteMap.set(noteId, dataBlob);
  }

  /**
   * Removes an external ID from the note map.
   *
   * @throws IllegalStateException is thrown if there is an existing external ID that has the same
   *     key, but otherwise doesn't match the specified external ID.
   */
  public static void remove(RevWalk rw, NoteMap noteMap, ExternalId extId)
      throws IOException, ConfigInvalidException {
    ObjectId noteId = extId.key().sha1();
    if (!noteMap.contains(noteId)) {
      return;
    }

    byte[] raw =
        rw.getObjectReader().open(noteMap.get(noteId), OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
    ExternalId actualExtId = ExternalId.parse(noteId.name(), raw);
    checkState(
        extId.equals(actualExtId),
        "external id %s should be removed, but it's not matching the actual external id %s",
        extId.toString(),
        actualExtId.toString());
    noteMap.remove(noteId);
  }

  /**
   * Removes an external ID from the note map by external ID key.
   *
   * @throws IllegalStateException is thrown if an expected account ID is provided and an external
   *     ID with the specified key exists, but belongs to another account.
   */
  private static void remove(
      RevWalk rw, NoteMap noteMap, ExternalId.Key extIdKey, Account.Id expectedAccountId)
      throws IOException, ConfigInvalidException {
    ObjectId noteId = extIdKey.sha1();
    if (!noteMap.contains(noteId)) {
      return;
    }

    byte[] raw =
        rw.getObjectReader().open(noteMap.get(noteId), OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
    ExternalId extId = ExternalId.parse(noteId.name(), raw);
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
  }

  private ObjectId updateNoteMap(MyConsumer<OpenRepo> update)
      throws IOException, ConfigInvalidException, OrmException {
    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      return retryer.call(new TryNoteMapUpdate(repo, rw, ins, update));
    } catch (ExecutionException | RetryException e) {
      if (e.getCause() != null) {
        Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
        Throwables.throwIfInstanceOf(e.getCause(), ConfigInvalidException.class);
        Throwables.throwIfInstanceOf(e.getCause(), OrmException.class);
      }
      throw new OrmException(e);
    }
  }

  private ObjectId commit(
      Repository repo, RevWalk rw, ObjectInserter ins, ObjectId rev, NoteMap noteMap)
      throws IOException {
    return commit(repo, rw, ins, rev, noteMap, COMMIT_MSG, committerIdent, authorIdent);
  }

  /** Commits updates to the external IDs. */
  public static ObjectId commit(
      Repository repo,
      RevWalk rw,
      ObjectInserter ins,
      ObjectId rev,
      NoteMap noteMap,
      String commitMessage,
      PersonIdent committerIdent,
      PersonIdent authorIdent)
      throws IOException {
    CommitBuilder cb = new CommitBuilder();
    cb.setMessage(commitMessage);
    cb.setTreeId(noteMap.writeTree(ins));
    cb.setAuthor(authorIdent);
    cb.setCommitter(committerIdent);
    if (!rev.equals(ObjectId.zeroId())) {
      cb.setParentId(rev);
    } else {
      cb.setParentIds(); // Ref is currently nonexistent, commit has no parents.
    }
    if (cb.getTreeId() == null) {
      if (rev.equals(ObjectId.zeroId())) {
        cb.setTreeId(emptyTree(ins)); // No parent, assume empty tree.
      } else {
        RevCommit p = rw.parseCommit(rev);
        cb.setTreeId(p.getTree()); // Copy tree from parent.
      }
    }
    ObjectId commitId = ins.insert(cb);
    ins.flush();

    RefUpdate u = repo.updateRef(RefNames.REFS_EXTERNAL_IDS);
    u.setRefLogIdent(committerIdent);
    u.setRefLogMessage("Update external IDs", false);
    u.setExpectedOldObjectId(rev);
    u.setNewObjectId(commitId);
    RefUpdate.Result res = u.update();
    switch (res) {
      case NEW:
      case FAST_FORWARD:
      case NO_CHANGE:
      case RENAMED:
      case FORCED:
        break;
      case LOCK_FAILURE:
        throw new LockFailureException("Updating external IDs failed with " + res);
      case IO_FAILURE:
      case NOT_ATTEMPTED:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      default:
        throw new IOException("Updating external IDs failed with " + res);
    }
    return commitId;
  }

  private static ObjectId emptyTree(ObjectInserter ins) throws IOException {
    return ins.insert(OBJ_TREE, new byte[] {});
  }

  @FunctionalInterface
  private static interface MyConsumer<T> {
    void accept(T t) throws IOException, ConfigInvalidException, OrmException;
  }

  @AutoValue
  abstract static class OpenRepo {
    static OpenRepo create(Repository repo, RevWalk rw, ObjectInserter ins, NoteMap noteMap) {
      return new AutoValue_ExternalIdsUpdate_OpenRepo(repo, rw, ins, noteMap);
    }

    abstract Repository repo();

    abstract RevWalk rw();

    abstract ObjectInserter ins();

    abstract NoteMap noteMap();
  }

  private class TryNoteMapUpdate implements Callable<ObjectId> {
    private final Repository repo;
    private final RevWalk rw;
    private final ObjectInserter ins;
    private final MyConsumer<OpenRepo> update;

    private TryNoteMapUpdate(
        Repository repo, RevWalk rw, ObjectInserter ins, MyConsumer<OpenRepo> update) {
      this.repo = repo;
      this.rw = rw;
      this.ins = ins;
      this.update = update;
    }

    @Override
    public ObjectId call() throws Exception {
      ObjectId rev = readRevision(repo);

      afterReadRevision.run();

      NoteMap noteMap = readNoteMap(rw, rev);
      update.accept(OpenRepo.create(repo, rw, ins, noteMap));

      return commit(repo, rw, ins, rev, noteMap);
    }
  }
}
