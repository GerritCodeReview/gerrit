// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.update;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.git.InsertedObject;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.notedb.NoteDbUpdateManager.MismatchedStateException;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.util.RequestId;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link BatchUpdate} implementation that supports mixed ReviewDb/NoteDb operations, depending on
 * the migration state specified in {@link NotesMigration}.
 *
 * <p>When performing change updates in a mixed ReviewDb/NoteDb environment with ReviewDb primary,
 * the order of operations is very subtle:
 *
 * <ol>
 *   <li>Stage NoteDb updates to get the new NoteDb state, but do not write to the repo.
 *   <li>Write the new state in the Change entity, and commit this to ReviewDb.
 *   <li>Update NoteDb, ignoring any write failures.
 * </ol>
 *
 * The implementation in this class is well-tested, and it is strongly recommended that you not
 * attempt to reimplement this logic. Use {@code BatchUpdate} if at all possible.
 */
public class ReviewDbBatchUpdate extends BatchUpdate {
  private static final Logger log = LoggerFactory.getLogger(ReviewDbBatchUpdate.class);

  public interface AssistedFactory {
    ReviewDbBatchUpdate create(
        ReviewDb db, Project.NameKey project, CurrentUser user, Timestamp when);
  }

  class ContextImpl implements Context {
    @Override
    public RepoView getRepoView() throws IOException {
      return ReviewDbBatchUpdate.this.getRepoView();
    }

    @Override
    public RevWalk getRevWalk() throws IOException {
      return getRepoView().getRevWalk();
    }

    @Override
    public Project.NameKey getProject() {
      return project;
    }

    @Override
    public Timestamp getWhen() {
      return when;
    }

    @Override
    public TimeZone getTimeZone() {
      return tz;
    }

    @Override
    public ReviewDb getDb() {
      return db;
    }

    @Override
    public CurrentUser getUser() {
      return user;
    }

    @Override
    public Order getOrder() {
      return order;
    }
  }

  private class RepoContextImpl extends ContextImpl implements RepoContext {
    @Override
    public ObjectInserter getInserter() throws IOException {
      return getRepoView().getInserterWrapper();
    }

    @Override
    public void addRefUpdate(ReceiveCommand cmd) throws IOException {
      initRepository();
      repoView.getCommands().add(cmd);
    }
  }

  private class ChangeContextImpl extends ContextImpl implements ChangeContext {
    private final ChangeNotes notes;
    private final Map<PatchSet.Id, ChangeUpdate> updates;
    private final ReviewDbWrapper dbWrapper;
    private final Repository threadLocalRepo;
    private final RevWalk threadLocalRevWalk;

    private boolean deleted;
    private boolean bumpLastUpdatedOn = true;

    protected ChangeContextImpl(
        ChangeNotes notes, ReviewDbWrapper dbWrapper, Repository repo, RevWalk rw) {
      this.notes = checkNotNull(notes);
      this.dbWrapper = dbWrapper;
      this.threadLocalRepo = repo;
      this.threadLocalRevWalk = rw;
      updates = new TreeMap<>(comparing(PatchSet.Id::get));
    }

    @Override
    public ReviewDb getDb() {
      checkNotNull(dbWrapper);
      return dbWrapper;
    }

    @Override
    public RevWalk getRevWalk() {
      return threadLocalRevWalk;
    }

    @Override
    public ChangeUpdate getUpdate(PatchSet.Id psId) {
      ChangeUpdate u = updates.get(psId);
      if (u == null) {
        u = changeUpdateFactory.create(notes, user, when);
        if (newChanges.containsKey(notes.getChangeId())) {
          u.setAllowWriteToNewRef(true);
        }
        u.setPatchSetId(psId);
        updates.put(psId, u);
      }
      return u;
    }

    @Override
    public ChangeNotes getNotes() {
      return notes;
    }

    @Override
    public void dontBumpLastUpdatedOn() {
      bumpLastUpdatedOn = false;
    }

    @Override
    public void deleteChange() {
      deleted = true;
    }
  }

