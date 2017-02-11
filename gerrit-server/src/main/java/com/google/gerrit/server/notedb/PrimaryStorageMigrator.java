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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
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
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutionException;
import java.util.Objects;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper to migrate the {@link PrimaryStorage} of individual changes. */
@Singleton
public class PrimaryStorageMigrator {
  private static final Logger log = LoggerFactory.getLogger(PrimaryStorageMigrator.class);

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final ChangeRebuilder rebuilder;

  private final long skewMs;
  private final long timeoutMs;
  private final Retryer<NoteDbChangeState> testEnsureRebuiltRetryer;

  @Inject
  PrimaryStorageMigrator(
      @GerritServerConfig Config cfg,
      Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      ChangeRebuilder rebuilder) {
    this(cfg, db, repoManager, allUsers, rebuilder, null);
  }

  @VisibleForTesting
  public PrimaryStorageMigrator(
      Config cfg,
      Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      ChangeRebuilder rebuilder,
      @Nullable Retryer<NoteDbChangeState> testEnsureRebuiltRetryer) {
    this.db = db;
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.rebuilder = rebuilder;
    this.testEnsureRebuiltRetryer = testEnsureRebuiltRetryer;
    skewMs = NoteDbChangeState.getReadOnlySkew(cfg);

    String s = "notedb";
    timeoutMs =
        cfg.getTimeUnit(
            s,
            null,
            "primaryStorageMigrationTimeout",
            MILLISECONDS.convert(60, SECONDS),
            MILLISECONDS);
  }

  /**
   * Migrate a change's primary storage from ReviewDb to NoteDb.
   *
   * <p>This method will return only if the primary storage of the change is NoteDb afterwards. (It
   * may return early if the primary storage was already NoteDb.)
   *
   * <p>If this method throws an exception, then the primary storage of the change is probably not
   * NoteDb. (It is possible that the primary storage of the change is NoteDb in this case, but
   * there was an error reading the state.) Moreover, after an exception, the change may be
   * read-only until a lease expires. If the caller chooses to retry, they should wait until the
   * read-only lease expires; this method will fail relatively quickly if called on a read-only
   * change.
   *
   * <p>Note that if the change is read-only after this method throws an exception, that does not
   * necessarily guarantee that the read-only lease was acquired during that particular method
   * invocation; this call may have in fact failed because another thread acquired the lease first.
   *
   * @param id change ID.
   * @throws OrmException if a ReviewDb-level error occurs.
   * @throws IOException if a repo-level error occurs.
   */
  public void migrateToNoteDbPrimary(Change.Id id) throws OrmException, IOException {
    // Since there are multiple non-atomic steps in this method, we need to
    // consider what happens when there is another writer concurrent with the
    // thread executing this method.
    //
    // Let:
    // * OR = other writer writes noteDbState & new data to ReviewDb (in one
    //        transaction)
    // * ON = other writer writes to NoteDb
    // * MRO = migrator sets state to read-only
    // * MR = ensureRebuilt writes rebuilt noteDbState to ReviewDb (but does not
    //        otherwise update ReviewDb in this transaction)
    // * MN = ensureRebuilt writes rebuilt state to NoteDb
    //
    // Consider all the interleavings of these operations.
    //
    // * OR,ON,MRO,...
    //   Other writer completes before migrator begins; this is not a concurrent
    //   write.
    // * MRO,...,OR,...
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
    //   a change to be read-only for so long.
    //
    //   Thus, as an optimization, we have a retry loop that attempts
    //   ensureRebuilt while still holding the same read-only lease. This
    //   effectively results in the interleaving OR,MR,ON,MR,MN; in contrast
    //   with the previous case, here, MR/MN actually rebuilds the change. In
    //   the case of a write failure, MR/MN might fail and get retried again. If
    //   it exceeds the maximum number of retries, an exception is thrown.
    //
    // * OR,MRO,MR,MN,ON
    //   The change is out-of-date when ensureRebuilt begins. The change is
    //   rebuilt, leaving a new state in NoteDb. ON will fail, because the old
    //   NoteDb state has changed since the ref state was read when the update
    //   began (prior to OR). This results in an exception from ON, but the end
    //   result is still an up-to-date, read-only change. The end user that
    //   initiated the other write observes an error, but this is no different
    //   from other errors that need retrying, e.g. due to a backend write
    //   failure.

    Stopwatch sw = Stopwatch.createStarted();
    Change readOnlyChange = setReadOnly(id); // MRO
    if (readOnlyChange == null) {
      return; // Already migrated.
    }

    NoteDbChangeState rebuiltState;
    try {
      // MR,MN
      rebuiltState =
          ensureRebuiltRetryer(sw)
              .call(
                  () ->
                      ensureRebuilt(
                          readOnlyChange.getProject(),
                          id,
                          NoteDbChangeState.parse(readOnlyChange)));
    } catch (RetryException | ExecutionException e) {
      throw new OrmException(e);
    }

    // At this point, the noteDbState in ReviewDb is read-only, and it is
    // guaranteed to match the state actually in NoteDb. Now it is safe to set
    // the primary storage to NoteDb.

    setPrimaryStorageNoteDb(id, rebuiltState);
    log.info("Migrated change {} to NoteDb primary in {}ms", id, sw.elapsed(MILLISECONDS));
  }

