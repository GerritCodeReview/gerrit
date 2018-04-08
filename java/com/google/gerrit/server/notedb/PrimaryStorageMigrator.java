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

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepoRefCache;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbChangeState.RefState;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper to migrate the {@link PrimaryStorage} of individual changes. */
@Singleton
public class PrimaryStorageMigrator {
  private static final Logger log = LoggerFactory.getLogger(PrimaryStorageMigrator.class);

  /**
   * Exception thrown during migration if the change has no {@code noteDbState} field at the
   * beginning of the migration.
   */
  public static class NoNoteDbStateException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private NoNoteDbStateException(Change.Id id) {
      super("change " + id + " has no note_db_state; rebuild it first");
    }
  }

  private final AllUsersName allUsers;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeRebuilder rebuilder;
  private final ChangeUpdate.Factory updateFactory;
  private final GitRepositoryManager repoManager;
  private final InternalUser.Factory internalUserFactory;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<ReviewDb> db;
  private final RetryHelper retryHelper;

  private final long skewMs;
  private final long timeoutMs;
  private final Retryer<NoteDbChangeState> testEnsureRebuiltRetryer;

  @Inject
  PrimaryStorageMigrator(
      @GerritServerConfig Config cfg,
      Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      ChangeRebuilder rebuilder,
      ChangeNotes.Factory changeNotesFactory,
      Provider<InternalChangeQuery> queryProvider,
      ChangeUpdate.Factory updateFactory,
      InternalUser.Factory internalUserFactory,
      RetryHelper retryHelper) {
    this(
        cfg,
        db,
        repoManager,
        allUsers,
        rebuilder,
        null,
        changeNotesFactory,
        queryProvider,
        updateFactory,
        internalUserFactory,
        retryHelper);
  }

  @VisibleForTesting
  public PrimaryStorageMigrator(
      Config cfg,
      Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      ChangeRebuilder rebuilder,
      @Nullable Retryer<NoteDbChangeState> testEnsureRebuiltRetryer,
      ChangeNotes.Factory changeNotesFactory,
      Provider<InternalChangeQuery> queryProvider,
      ChangeUpdate.Factory updateFactory,
      InternalUser.Factory internalUserFactory,
      RetryHelper retryHelper) {
    this.db = db;
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.rebuilder = rebuilder;
    this.testEnsureRebuiltRetryer = testEnsureRebuiltRetryer;
    this.changeNotesFactory = changeNotesFactory;
    this.queryProvider = queryProvider;
    this.updateFactory = updateFactory;
    this.internalUserFactory = internalUserFactory;
    this.retryHelper = retryHelper;
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
    Change readOnlyChange = setReadOnlyInReviewDb(id); // MRO
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
    log.debug("Migrated change {} to NoteDb primary in {}ms", id, sw.elapsed(MILLISECONDS));
  }

  private Change setReadOnlyInReviewDb(Change.Id id) throws OrmException {
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
                      // normally shouldn't happen.
                      //
                      // Known cases where this happens are described in and handled by
                      // NoteDbMigrator#canSkipPrimaryStorageMigration.
                      throw new NoNoteDbStateException(id);
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

  public void migrateToReviewDbPrimary(Change.Id id, @Nullable Project.NameKey project)
      throws OrmException, IOException {
    // Migrating back to ReviewDb primary is much simpler than the original migration to NoteDb
    // primary, because when NoteDb is primary, each write only goes to one storage location rather
    // than both. We only need to consider whether a concurrent writer (OR) conflicts with the first
    // setReadOnlyInNoteDb step (MR) in this method.
    //
    // If OR wins, then either:
    // * MR will set read-only after OR is completed, which is not a concurrent write.
    // * MR will fail to set read-only with a lock failure. The caller will have to retry, but the
    //   change is not in a read-only state, so behavior is not degraded in the meantime.
    //
    // If MR wins, then either:
    // * OR will fail with a read-only exception (via AbstractChangeNotes#apply).
    // * OR will fail with a lock failure.
    //
    // In all of these scenarios, the change is read-only if and only if MR succeeds.
    //
    // There will be no concurrent writes to ReviewDb for this change until
    // setPrimaryStorageReviewDb completes, because ReviewDb writes are not attempted when primary
    // storage is NoteDb. After the primary storage changes back, it is possible for subsequent
    // NoteDb writes to conflict with the releaseReadOnlyLeaseInNoteDb step, but at this point,
    // since ReviewDb is primary, we are back to ignoring them.
    Stopwatch sw = Stopwatch.createStarted();
    if (project == null) {
      project = getProject(id);
    }
    ObjectId newMetaId = setReadOnlyInNoteDb(project, id);
    rebuilder.rebuildReviewDb(db(), project, id);
    setPrimaryStorageReviewDb(id, newMetaId);
    releaseReadOnlyLeaseInNoteDb(project, id);
    log.debug("Migrated change {} to ReviewDb primary in {}ms", id, sw.elapsed(MILLISECONDS));
  }

  private ObjectId setReadOnlyInNoteDb(Project.NameKey project, Change.Id id)
      throws OrmException, IOException {
    Timestamp now = TimeUtil.nowTs();
    Timestamp until = new Timestamp(now.getTime() + timeoutMs);
    ChangeUpdate update =
        updateFactory.create(
            changeNotesFactory.createChecked(db.get(), project, id), internalUserFactory.create());
    update.setReadOnlyUntil(until);
    return update.commit();
  }

  private void setPrimaryStorageReviewDb(Change.Id id, ObjectId newMetaId)
      throws OrmException, IOException {
    ImmutableMap.Builder<Account.Id, ObjectId> draftIds = ImmutableMap.builder();
    try (Repository repo = repoManager.openRepository(allUsers)) {
      for (Ref draftRef :
          repo.getRefDatabase().getRefs(RefNames.refsDraftCommentsPrefix(id)).values()) {
        Account.Id accountId = Account.Id.fromRef(draftRef.getName());
        if (accountId != null) {
          draftIds.put(accountId, draftRef.getObjectId().copy());
        }
      }
    }
    NoteDbChangeState newState =
        new NoteDbChangeState(
            id,
            PrimaryStorage.REVIEW_DB,
            Optional.of(RefState.create(newMetaId, draftIds.build())),
            Optional.empty());
    db().changes()
        .atomicUpdate(
            id,
            new AtomicUpdate<Change>() {
              @Override
              public Change update(Change change) {
                if (PrimaryStorage.of(change) != PrimaryStorage.NOTE_DB) {
                  throw new OrmRuntimeException(
                      "change " + id + " is not NoteDb primary: " + change.getNoteDbState());
                }
                change.setNoteDbState(newState.toString());
                return change;
              }
            });
  }

  private void releaseReadOnlyLeaseInNoteDb(Project.NameKey project, Change.Id id)
      throws OrmException {
    // Use a BatchUpdate since ReviewDb is primary at this point, so it needs to reflect the update.
    // (In practice retrying won't happen, since we aren't using fused updates at this point.)
    try {
      retryHelper.execute(
          updateFactory -> {
            try (BatchUpdate bu =
                updateFactory.create(
                    db.get(), project, internalUserFactory.create(), TimeUtil.nowTs())) {
              bu.addOp(
                  id,
                  new BatchUpdateOp() {
                    @Override
                    public boolean updateChange(ChangeContext ctx) {
                      ctx.getUpdate(ctx.getChange().currentPatchSetId())
                          .setReadOnlyUntil(new Timestamp(0));
                      return true;
                    }
                  });
              bu.execute();
              return null;
            }
          });
    } catch (RestApiException | UpdateException e) {
      throw new OrmException(e);
    }
  }

  private Project.NameKey getProject(Change.Id id) throws OrmException {
    List<ChangeData> cds =
        queryProvider.get().setRequestedFields(ChangeField.PROJECT).byLegacyChangeId(id);
    Set<Project.NameKey> projects = new TreeSet<>();
    for (ChangeData cd : cds) {
      projects.add(cd.project());
    }
    if (projects.size() != 1) {
      throw new OrmException(
          "zero or multiple projects found for change "
              + id
              + ", must specify project explicitly: "
              + projects);
    }
    return projects.iterator().next();
  }
}
