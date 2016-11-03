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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepoRefCache;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Helper to migrate the {@link PrimaryStorage} of individual changes. */
@Singleton
public class PrimaryStorageMigrator {
  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final ChangeRebuilder rebuilder;

  private final long skewMs;
  private final long timeoutMs;
  private final long concurrentWriterTimeoutMs;

  @Inject
  PrimaryStorageMigrator(@GerritServerConfig Config cfg,
      Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      ChangeRebuilder rebuilder) {
    this.db = db;
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.rebuilder = rebuilder;
    skewMs = NoteDbChangeState.getReadOnlySkew(cfg);

    String s = "notedb";
    String primaryStorageMigrationTimeout = "primaryStorageMigrationTimeout";
    String concurrentWriterTimeout = "concurrentWriterTimeout";
    timeoutMs = cfg.getTimeUnit(
        s, null, primaryStorageMigrationTimeout,
        MILLISECONDS.convert(60, SECONDS), MILLISECONDS);
    concurrentWriterTimeoutMs = cfg.getTimeUnit(
        s, null, concurrentWriterTimeout,
        MILLISECONDS.convert(10, SECONDS), MILLISECONDS);

    checkArgument(timeoutMs >= 2 * concurrentWriterTimeoutMs,
        "%s.%s=%s must be at least double %s.%s=%s",
        s, primaryStorageMigrationTimeout,
        cfg.getString(s, null, primaryStorageMigrationTimeout),
        s, concurrentWriterTimeout,
        cfg.getString(s, null, concurrentWriterTimeout));
  }

  public void migrateToNoteDbPrimary(Change.Id id)
      throws OrmException, IOException {
    // Since there are multiple non-atomic steps in this method, we need to
    // consider what happens when there is another writer concurrent with the
    // thread executing this method.
    //
    // Let:
    // * OR = other writer writes noteDbState & new data to ReviewDb
    // * ON = other writer writes to NoteDb
    // * MRO = migrator sets state to read-only
    // * MR = ensureRebuilt writes rebuilt noteDbState to ReviewDb
    // * MN = ensureRebuilt writes rebuilt state to NoteDb
    //
    // Consider all the interleavings of these operations.
    //
    // * OR,ON,MRO,...
    //   Other writer completes before migrator begins; this is not a concurrent
    //   write.
    // * MRO,...,OR
    //   OR will fail, since it atomically checks that the noteDbState is not
    //   read-only before proceeding. This results in an exception, but not a
    //   concurrent write.
    //
    // Thus all the "interesting" interleavings start with OR,MRO, and differ on
    // where ON falls relative to MR/MN.
    //
    // * OR,MRO,ON,MR,MN
    //   The other NoteDb write succeeds despite the noteDbState being
    //   read-only. Because the read-only state from MRO includes the update
    //   from OR, the change is up-to-date at this point. Thus MR,MN is a no-op.
    //   The end result is an up-to-date, read-only change.
    //
    // * OR,MRO,MR,ON,MN
    //   The change is out-of-date when ensureRebuilt begins, because OR
    //   succeeded but the corresponding ON has not happened yet. ON will
    //   succeed, because there have been no intervening NoteDb writes. MN will
    //   fail, because ON updated the state in NoteDb to something other than
    //   what MR claimed. This leaves the change in an out-of-date, read-only
    //   state.
    //
    //   If this method threw an exception in this case, the change would
    //   eventually switch back to read-write when the read-only lease expires,
    //   so this situation is recoverable. However, it would be inconvenient for
    //   a change to be read-only for so long, and for the caller to have to
    //   retry.
    //
    //   Thus, as an optimization, we have a retry loop that attempts
    //   ensureRebuilt while still holding the same read-only lease. This
    //   effectively results in the interleaving OR,MR,ON,MR,MN; in contrast
    //   with the previous case, here, MR/MN actually rebuilds the change. In
    //   the case of a write failure or another concurrent write, MR/MN might
    //   fail and get retried again. If it exceeds the maximum number of
    //   retries, an exception is thrown, so the caller still has to retry.
    //   TODO(dborowitz): Implement said loop.
    //
    // * OR,MRO,MR,MN,ON
    //   The change is out-of-date when ensureRebuilt begins. The change is
    //   rebuilt, leaving a new state in NoteDb. ON will fail, because the old
    //   NoteDb state has changed since the ref state was read when the update
    //   began (prior to OR). This results in an exception from ON, but the end
    //   result is still an up-to-date, read-only change.

    Change readOnlyChange = setReadOnly(id); // MRO
    if (readOnlyChange == null) {
      return; // Already migrated.
    }
    NoteDbChangeState readOnlyState = NoteDbChangeState.parse(readOnlyChange);

    readOnlyState =
        ensureRebuilt(readOnlyChange.getProject(), id, readOnlyState); // MR,MN

    // At this point, the noteDbState in ReviewDb is read-only, and it is
    // guaranteed to match the state actually in NoteDb. Now it is safe to set
    // the primary storage to NoteDb.

    setPrimaryStorageNoteDb(id, readOnlyState);
  }

