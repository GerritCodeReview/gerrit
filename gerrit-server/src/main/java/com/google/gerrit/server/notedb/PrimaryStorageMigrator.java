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
import static com.google.common.base.Throwables.getStackTraceAsString;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Helper to migrate the {@link PrimaryStorage} of individual changes. */
@Singleton
public class PrimaryStorageMigrator {
  public enum Status {
    /** Change already has the intended status after the migration. */
    ALREADY_MIGRATED,

    /** Change was successfully migrated. */
    MIGRATED,

    /**
     * An error occurred during migration.
     * <p>
     * The migrator was able to release the read-only lease, so the change is no
     * longer in a read-only state.
     */
    ERROR,

    /**
     * An error occurred during migration, and the attempt to release the
     * read-only lease on the change also failed, so the change may still be in
     * a read-only state.
     */
    ERROR_MAY_BE_READ_ONLY;
  }

  @AutoValue
  public abstract static class Result {
    private static Result create(Change.Id id, Status status) {
      checkArgument(status != Status.ERROR, "use error(String, Throwable)");
      return new AutoValue_PrimaryStorageMigrator_Result(id, status, null);
    }

    private static Result error(Change.Id id, @Nullable String message,
        Throwable t) {
      String stackTrace = getStackTraceAsString(t);
      return new AutoValue_PrimaryStorageMigrator_Result(
          id, Status.ERROR,
          message != null ? message + "\n" + stackTrace : stackTrace);
    }

    private static Result errorMayBeReadOnly(Result orig, Throwable t) {
      return new AutoValue_PrimaryStorageMigrator_Result(
          orig.id(), Status.ERROR_MAY_BE_READ_ONLY,
          "Attempt to release the read-only lease failed\n"
              + getStackTraceAsString(t)
              + "\nOriginal error follows\n"
              + orig.message());
    }

    public abstract Change.Id id();
    public abstract Status status();
    @Nullable public abstract String message();
  }

  private class Handle {
    Change.Id id;
    Project.NameKey project;
    NoteDbChangeState readOnlyState;
    Timestamp concurrentWriterDeadline;
    Result result;

    void waitForConcurrentWriters() throws OrmException, InterruptedException {
      Timestamp now = TimeUtil.nowTs();
      while (now.before(concurrentWriterDeadline)) {
        Thread.sleep(concurrentWriterDeadline.getTime() - now.getTime());
        now = TimeUtil.nowTs();
      }
      NoteDbChangeState stateAfterSleep =
          NoteDbChangeState.parse(readReviewDb(id));
      if (!Objects.equals(stateAfterSleep, readOnlyState)) {
        throw new OrmException(badState(stateAfterSleep, readOnlyState));
      }
    }
  }

  private static class MigrationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MigrationException(String msg) {
      super(msg);
    }

