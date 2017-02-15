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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
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
  public static ObjectId readRevision(Repository repo) throws IOException {
    Ref ref = repo.exactRef(RefNames.REFS_EXTERNAL_IDS);
    return ref != null ? ref.getObjectId() : ObjectId.zeroId();
  }

  public static NoteMap readNoteMap(RevWalk rw, ObjectId rev) throws IOException {
    if (!rev.equals(ObjectId.zeroId())) {
      return NoteMap.read(rw.getObjectReader(), rw.parseCommit(rev));
    }
    return NoteMap.newEmptyMap();
  }

  private static final int MAX_NOTE_SZ = 25 << 20;
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
    private final PersonIdent serverIdent;

    @Inject
    public Server(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        @GerritPersonIdent PersonIdent serverIdent) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.serverIdent = serverIdent;
    }

    public ExternalIdsUpdate create() {
      return new ExternalIdsUpdate(repoManager, allUsersName, serverIdent, serverIdent);
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
    private final PersonIdent serverIdent;
    private final Provider<IdentifiedUser> identifiedUser;

    @Inject
    public User(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        @GerritPersonIdent PersonIdent serverIdent,
        Provider<IdentifiedUser> identifiedUser) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.serverIdent = serverIdent;
      this.identifiedUser = identifiedUser;
    }

    public ExternalIdsUpdate create() {
      return new ExternalIdsUpdate(
          repoManager, allUsersName, serverIdent, createPersonIdent(identifiedUser.get()));
    }

    private PersonIdent createPersonIdent(IdentifiedUser user) {
      return user.newCommitterIdent(serverIdent.getWhen(), serverIdent.getTimeZone());
    }
  }

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final PersonIdent committerIdent;
  private final PersonIdent authorIdent;

  private ExternalIdsUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      PersonIdent committerIdent,
      PersonIdent authorIdent) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.committerIdent = committerIdent;
    this.authorIdent = authorIdent;
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
  public synchronized void insert(ReviewDb db, Collection<ExternalId> extIds)
      throws IOException, OrmException {
    db.accountExternalIds().insert(toAccountExternalIds(extIds));

    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId rev = readRevision(repo);

      NoteMap noteMap = readNoteMap(rw, rev);
      for (ExternalId extId : extIds) {
        insert(ins, noteMap, extId);
      }

      commit(repo, rw, ins, rev, noteMap);
    }
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
  public synchronized void upsert(ReviewDb db, Collection<ExternalId> extIds)
      throws IOException, OrmException {
    db.accountExternalIds().upsert(toAccountExternalIds(extIds));

    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId rev = readRevision(repo);

      NoteMap noteMap = readNoteMap(rw, rev);
      for (ExternalId extId : extIds) {
        upsert(ins, noteMap, extId);
      }

      commit(repo, rw, ins, rev, noteMap);
    }
  }

  /**
   * Deletes an external ID.
   *
   * <p>The deletion fails with {@link IllegalStateException} if there is an existing external ID
   * that has the same key, but otherwise doesn't match the specified external ID.
   */
  public void delete(ReviewDb db, ExternalId extId)
      throws IOException, ConfigInvalidException, OrmException {
    delete(db, Collections.singleton(extId));
  }

  /**
   * Deletes external IDs.
   *
   * <p>The deletion fails with {@link IllegalStateException} if there is an existing external ID
   * that has the same key as any of the external IDs that should be deleted, but otherwise doesn't
   * match the that external ID.
   */
  public synchronized void delete(ReviewDb db, Collection<ExternalId> extIds)
      throws IOException, ConfigInvalidException, OrmException {
    db.accountExternalIds().delete(toAccountExternalIds(extIds));

    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId rev = readRevision(repo);

      NoteMap noteMap = readNoteMap(rw, rev);
      for (ExternalId extId : extIds) {
        remove(rw, noteMap, extId);
      }

      commit(repo, rw, ins, rev, noteMap);
    }
  }

  /**
   * Delete an external ID by key.
   *
   * <p>The external ID is only deleted if it belongs to the specified account. If it belongs to
   * another account the deletion fails with {@link IllegalStateException}.
   */
  public void delete(ReviewDb db, Account.Id accountId, ExternalId.Key extIdKey)
      throws IOException, ConfigInvalidException, OrmException {
    delete(db, accountId, Collections.singleton(extIdKey));
  }

  /**
   * Delete external IDs by external ID key.
   *
   * <p>The external IDs are only deleted if they belongs to the specified account. If any of the
   * external IDs belongs to another account the deletion fails with {@link IllegalStateException}.
   */
  public synchronized void delete(
      ReviewDb db, Account.Id accountId, Collection<ExternalId.Key> extIdKeys)
      throws IOException, ConfigInvalidException, OrmException {
    db.accountExternalIds().deleteKeys(toAccountExternalIdKeys(extIdKeys));

    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId rev = readRevision(repo);

      NoteMap noteMap = readNoteMap(rw, rev);
      for (ExternalId.Key extIdKey : extIdKeys) {
        remove(rw, noteMap, accountId, extIdKey);
      }

      commit(repo, rw, ins, rev, noteMap);
    }
  }

  /** Deletes all external IDs of the specified account. */
  public void deleteAll(ReviewDb db, Account.Id accountId)
      throws IOException, ConfigInvalidException, OrmException {
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
  public synchronized void replace(
      ReviewDb db,
      Account.Id accountId,
      Collection<ExternalId.Key> toDelete,
      Collection<ExternalId> toAdd)
      throws IOException, ConfigInvalidException, OrmException {
    checkSameAccount(toAdd, accountId);

    db.accountExternalIds().deleteKeys(toAccountExternalIdKeys(toDelete));
    db.accountExternalIds().insert(toAccountExternalIds(toAdd));

    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId rev = readRevision(repo);

      NoteMap noteMap = readNoteMap(rw, rev);
      for (ExternalId.Key extIdKey : toDelete) {
        remove(rw, noteMap, accountId, extIdKey);
      }

      for (ExternalId extId : toAdd) {
        insert(ins, noteMap, extId);
      }

      commit(repo, rw, ins, rev, noteMap);
    }
  }

  /**
   * Replaces an external ID.
   *
   * <p>If the specified external IDs belongs to different accounts the replacement fails with
   * {@link IllegalStateException}.
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
   * <p>If the specified external IDs belong to different accounts the replacement fails with {@link
   * IllegalStateException}.
   */
  public synchronized void replace(
      ReviewDb db, Collection<ExternalId> toDelete, Collection<ExternalId> toAdd)
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
  public static void insert(ObjectInserter ins, NoteMap noteMap, ExternalId extId)
      throws OrmDuplicateKeyException, IOException {
    if (noteMap.contains(extId.key().sha1())) {
      throw new OrmDuplicateKeyException(
          String.format("external id %s already exists", extId.key().get()));
    }
    upsert(ins, noteMap, extId);
  }

  /**
   * Insert or updates an new external ID and sets it in the note map.
   *
   * <p>If the external ID already exists it is overwritten.
   */
  private static void upsert(ObjectInserter ins, NoteMap noteMap, ExternalId extId)
      throws IOException {
    byte[] raw = extId.toString().getBytes(UTF_8);
    ObjectId dataBlob = ins.insert(OBJ_BLOB, raw);
    noteMap.set(extId.key().sha1(), dataBlob);
  }

  /**
   * Removes an external ID from the note map.
   *
   * <p>The removal fails with {@link IllegalStateException} if there is an existing external ID
   * that has the same key, but otherwise doesn't match the specified external ID.
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
        "external id %s should be removed," + " but it's not matching the actual external id %s",
        extId.toString(),
        actualExtId.toString());
    noteMap.remove(noteId);
  }

  /**
   * Removes an external ID from the note map by external ID key.
   *
   * <p>The external ID is only deleted if it belongs to the specified account. If the external IDs
   * belongs to another account the deletion fails with {@link IllegalStateException}.
   */
  private static void remove(
      RevWalk rw, NoteMap noteMap, Account.Id accountId, ExternalId.Key extIdKey)
      throws IOException, ConfigInvalidException {
    ObjectId noteId = extIdKey.sha1();
    if (!noteMap.contains(noteId)) {
      return;
    }

    byte[] raw =
        rw.getObjectReader().open(noteMap.get(noteId), OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
    ExternalId extId = ExternalId.parse(noteId.name(), raw);
    checkState(
        accountId.equals(extId.accountId()),
        "external id %s should be removed for account %s,"
            + " but external id belongs to account %s",
        extIdKey.toString(),
        accountId.get(),
        extId.accountId().get());
    noteMap.remove(noteId);
  }

  private void commit(
      Repository repo, RevWalk rw, ObjectInserter ins, ObjectId rev, NoteMap noteMap)
      throws IOException {
    commit(repo, rw, ins, rev, noteMap, COMMIT_MSG, authorIdent, committerIdent);
  }

  /** Commits updates to the external IDs. */
  public static void commit(
      Repository repo,
      RevWalk rw,
      ObjectInserter ins,
      ObjectId rev,
      NoteMap noteMap,
      String commitMessage,
      PersonIdent authorIdent,
      PersonIdent committerIdent)
      throws IOException {
    CommitBuilder cb = new CommitBuilder();
    cb.setMessage(commitMessage);
    cb.setTreeId(noteMap.writeTree(ins));
    cb.setAuthor(authorIdent);
    cb.setCommitter(new PersonIdent(committerIdent, TimeUtil.nowTs()));
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
      case IO_FAILURE:
      case LOCK_FAILURE:
      case NOT_ATTEMPTED:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      default:
        throw new IOException("Updating external IDs failed with " + res);
    }
  }

  private static ObjectId emptyTree(ObjectInserter ins) throws IOException {
    return ins.insert(OBJ_TREE, new byte[] {});
  }
}