  private Change setReadOnly(Change.Id id) throws OrmException {
    AtomicBoolean alreadyMigrated = new AtomicBoolean(false);
    Change result = db().changes().atomicUpdate(id, new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        NoteDbChangeState state = NoteDbChangeState.parse(change);
        if (state == null) {
          // Could rebuild the change here, but that's more complexity, and this
          // really shouldn't happen.
          throw new OrmRuntimeException(
              "change " + id + " has no note_db_state; rebuild it first");
        }
        // If the change is already read-only, then the lease is held by another
        // (likely failed) migrator thread. Fail early, as we can't take over
        // the lease.
        NoteDbChangeState.checkNotReadOnly(change, skewMs);
        if (state.getPrimaryStorage() != PrimaryStorage.NOTE_DB) {
          Timestamp now = TimeUtil.nowTs();
          Timestamp until = new Timestamp(now.getTime() + timeoutMs);
          change.setNoteDbState(state.withReadOnlyUntil(until).toString());
        } else {
          alreadyMigrated.set(true);
        }
        return change;
      }
    });
    return alreadyMigrated.get() ? null : result;
  }

  private NoteDbChangeState ensureRebuilt(Project.NameKey project, Change.Id id,
      NoteDbChangeState readOnlyState)
      throws IOException, OrmException, RepositoryNotFoundException {
    try (Repository changeRepo = repoManager.openRepository(project);
        Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      if (!readOnlyState.isUpToDate(
          new RepoRefCache(changeRepo), new RepoRefCache(allUsersRepo))) {
        NoteDbUpdateManager.Result r =
            rebuilder.rebuildEvenIfReadOnly(db(), id);
        checkState(
            r.newState().getReadOnlyUntil()
                .equals(readOnlyState.getReadOnlyUntil()),
            "state after rebuilding has different read-only lease: %s != %s",
            r.newState(), readOnlyState);
        readOnlyState = r.newState();
      }
    } catch (NoSuchChangeException e) {
      throw new OrmException(e);
    }
    return readOnlyState;
  }

  private void setPrimaryStorageNoteDb(Change.Id id,
      NoteDbChangeState expectedState) throws OrmException {
    db().changes().atomicUpdate(id, new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        NoteDbChangeState state = NoteDbChangeState.parse(change);
        if (!Objects.equals(state, expectedState)) {
          throw new OrmRuntimeException(badState(state, expectedState));
        }
        Timestamp until = state.getReadOnlyUntil().get();
        if (TimeUtil.nowTs().after(until)) {
          throw new OrmRuntimeException(
              "read-only lease on change " + id + " expired at " + until);
        }
        change.setNoteDbState(NoteDbChangeState.NOTE_DB_PRIMARY_STATE);
        return change;
      }
    });
  }

  private ReviewDb db() {
    return ReviewDbUtil.unwrapDb(db.get());
  }

  private String badState(NoteDbChangeState actual,
      NoteDbChangeState expected) {
    return "state changed unexpectedly: " + actual + " != " + expected;
  }
}