    public MigrationException(String msg, Throwable t) {
      super(msg, t);
    }
  }

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
    timeoutMs = cfg.getTimeUnit(s, null, primaryStorageMigrationTimeout,
        MILLISECONDS.convert(60, SECONDS), MILLISECONDS);
    concurrentWriterTimeoutMs =
        cfg.getTimeUnit(s, null, concurrentWriterTimeout,
            MILLISECONDS.convert(10, SECONDS), MILLISECONDS);

    checkArgument(timeoutMs >= 2 * concurrentWriterTimeoutMs,
        "%s.%s=%s must be at least double %s.%s=%s", s,
        primaryStorageMigrationTimeout,
        cfg.getString(s, null, primaryStorageMigrationTimeout), s,
        concurrentWriterTimeout,
        cfg.getString(s, null, concurrentWriterTimeout));
  }

  public Result migrateToNoteDbPrimary(Change.Id id) {
    return migrateToNoteDbPrimary(ImmutableList.of(id)).get(0);
  }

  public List<Result> migrateToNoteDbPrimary(Collection<Change.Id> ids) {
    // Mark all changes read-only first, so we can wait for them in parallel.
    List<Handle> handles = setReadOnly(ids);
    migrateToNoteDbPrimary(handles);
    releaseLeasesForFailedChanges(handles);
    return handles.stream().map(h -> h.result).collect(toList());
  }

  private List<Handle> setReadOnly(Collection<Change.Id> ids) {
    List<Handle> handles = new ArrayList<>(ids.size());
    for (Change.Id id : ids) {
      Handle h = new Handle();
      h.id = id;
      handles.add(h);

      Change c;
      try {
        c = setReadOnly(id);
        if (c == null) {
          h.result = Result.create(h.id, Status.ALREADY_MIGRATED);
          continue;
        }
        h.project = c.getProject();
      } catch (OrmException e) {
        h.result = Result.error(h.id, "Error marking read-only", e);
        continue;
      }

      h.readOnlyState = NoteDbChangeState.parse(c);
      h.concurrentWriterDeadline =
          new Timestamp(TimeUtil.nowMs() + concurrentWriterTimeoutMs);
    }
    return handles;
  }

  private Change setReadOnly(Change.Id id) throws OrmException {
    AtomicBoolean alreadyMigrated = new AtomicBoolean(false);
    try {
      Change result =
          db().changes().atomicUpdate(id, new AtomicUpdate<Change>() {
            @Override
            public Change update(Change c) {
              NoteDbChangeState state = NoteDbChangeState.parse(c);
              if (state == null) {
                // Could rebuild the change here, but that's more complexity,
                // and this really shouldn't happen.
                throw new MigrationException(
                    "change " + id + " has no note_db_state; rebuild it first");
              }
              try {
                NoteDbChangeState.checkNotReadOnly(c, skewMs);
              } catch (OrmException e) {
                throw new MigrationException(e.getMessage(), e);
              }
              if (state.getPrimaryStorage() != PrimaryStorage.NOTE_DB) {
                Timestamp now = TimeUtil.nowTs();
                Timestamp until = new Timestamp(now.getTime() + timeoutMs);
                c.setNoteDbState(state.withReadOnlyUntil(until).toString());
              } else {
                alreadyMigrated.set(true);
              }
              return c;
            }
          });
      return alreadyMigrated.get() ? null : result;
    } catch (MigrationException e) {
      throw new OrmException(e.getMessage(), e);
    }
  }

  private void migrateToNoteDbPrimary(List<Handle> handles) {
    for (Handle h : handles) {
      if (h.result != null) {
        continue;
      }
      try {
        h.waitForConcurrentWriters();
      } catch (OrmException | InterruptedException e) {
        h.result =
            Result.error(h.id, "Error waiting for concurrent writers", e);
        continue;
      }

      // From this point on, we are sure that no other writers are going to
      // modify ReviewDb or NoteDb for this change before our lease expires.

      try {
        h.readOnlyState = ensureRebuilt(h.project, h.id, h.readOnlyState);
      } catch (IOException | OrmException e) {
        h.result = Result.error(h.id, "Error ensuring change is rebuilt", e);
        continue;
      }
      try {
        setPrimaryStorageNoteDb(h.id, h.readOnlyState);
      } catch (OrmException e) {
        h.result = Result.error(h.id, "Error setting primary storage", e);
        continue;
      }
      h.result = Result.create(h.id, Status.MIGRATED);
    }
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
    try {
      db().changes().atomicUpdate(id, new AtomicUpdate<Change>() {
        @Override
        public Change update(Change change) {
          NoteDbChangeState state = NoteDbChangeState.parse(change);
          if (!Objects.equals(state, expectedState)) {
            throw new MigrationException(badState(state, expectedState));
          }
          Timestamp until = state.getReadOnlyUntil().get();
          if (TimeUtil.nowTs().after(until)) {
            throw new MigrationException(
                "read-only lease on change " + id + " expired at " + until);
          }
          change.setNoteDbState(NoteDbChangeState.NOTE_DB_PRIMARY_STATE);
          return change;
        }
      });
    } catch (MigrationException e) {
      throw new OrmException(e.getMessage());
    }
  }

  private void releaseLeasesForFailedChanges(List<Handle> handles) {
    for (Handle h : handles) {
      checkState(h.result != null, "no result for %s", h.id);
      if (h.result.status() != Status.ERROR) {
        continue; // Succeeded, nothing to do.
      } else if (h.readOnlyState == null) {
        // Error occurred before recording state in handle, nothing to do.
        continue;
      }
      try {
        db().changes().atomicUpdate(h.id, new AtomicUpdate<Change>() {
          @Override
          public Change update(Change c) {
            if (!h.readOnlyState.toString().equals(c.getNoteDbState())) {
              // This is bad, someone other than us touched the state even
              // though they weren't supposed to. Log it, but don't do anything
              // else, as touching it might just make it worse.
              throw new MigrationException(String.format(
                  "state for {} changed unexpectedly during migration process:"
                      + " {} != {}",
                  h.id, h.readOnlyState, c.getNoteDbState()));
            }
            c.setNoteDbState(h.readOnlyState.withoutReadOnlyUntil().toString());
            return c;
          }
        });
      } catch (MigrationException | OrmException e) {
        h.result = Result.errorMayBeReadOnly(h.result, e);
      }
    }
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
}
