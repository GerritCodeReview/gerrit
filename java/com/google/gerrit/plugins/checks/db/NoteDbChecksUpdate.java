// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.db;

import static com.google.gerrit.plugins.checks.CheckerRef.checksRef;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Throwables;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

public class NoteDbChecksUpdate implements ChecksUpdate {
  public static final Ordering<NoteDbCheck> CHECK_ORDER =
      new Ordering<NoteDbCheck>() {
        @Override
        public int compare(NoteDbCheck c1, NoteDbCheck c2) {
          return ComparisonChain.start().compare(c1.checkerUUID, c2.checkerUUID).result();
        }
      };

  interface Factory {
    NoteDbChecksUpdate create(IdentifiedUser currentUser);

    NoteDbChecksUpdate createWithServerIdent();
  }

  private final GitRepositoryManager repoManager;
  private final PersonIdent personIdent;
  private final GitReferenceUpdated gitRefUpdated;
  private final RetryHelper retryHelper;
  private final ChangeNoteUtil noteUtil;
  private final Optional<IdentifiedUser> currentUser;

  @AssistedInject
  NoteDbChecksUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      ChangeNoteUtil noteUtil,
      @GerritPersonIdent PersonIdent personIdent) {
    this(repoManager, gitRefUpdated, retryHelper, noteUtil, personIdent, Optional.empty());
  }

  @AssistedInject
  NoteDbChecksUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      ChangeNoteUtil noteUtil,
      @GerritPersonIdent PersonIdent personIdent,
      @Assisted IdentifiedUser currentUser) {
    this(repoManager, gitRefUpdated, retryHelper, noteUtil, personIdent, Optional.of(currentUser));
  }

  private NoteDbChecksUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      ChangeNoteUtil noteUtil,
      @GerritPersonIdent PersonIdent personIdent,
      Optional<IdentifiedUser> currentUser) {
    this.repoManager = repoManager;
    this.gitRefUpdated = gitRefUpdated;
    this.retryHelper = retryHelper;
    this.noteUtil = noteUtil;
    this.currentUser = currentUser;
    this.personIdent = personIdent;
  }

  @Override
  public Check createCheck(CheckKey checkKey, CheckUpdate checkUpdate)
      throws OrmDuplicateKeyException, IOException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.PLUGIN_UPDATE,
          () -> upsertCheckInNoteDb(checkKey, checkUpdate, true),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, OrmDuplicateKeyException.class);
      Throwables.throwIfInstanceOf(e, IOException.class);
      throw new IOException(e);
    }
  }

  @Override
  public Check updateCheck(CheckKey checkKey, CheckUpdate checkUpdate) throws IOException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.PLUGIN_UPDATE,
          () -> upsertCheckInNoteDb(checkKey, checkUpdate, false),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      throw new IOException(e);
    }
  }

  private Check upsertCheckInNoteDb(CheckKey checkKey, CheckUpdate checkUpdate, boolean allowCreate)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException, OrmException {
    try (Repository repo = repoManager.openRepository(checkKey.project());
        RevWalk rw = new RevWalk(repo);
        ObjectInserter objectInserter = repo.newObjectInserter()) {
      Ref checkRef = repo.getRefDatabase().exactRef(checksRef(checkKey.patchSet().getParentKey()));
      ObjectId parent = checkRef == null ? ObjectId.zeroId() : checkRef.getObjectId();
      CommitBuilder cb;
      String message;
      if (allowCreate) {
        message = "Insert check " + checkKey.checkerUUID();
        cb = commitBuilder(message, parent);
        insertIntoNotesMap(checkKey, checkUpdate, repo, rw, objectInserter, parent, cb);
      } else {
        message = "Update check " + checkKey.checkerUUID();
        cb = commitBuilder(message, parent);
        boolean dirty = updateNotesMap(checkKey, checkUpdate, repo, rw, objectInserter, parent, cb);
        if (!dirty) {
          // This update is a NoOp, so omit writing a commit with the same tree.
          // TODO(gerrit-team): Return entity
          return null;
        }
      }

      ObjectId newCommitId = objectInserter.insert(cb);
      objectInserter.flush();

      String refName = CheckerRef.checksRef(checkKey.patchSet().getParentKey());
      RefUpdate refUpdate = repo.updateRef(refName);
      refUpdate.setForceUpdate(true);
      refUpdate.setExpectedOldObjectId(parent);
      refUpdate.setNewObjectId(newCommitId);
      refUpdate.setRefLogIdent(personIdent);
      refUpdate.setRefLogMessage(message, false);
      refUpdate.update();
      RefUpdateUtil.checkResult(refUpdate);

      gitRefUpdated.fire(
          checkKey.project(), refUpdate, currentUser.map(user -> user.state()).orElse(null));
      // TODO(gerrit-team): Return entity
      return null;
    }
  }

  private void insertIntoNotesMap(
      CheckKey checkKey,
      CheckUpdate checkUpdate,
      Repository repo,
      RevWalk rw,
      ObjectInserter ins,
      ObjectId curr,
      CommitBuilder cb)
      throws ConfigInvalidException, IOException, OrmDuplicateKeyException {
    Ref patchSetRef = repo.exactRef(RefNames.patchSetRef(checkKey.patchSet()));
    if (patchSetRef == null) {
      throw new IOException("patchset " + checkKey.patchSet() + "not found");
    }
    RevId revId = new RevId(patchSetRef.getObjectId().name());

    // Read a fresh copy of the notes map
    Map<RevId, List<NoteDbCheck>> newNotes = getRevisionNoteByRevId(rw, curr);

    // Insert
    if (!newNotes.containsKey(revId)) {
      newNotes.put(revId, new ArrayList<>());
    }
    List<NoteDbCheck> checksForRevision = newNotes.get(revId);
    Optional<NoteDbCheck> check =
        checksForRevision
            .stream()
            .filter(c -> c.checkerUUID.equals(checkKey.checkerUUID()))
            .findAny();
    if (check.isPresent()) {
      throw new OrmDuplicateKeyException(checkKey.checkerUUID() + " exists");
    }
    NoteDbCheck noteDbCheck = NoteDbCheck.fromCheckCreation(checkKey, checkUpdate);
    noteDbCheck.created = Timestamp.from(TimeUtil.now());
    noteDbCheck.updated = noteDbCheck.created;
    checksForRevision.add(noteDbCheck);

    writeNotesMap(newNotes, cb, ins);
  }

  private boolean updateNotesMap(
      CheckKey checkKey,
      CheckUpdate checkUpdate,
      Repository repo,
      RevWalk rw,
      ObjectInserter ins,
      ObjectId curr,
      CommitBuilder cb)
      throws ConfigInvalidException, IOException, OrmException {
    Ref patchSetRef = repo.exactRef(RefNames.patchSetRef(checkKey.patchSet()));
    if (patchSetRef == null) {
      throw new IOException("patchset " + checkKey.patchSet() + "not found");
    }
    RevId revId = new RevId(patchSetRef.getObjectId().name());

    // Read a fresh copy of the notes map
    Map<RevId, List<NoteDbCheck>> newNotes = getRevisionNoteByRevId(rw, curr);

    // Update in place
    List<NoteDbCheck> checksForRevision = newNotes.get(revId);
    if (checksForRevision == null || checksForRevision.isEmpty()) {
      throw new IOException("Not found: " + checkKey.checkerUUID());
    }

    Optional<NoteDbCheck> check =
        checksForRevision
            .stream()
            .filter(c -> c.checkerUUID.equals(checkKey.checkerUUID()))
            .findAny();
    if (!check.isPresent()) {
      throw new IOException("Not found: " + checkKey.checkerUUID());
    }

    boolean dirty = check.get().applyUpdate(checkUpdate);
    if (!dirty) {
      return false;
    }
    check.get().updated = Timestamp.from(TimeUtil.now());

    writeNotesMap(newNotes, cb, ins);
    return true;
  }

  private void writeNotesMap(
      Map<RevId, List<NoteDbCheck>> notesMap, CommitBuilder cb, ObjectInserter ins)
      throws IOException {
    CheckRevisionNoteMap<CheckRevisionNote> output = CheckRevisionNoteMap.emptyMap();
    for (Map.Entry<RevId, List<NoteDbCheck>> e : notesMap.entrySet()) {
      ObjectId id = ObjectId.fromString(e.getKey().get());
      byte[] data = toData(e.getValue());
      if (data.length != 0) {
        ObjectId dataBlob = ins.insert(OBJ_BLOB, data);
        output.noteMap.set(id, dataBlob);
      }
    }
    cb.setTreeId(output.noteMap.writeTree(ins));
  }

  private Map<RevId, List<NoteDbCheck>> getRevisionNoteByRevId(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, IOException {
    CheckRevisionNoteMap<CheckRevisionNote> existingNotes = getRevisionNoteMap(rw, curr);

    // Generate a list with all current checks keyed by patch set
    Map<RevId, List<NoteDbCheck>> newNotes =
        Maps.newHashMapWithExpectedSize(existingNotes.revisionNotes.size());
    for (Map.Entry<RevId, CheckRevisionNote> e : existingNotes.revisionNotes.entrySet()) {
      newNotes.put(e.getKey(), new ArrayList<>(e.getValue().getComments()));
    }
    return newNotes;
  }

  private CheckRevisionNoteMap<CheckRevisionNote> getRevisionNoteMap(RevWalk rw, ObjectId curr)
      throws ConfigInvalidException, IOException {
    if (curr.equals(ObjectId.zeroId())) {
      return CheckRevisionNoteMap.emptyMap();
    }
    NoteMap noteMap;
    if (!curr.equals(ObjectId.zeroId())) {
      noteMap = NoteMap.read(rw.getObjectReader(), rw.parseCommit(curr));
    } else {
      noteMap = NoteMap.newEmptyMap();
    }
    return CheckRevisionNoteMap.parseChecks(
        noteUtil.getChangeNoteJson(), rw.getObjectReader(), noteMap);
  }

  private byte[] toData(List<NoteDbCheck> checks) throws IOException {
    if (checks.isEmpty()) {
      return new byte[0];
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (OutputStreamWriter osw = new OutputStreamWriter(out, UTF_8)) {
      noteUtil.getChangeNoteJson().getGson().toJson(CHECK_ORDER.sortedCopy(checks), osw);
    }
    return out.toByteArray();
  }

  private CommitBuilder commitBuilder(String message, ObjectId parent) {
    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parent);
    cb.setAuthor(personIdent);
    cb.setCommitter(personIdent);
    cb.setMessage(message);
    return cb;
  }
}