  private Change setReadOnly(Change.Id id) throws OrmException {
    AtomicBoolean alreadyMigrated = new AtomicBoolean(false);
    Change result =
        db().changes()
            .atomicUpdate(
                id,
                new AtomicUpdate<Change>() {
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

  private Retryer<NoteDbChangeState> ensureRebuiltRetryer(Stopwatch sw) {
    if (testEnsureRebuiltRetryer != null) {
      return testEnsureRebuiltRetryer;
    }
    // Retry the ensureRebuilt step with backoff until half the timeout has
    // expired, leaving the remaining half for the rest of the steps.
    long remainingNanos = (MILLISECONDS.toNanos(timeoutMs) / 2) - sw.elapsed(NANOSECONDS);
    remainingNanos = Math.max(remainingNanos, 0);
    return RetryerBuilder.<NoteDbChangeState>newBuilder()
        .retryIfException(e -> (e instanceof IOException) || (e instanceof OrmException))
        .withWaitStrategy(
            WaitStrategies.join(
                WaitStrategies.exponentialWait(250, MILLISECONDS),
                WaitStrategies.randomWait(50, MILLISECONDS)))
        .withStopStrategy(StopStrategies.stopAfterDelay(remainingNanos, NANOSECONDS))
        .build();
  }

  private NoteDbChangeState ensureRebuilt(
      Project.NameKey project, Change.Id id, NoteDbChangeState readOnlyState)
      throws IOException, OrmException, RepositoryNotFoundException {
    try (Repository changeRepo = repoManager.openRepository(project);
        Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      if (!readOnlyState.isUpToDate(new RepoRefCache(changeRepo), new RepoRefCache(allUsersRepo))) {
        NoteDbUpdateManager.Result r = rebuilder.rebuildEvenIfReadOnly(db(), id);
        checkState(
            r.newState().getReadOnlyUntil().equals(readOnlyState.getReadOnlyUntil()),
            "state after rebuilding has different read-only lease: %s != %s",
            r.newState(),
            readOnlyState);
        readOnlyState = r.newState();
      }
    }
    return readOnlyState;
  }

  private void setPrimaryStorageNoteDb(Change.Id id, NoteDbChangeState expectedState)
      throws OrmException {
    db().changes()
        .atomicUpdate(
            id,
            new AtomicUpdate<Change>() {
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

  private String badState(NoteDbChangeState actual, NoteDbChangeState expected) {
    return "state changed unexpectedly: " + actual + " != " + expected;
  }
}