  @Singleton
  private static class Metrics {
    final Timer1<Boolean> executeChangeOpsLatency;

    @Inject
    Metrics(MetricMaker metricMaker) {
      executeChangeOpsLatency =
          metricMaker.newTimer(
              "batch_update/execute_change_ops",
              new Description("BatchUpdate change update latency, excluding reindexing")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS),
              Field.ofBoolean("success"));
    }
  }

  static void execute(
      ImmutableList<ReviewDbBatchUpdate> updates,
      BatchUpdateListener listener,
      @Nullable RequestId requestId,
      boolean dryrun)
      throws UpdateException, RestApiException {
    if (updates.isEmpty()) {
      return;
    }
    setRequestIds(updates, requestId);
    try {
      Order order = getOrder(updates, listener);
      boolean updateChangesInParallel = getUpdateChangesInParallel(updates);
      switch (order) {
        case REPO_BEFORE_DB:
          for (ReviewDbBatchUpdate u : updates) {
            u.executeUpdateRepo();
          }
          listener.afterUpdateRepos();
          for (ReviewDbBatchUpdate u : updates) {
            u.executeRefUpdates(dryrun);
          }
          listener.afterUpdateRefs();
          for (ReviewDbBatchUpdate u : updates) {
            u.reindexChanges(u.executeChangeOps(updateChangesInParallel, dryrun));
          }
          listener.afterUpdateChanges();
          break;
        case DB_BEFORE_REPO:
          for (ReviewDbBatchUpdate u : updates) {
            u.reindexChanges(u.executeChangeOps(updateChangesInParallel, dryrun));
          }
          for (ReviewDbBatchUpdate u : updates) {
            u.executeUpdateRepo();
          }
          for (ReviewDbBatchUpdate u : updates) {
            u.executeRefUpdates(dryrun);
          }
          break;
        default:
          throw new IllegalStateException("invalid execution order: " + order);
      }

      ChangeIndexer.allAsList(
              updates.stream().flatMap(u -> u.indexFutures.stream()).collect(toList()))
          .get();

      // Fire ref update events only after all mutations are finished, since callers may assume a
      // patch set ref being created means the change was created, or a branch advancing meaning
      // some changes were closed.
      updates
          .stream()
          .filter(u -> u.batchRefUpdate != null)
          .forEach(
              u -> u.gitRefUpdated.fire(u.project, u.batchRefUpdate, u.getAccount().orElse(null)));

      if (!dryrun) {
        for (ReviewDbBatchUpdate u : updates) {
          u.executePostOps();
        }
      }
    } catch (Exception e) {
      wrapAndThrowException(e);
    }
  }

  private final AllUsersName allUsers;
  private final ChangeIndexer indexer;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final ListeningExecutorService changeUpdateExector;
  private final Metrics metrics;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final NotesMigration notesMigration;
  private final ReviewDb db;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final long skewMs;

  @SuppressWarnings("deprecation")
  private final List<com.google.common.util.concurrent.CheckedFuture<?, IOException>> indexFutures =
      new ArrayList<>();

  @Inject
  ReviewDbBatchUpdate(
      @GerritServerConfig Config cfg,
      AllUsersName allUsers,
      ChangeIndexer indexer,
      ChangeNotes.Factory changeNotesFactory,
      @ChangeUpdateExecutor ListeningExecutorService changeUpdateExector,
      ChangeUpdate.Factory changeUpdateFactory,
      @GerritPersonIdent PersonIdent serverIdent,
      GitReferenceUpdated gitRefUpdated,
      GitRepositoryManager repoManager,
      Metrics metrics,
      NoteDbUpdateManager.Factory updateManagerFactory,
      NotesMigration notesMigration,
      SchemaFactory<ReviewDb> schemaFactory,
      @Assisted ReviewDb db,
      @Assisted Project.NameKey project,
      @Assisted CurrentUser user,
      @Assisted Timestamp when) {
    super(repoManager, serverIdent, project, user, when);
    this.allUsers = allUsers;
    this.changeNotesFactory = changeNotesFactory;
    this.changeUpdateExector = changeUpdateExector;
    this.changeUpdateFactory = changeUpdateFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.indexer = indexer;
    this.metrics = metrics;
    this.notesMigration = notesMigration;
    this.schemaFactory = schemaFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.db = db;
    skewMs = NoteDbChangeState.getReadOnlySkew(cfg);
  }

  @Override
  public void execute(BatchUpdateListener listener) throws UpdateException, RestApiException {
    execute(ImmutableList.of(this), listener, requestId, false);
  }

  @Override
  protected Context newContext() {
    return new ContextImpl();
  }

  private void executeUpdateRepo() throws UpdateException, RestApiException {
    try {
      logDebug("Executing updateRepo on {} ops", ops.size());
      RepoContextImpl ctx = new RepoContextImpl();
      for (BatchUpdateOp op : ops.values()) {
        op.updateRepo(ctx);
      }

      logDebug("Executing updateRepo on {} RepoOnlyOps", repoOnlyOps.size());
      for (RepoOnlyOp op : repoOnlyOps) {
        op.updateRepo(ctx);
      }

      if (onSubmitValidators != null && !getRefUpdates().isEmpty()) {
        // Validation of refs has to take place here and not at the beginning of executeRefUpdates.
        // Otherwise, failing validation in a second BatchUpdate object will happen *after* the
        // first update's executeRefUpdates has finished, hence after first repo's refs have been
        // updated, which is too late.
        onSubmitValidators.validate(
            project, ctx.getRevWalk().getObjectReader(), repoView.getCommands());
      }

      if (repoView != null) {
        logDebug("Flushing inserter");
        repoView.getInserter().flush();
      } else {
        logDebug("No objects to flush");
      }
    } catch (Exception e) {
      Throwables.throwIfInstanceOf(e, RestApiException.class);
      throw new UpdateException(e);
    }
  }

  private void executeRefUpdates(boolean dryrun) throws IOException, RestApiException {
    if (getRefUpdates().isEmpty()) {
      logDebug("No ref updates to execute");
      return;
    }
    // May not be opened if the caller added ref updates but no new objects.
    // TODO(dborowitz): Really?
    initRepository();
    batchRefUpdate = repoView.getRepository().getRefDatabase().newBatchUpdate();
    batchRefUpdate.setPushCertificate(pushCert);
    batchRefUpdate.setRefLogMessage(refLogMessage, true);
    batchRefUpdate.setAllowNonFastForwards(true);
    repoView.getCommands().addTo(batchRefUpdate);
    if (user.isIdentifiedUser()) {
      batchRefUpdate.setRefLogIdent(user.asIdentifiedUser().newRefLogIdent(when, tz));
    }
    logDebug("Executing batch of {} ref updates", batchRefUpdate.getCommands().size());
    if (dryrun) {
      return;
    }

    // Force BatchRefUpdate to read newly referenced objects using a new RevWalk, rather than one
    // that might have access to unflushed objects.
    try (RevWalk updateRw = new RevWalk(repoView.getRepository())) {
      batchRefUpdate.execute(updateRw, NullProgressMonitor.INSTANCE);
    }
    boolean ok = true;
    for (ReceiveCommand cmd : batchRefUpdate.getCommands()) {
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        ok = false;
        break;
      }
    }
    if (!ok) {
      throw new RestApiException("BatchRefUpdate failed: " + batchRefUpdate);
    }
  }

  private List<ChangeTask> executeChangeOps(boolean parallel, boolean dryrun)
      throws UpdateException, RestApiException {
    List<ChangeTask> tasks;
    boolean success = false;
    Stopwatch sw = Stopwatch.createStarted();
    try {
      logDebug("Executing change ops (parallel? {})", parallel);
      ListeningExecutorService executor =
          parallel ? changeUpdateExector : MoreExecutors.newDirectExecutorService();

      tasks = new ArrayList<>(ops.keySet().size());
      try {
        if (notesMigration.commitChangeWrites() && repoView != null) {
          // A NoteDb change may have been rebuilt since the repo was originally
          // opened, so make sure we see that.
          logDebug("Preemptively scanning for repo changes");
          repoView.getRepository().scanForRepoChanges();
        }
        if (!ops.isEmpty() && notesMigration.failChangeWrites()) {
          // Fail fast before attempting any writes if changes are read-only, as
          // this is a programmer error.
          logDebug("Failing early due to read-only Changes table");
          throw new OrmException(NoteDbUpdateManager.CHANGES_READ_ONLY);
        }
        List<ListenableFuture<?>> futures = new ArrayList<>(ops.keySet().size());
        for (Map.Entry<Change.Id, Collection<BatchUpdateOp>> e : ops.asMap().entrySet()) {
          ChangeTask task =
              new ChangeTask(e.getKey(), e.getValue(), Thread.currentThread(), dryrun);
          tasks.add(task);
          if (!parallel) {
            logDebug("Direct execution of task for ops: {}", ops);
          }
          futures.add(executor.submit(task));
        }
        if (parallel) {
          logDebug(
              "Waiting on futures for {} ops spanning {} changes", ops.size(), ops.keySet().size());
        }
        Futures.allAsList(futures).get();

        if (notesMigration.commitChangeWrites()) {
          if (!dryrun) {
            executeNoteDbUpdates(tasks);
          }
        }
        success = true;
      } catch (ExecutionException | InterruptedException e) {
        Throwables.throwIfInstanceOf(e.getCause(), UpdateException.class);
        Throwables.throwIfInstanceOf(e.getCause(), RestApiException.class);
        throw new UpdateException(e);
      } catch (OrmException | IOException e) {
        throw new UpdateException(e);
      }
    } finally {
      metrics.executeChangeOpsLatency.record(success, sw.elapsed(NANOSECONDS), NANOSECONDS);
    }
    return tasks;
  }

  private void reindexChanges(List<ChangeTask> tasks) {
    // Reindex changes.
    for (ChangeTask task : tasks) {
      if (task.deleted) {
        indexFutures.add(indexer.deleteAsync(task.id));
      } else if (task.dirty) {
        indexFutures.add(indexer.indexAsync(project, task.id));
      }
    }
  }

  private void executeNoteDbUpdates(List<ChangeTask> tasks)
      throws ResourceConflictException, IOException {
    // Aggregate together all NoteDb ref updates from the ops we executed,
    // possibly in parallel. Each task had its own NoteDbUpdateManager instance
    // with its own thread-local copy of the repo(s), but each of those was just
    // used for staging updates and was never executed.
    //
    // Use a new BatchRefUpdate as the original batchRefUpdate field is intended
    // for use only by the updateRepo phase.
    //
    // See the comments in NoteDbUpdateManager#execute() for why we execute the
    // updates on the change repo first.
    logDebug("Executing NoteDb updates for {} changes", tasks.size());
    try {
      initRepository();
      BatchRefUpdate changeRefUpdate = repoView.getRepository().getRefDatabase().newBatchUpdate();
      boolean hasAllUsersCommands = false;
      try (ObjectInserter ins = repoView.getRepository().newObjectInserter()) {
        int objs = 0;
        for (ChangeTask task : tasks) {
          if (task.noteDbResult == null) {
            logDebug("No-op update to {}", task.id);
            continue;
          }
          for (ReceiveCommand cmd : task.noteDbResult.changeCommands()) {
            changeRefUpdate.addCommand(cmd);
          }
          for (InsertedObject obj : task.noteDbResult.changeObjects()) {
            objs++;
            ins.insert(obj.type(), obj.data().toByteArray());
          }
          hasAllUsersCommands |= !task.noteDbResult.allUsersCommands().isEmpty();
        }
        logDebug(
            "Collected {} objects and {} ref updates to change repo",
            objs,
            changeRefUpdate.getCommands().size());
        executeNoteDbUpdate(getRevWalk(), ins, changeRefUpdate);
      }

      if (hasAllUsersCommands) {
        try (Repository allUsersRepo = repoManager.openRepository(allUsers);
            RevWalk allUsersRw = new RevWalk(allUsersRepo);
            ObjectInserter allUsersIns = allUsersRepo.newObjectInserter()) {
          int objs = 0;
          BatchRefUpdate allUsersRefUpdate = allUsersRepo.getRefDatabase().newBatchUpdate();
          for (ChangeTask task : tasks) {
            for (ReceiveCommand cmd : task.noteDbResult.allUsersCommands()) {
              allUsersRefUpdate.addCommand(cmd);
            }
            for (InsertedObject obj : task.noteDbResult.allUsersObjects()) {
              allUsersIns.insert(obj.type(), obj.data().toByteArray());
            }
          }
          logDebug(
              "Collected {} objects and {} ref updates to All-Users",
              objs,
              allUsersRefUpdate.getCommands().size());
          executeNoteDbUpdate(allUsersRw, allUsersIns, allUsersRefUpdate);
        }
      } else {
        logDebug("No All-Users updates");
      }
    } catch (IOException e) {
      if (tasks.stream().allMatch(t -> t.storage == PrimaryStorage.REVIEW_DB)) {
        // Ignore all errors trying to update NoteDb at this point. We've already written the
        // NoteDbChangeStates to ReviewDb, which means if any state is out of date it will be
        // rebuilt the next time it is needed.
        //
        // Always log even without RequestId.
        log.debug("Ignoring NoteDb update error after ReviewDb write", e);

        // Otherwise, we can't prove it's safe to ignore the error, either because some change had
        // NOTE_DB primary, or a task failed before determining the primary storage.
      } else if (e instanceof LockFailureException) {
        // LOCK_FAILURE is a special case indicating there was a conflicting write to a meta ref,
        // although it happened too late for us to produce anything but a generic error message.
        throw new ResourceConflictException("Updating change failed due to conflicting write", e);
      }
      throw e;
    }
  }

  private void executeNoteDbUpdate(RevWalk rw, ObjectInserter ins, BatchRefUpdate bru)
      throws IOException {
    if (bru.getCommands().isEmpty()) {
      logDebug("No commands, skipping flush and ref update");
      return;
    }
    ins.flush();
    bru.setAllowNonFastForwards(true);
    bru.execute(rw, NullProgressMonitor.INSTANCE);
    for (ReceiveCommand cmd : bru.getCommands()) {
      // TODO(dborowitz): LOCK_FAILURE for NoteDb primary should be retried.
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        throw new IOException("Update failed: " + bru);
      }
    }
  }

  private class ChangeTask implements Callable<Void> {
    final Change.Id id;
    private final Collection<BatchUpdateOp> changeOps;
    private final Thread mainThread;
    private final boolean dryrun;

    PrimaryStorage storage;
    NoteDbUpdateManager.StagedResult noteDbResult;
    boolean dirty;
    boolean deleted;
    private String taskId;

    private ChangeTask(
        Change.Id id, Collection<BatchUpdateOp> changeOps, Thread mainThread, boolean dryrun) {
      this.id = id;
      this.changeOps = changeOps;
      this.mainThread = mainThread;
      this.dryrun = dryrun;
    }

    @Override
    public Void call() throws Exception {
      taskId = id.toString() + "-" + Thread.currentThread().getId();
      if (Thread.currentThread() == mainThread) {
        initRepository();
        Repository repo = repoView.getRepository();
        try (RevWalk rw = new RevWalk(repo)) {
          call(ReviewDbBatchUpdate.this.db, repo, rw);
        }
      } else {
        // Possible optimization: allow Ops to declare whether they need to
        // access the repo from updateChange, and don't open in this thread
        // unless we need it. However, as of this writing the only operations
        // that are executed in parallel are during ReceiveCommits, and they
        // all need the repo open anyway. (The non-parallel case above does not
        // reopen the repo.)
        try (ReviewDb threadLocalDb = schemaFactory.open();
            Repository repo = repoManager.openRepository(project);
            RevWalk rw = new RevWalk(repo)) {
          call(threadLocalDb, repo, rw);
        }
      }
      return null;
    }

    private void call(ReviewDb db, Repository repo, RevWalk rw) throws Exception {
      @SuppressWarnings("resource") // Not always opened.
      NoteDbUpdateManager updateManager = null;
      try {
        db.changes().beginTransaction(id);
        try {
          ChangeContextImpl ctx = newChangeContext(db, repo, rw, id);
          NoteDbChangeState oldState = NoteDbChangeState.parse(ctx.getChange());
          NoteDbChangeState.checkNotReadOnly(oldState, skewMs);

          storage = PrimaryStorage.of(oldState);
          if (storage == PrimaryStorage.NOTE_DB && !notesMigration.readChanges()) {
            throw new OrmException("must have NoteDb enabled to update change " + id);
          }

          // Call updateChange on each op.
          logDebug("Calling updateChange on {} ops", changeOps.size());
          for (BatchUpdateOp op : changeOps) {
            dirty |= op.updateChange(ctx);
          }
          if (!dirty) {
            logDebug("No ops reported dirty, short-circuiting");
            return;
          }
          deleted = ctx.deleted;
          if (deleted) {
            logDebug("Change was deleted");
          }

          // Stage the NoteDb update and store its state in the Change.
          if (notesMigration.commitChangeWrites()) {
            updateManager = stageNoteDbUpdate(ctx, deleted);
          }

          if (storage == PrimaryStorage.REVIEW_DB) {
            // If primary storage of this change is in ReviewDb, bump
            // lastUpdatedOn or rowVersion and commit. Otherwise, don't waste
            // time updating ReviewDb at all.
            Iterable<Change> cs = changesToUpdate(ctx);
            if (isNewChange(id)) {
              // Insert rather than upsert in case of a race on change IDs.
              logDebug("Inserting change");
              db.changes().insert(cs);
            } else if (deleted) {
              logDebug("Deleting change");
              db.changes().delete(cs);
            } else {
              logDebug("Updating change");
              db.changes().update(cs);
            }
            if (!dryrun) {
              db.commit();
            }
          } else {
            logDebug("Skipping ReviewDb write since primary storage is {}", storage);
          }
        } finally {
          db.rollback();
        }

        // Do not execute the NoteDbUpdateManager, as we don't want too much
        // contention on the underlying repo, and we would rather use a single
        // ObjectInserter/BatchRefUpdate later.
        //
        // TODO(dborowitz): May or may not be worth trying to batch together
        // flushed inserters as well.
        if (storage == PrimaryStorage.NOTE_DB) {
          // Should have failed above if NoteDb is disabled.
          checkState(notesMigration.commitChangeWrites());
          noteDbResult = updateManager.stage().get(id);
        } else if (notesMigration.commitChangeWrites()) {
          try {
            noteDbResult = updateManager.stage().get(id);
          } catch (IOException ex) {
            // Ignore all errors trying to update NoteDb at this point. We've
            // already written the NoteDbChangeState to ReviewDb, which means
            // if the state is out of date it will be rebuilt the next time it
            // is needed.
            log.debug("Ignoring NoteDb update error after ReviewDb write", ex);
          }
        }
      } catch (Exception e) {
        logDebug("Error updating change (should be rethrown)", e);
        Throwables.propagateIfPossible(e, RestApiException.class);
        throw new UpdateException(e);
      } finally {
        if (updateManager != null) {
          updateManager.close();
        }
      }
    }

    private ChangeContextImpl newChangeContext(
        ReviewDb db, Repository repo, RevWalk rw, Change.Id id) throws OrmException {
      Change c = newChanges.get(id);
      boolean isNew = c != null;
      if (isNew) {
        // New change: populate noteDbState.
        checkState(c.getNoteDbState() == null, "noteDbState should not be filled in by callers");
        if (notesMigration.changePrimaryStorage() == PrimaryStorage.NOTE_DB) {
          c.setNoteDbState(NoteDbChangeState.NOTE_DB_PRIMARY_STATE);
        }
      } else {
        // Existing change.
        c = ChangeNotes.readOneReviewDbChange(db, id);
        if (c == null) {
          // Not in ReviewDb, but new changes are created with default primary
          // storage as NOTE_DB, so we can assume that a missing change is
          // NoteDb primary. Pass a synthetic change into ChangeNotes.Factory,
          // which lets ChangeNotes take care of the existence check.
          //
          // TODO(dborowitz): This assumption is potentially risky, because
          // it means once we turn this option on and start creating changes
          // without writing anything to ReviewDb, we can't turn this option
          // back off without making those changes inaccessible. The problem
          // is we have no way of distinguishing a change that only exists in
          // NoteDb because it only ever existed in NoteDb, from a change that
          // only exists in NoteDb because it used to exist in ReviewDb and
          // deleting from ReviewDb succeeded but deleting from NoteDb failed.
          //
          // TODO(dborowitz): We actually still have that problem anyway. Maybe
          // we need a cutoff timestamp? Or maybe we need to start leaving
          // tombstones in ReviewDb?
          c = ChangeNotes.Factory.newNoteDbOnlyChange(project, id);
        }
        NoteDbChangeState.checkNotReadOnly(c, skewMs);
      }
      ChangeNotes notes = changeNotesFactory.createForBatchUpdate(c, !isNew);
      return new ChangeContextImpl(notes, new BatchUpdateReviewDb(db), repo, rw);
    }

    private NoteDbUpdateManager stageNoteDbUpdate(ChangeContextImpl ctx, boolean deleted)
        throws OrmException, IOException {
      logDebug("Staging NoteDb update");
      NoteDbUpdateManager updateManager =
          updateManagerFactory
              .create(ctx.getProject())
              .setChangeRepo(
                  ctx.threadLocalRepo,
                  ctx.threadLocalRevWalk,
                  null,
                  new ChainedReceiveCommands(ctx.threadLocalRepo));
      if (ctx.getUser().isIdentifiedUser()) {
        updateManager.setRefLogIdent(
            ctx.getUser().asIdentifiedUser().newRefLogIdent(ctx.getWhen(), tz));
      }
      for (ChangeUpdate u : ctx.updates.values()) {
        updateManager.add(u);
      }

      Change c = ctx.getChange();
      if (deleted) {
        updateManager.deleteChange(c.getId());
      }
      try {
        updateManager.stageAndApplyDelta(c);
      } catch (MismatchedStateException ex) {
        // Refused to apply update because NoteDb was out of sync, which can
        // only happen if ReviewDb is the primary storage for this change.
        //
        // Go ahead with this ReviewDb update; it's still out of sync, but this
        // is no worse than before, and it will eventually get rebuilt.
        logDebug("Ignoring MismatchedStateException while staging");
      }

      return updateManager;
    }

    private boolean isNewChange(Change.Id id) {
      return newChanges.containsKey(id);
    }

    private void logDebug(String msg, Throwable t) {
      if (log.isDebugEnabled()) {
        ReviewDbBatchUpdate.this.logDebug("[" + taskId + "]" + msg, t);
      }
    }

    private void logDebug(String msg, Object... args) {
      if (log.isDebugEnabled()) {
        ReviewDbBatchUpdate.this.logDebug("[" + taskId + "]" + msg, args);
      }
    }
  }

  private static Iterable<Change> changesToUpdate(ChangeContextImpl ctx) {
    Change c = ctx.getChange();
    if (ctx.bumpLastUpdatedOn && c.getLastUpdatedOn().before(ctx.getWhen())) {
      c.setLastUpdatedOn(ctx.getWhen());
    }
    return Collections.singleton(c);
  }

  private void executePostOps() throws Exception {
    ContextImpl ctx = new ContextImpl();
    for (BatchUpdateOp op : ops.values()) {
      op.postUpdate(ctx);
    }

    for (RepoOnlyOp op : repoOnlyOps) {
      op.postUpdate(ctx);
    }
  }
}
