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
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilderImpl;
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
  private final ChangeRebuilderImpl rebuilder;

  private final long skewMs;
  private final long timeoutMs;
  private final long concurrentWriterTimeoutMs;

  @Inject
  PrimaryStorageMigrator(@GerritServerConfig Config cfg,
      Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      ChangeRebuilderImpl rebuilder) {
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
    Change readOnlyChange = setReadOnly(id);
    if (readOnlyChange == null) {
      return; // Already migrated.
    }
    NoteDbChangeState readOnlyState = NoteDbChangeState.parse(readOnlyChange);

    waitForConcurrentWriters(id, readOnlyState);

    // From this point on, we are sure that no other writers are going to modify
    // ReviewDb or NoteDb for the change before our lease expires.

    readOnlyState =
        ensureRebuilt(readOnlyChange.getProject(), id, readOnlyState);
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
        checkNotReadOnly(change);
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

  private void waitForConcurrentWriters(Change.Id id,
      NoteDbChangeState readOnlyState) throws OrmException {
    try {
      MILLISECONDS.sleep(concurrentWriterTimeoutMs);
    } catch (InterruptedException e) {
      throw new OrmException("interrupted waiting for concurrent writers", e);
    }
    NoteDbChangeState stateAfterSleep =
        NoteDbChangeState.parse(readReviewDb(id));
    if (!Objects.equals(stateAfterSleep, readOnlyState)) {
      throw new OrmException(badState(stateAfterSleep, readOnlyState));
    }
  }

  private NoteDbChangeState ensureRebuilt(Project.NameKey project, Change.Id id,
      NoteDbChangeState readOnlyState)
      throws IOException, OrmException, RepositoryNotFoundException {
    try (Repository changeRepo = repoManager.openRepository(project);
        Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      if (!readOnlyState.isUpToDate(
          new RepoRefCache(changeRepo), new RepoRefCache(allUsersRepo))) {
        NoteDbUpdateManager.Result r = rebuilder.rebuild(db(), id, false);
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

  private Change readReviewDb(Change.Id id) throws OrmException {
    Change c = db().changes().get(id);
    if (c == null) {
      throw new OrmException("change " + id + " not found in ReviewDb");
    }
    return c;
  }

  private String badState(NoteDbChangeState actual,
      NoteDbChangeState expected) {
    return "state changed unexpectedly: " + actual + " != " + expected;
  }

  private void checkNotReadOnly(Change c) {
    try {
      NoteDbChangeState.checkNotReadOnly(c, skewMs);
    } catch (OrmException e) {
      throw new OrmRuntimeException(e.getMessage(), e);
    }
  }
}
