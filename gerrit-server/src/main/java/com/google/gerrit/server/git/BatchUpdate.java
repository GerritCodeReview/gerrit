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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.notedb.NoteDbUpdateManager.MismatchedStateException;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.gerrit.server.util.RequestId;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Context for a set of updates that should be applied for a site.
 *
 * <p>An update operation can be divided into three phases:
 *
 * <ol>
 *   <li>Git reference updates
 *   <li>Database updates
 *   <li>Post-update steps
 *   <li>
 * </ol>
 *
 * A single conceptual operation, such as a REST API call or a merge operation, may make multiple
 * changes at each step, which all need to be serialized relative to each other. Moreover, for
 * consistency, <em>all</em> git ref updates must be performed before <em>any</em> database updates,
 * since database updates might refer to newly-created patch set refs. And all post-update steps,
 * such as hooks, should run only after all storage mutations have completed.
 *
 * <p>Depending on the backend used, each step might support batching, for example in a {@code
 * BatchRefUpdate} or one or more database transactions. All operations in one phase must complete
 * successfully before proceeding to the next phase.
 */
public class BatchUpdate implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(BatchUpdate.class);

  public interface Factory {
    BatchUpdate create(ReviewDb db, Project.NameKey project, CurrentUser user, Timestamp when);
  }

  /** Order of execution of the various phases. */
  public enum Order {
    /**
     * Update the repository and execute all ref updates before touching the database.
     *
     * <p>The default and most common, as Gerrit does not behave well when a patch set has no
     * corresponding ref in the repo.
     */
    REPO_BEFORE_DB,

    /**
     * Update the database before touching the repository.
     *
     * <p>Generally only used when deleting patch sets, which should be deleted first from the
     * database (for the same reason as above.)
     */
    DB_BEFORE_REPO;
  }

  public class Context {
    private Repository repoWrapper;

    public Repository getRepository() throws IOException {
      if (repoWrapper == null) {
        repoWrapper = new ReadOnlyRepository(BatchUpdate.this.getRepository());
      }
      return repoWrapper;
    }

    public RevWalk getRevWalk() throws IOException {
      return BatchUpdate.this.getRevWalk();
    }

    public Project.NameKey getProject() {
      return project;
    }

    public Timestamp getWhen() {
      return when;
    }

    public ReviewDb getDb() {
      return db;
    }

    public CurrentUser getUser() {
      return user;
    }

    public IdentifiedUser getIdentifiedUser() {
      checkNotNull(user);
      return user.asIdentifiedUser();
    }

    public Account getAccount() {
      checkNotNull(user);
      return user.asIdentifiedUser().getAccount();
    }

    public Account.Id getAccountId() {
      checkNotNull(user);
      return user.getAccountId();
    }

    public Order getOrder() {
      return order;
    }
  }

  public class RepoContext extends Context {
    @Override
    public Repository getRepository() throws IOException {
      return BatchUpdate.this.getRepository();
    }

    public ObjectInserter getInserter() throws IOException {
      return BatchUpdate.this.getObjectInserter();
    }

    public void addRefUpdate(ReceiveCommand cmd) throws IOException {
      initRepository();
      commands.add(cmd);
    }

    public TimeZone getTimeZone() {
      return tz;
    }
  }

  public class ChangeContext extends Context {
    private final ChangeControl ctl;
    private final Map<PatchSet.Id, ChangeUpdate> updates;
    private final ReviewDbWrapper dbWrapper;
    private final Repository threadLocalRepo;
    private final RevWalk threadLocalRevWalk;

    private boolean deleted;
    private boolean bumpLastUpdatedOn = true;

    protected ChangeContext(
        ChangeControl ctl, ReviewDbWrapper dbWrapper, Repository repo, RevWalk rw) {
      this.ctl = ctl;
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
    public Repository getRepository() {
      return threadLocalRepo;
    }

    @Override
    public RevWalk getRevWalk() {
      return threadLocalRevWalk;
    }

    public ChangeUpdate getUpdate(PatchSet.Id psId) {
      ChangeUpdate u = updates.get(psId);
      if (u == null) {
        u = changeUpdateFactory.create(ctl, when);
        if (newChanges.containsKey(ctl.getId())) {
          u.setAllowWriteToNewRef(true);
        }
        u.setPatchSetId(psId);
        updates.put(psId, u);
      }
      return u;
    }

    public ChangeNotes getNotes() {
      ChangeNotes n = ctl.getNotes();
      checkNotNull(n);
      return n;
    }

    public ChangeControl getControl() {
      checkNotNull(ctl);
      return ctl;
    }

    public Change getChange() {
      Change c = ctl.getChange();
      checkNotNull(c);
      return c;
    }

    public void bumpLastUpdatedOn(boolean bump) {
      bumpLastUpdatedOn = bump;
    }

    public void deleteChange() {
      deleted = true;
    }
  }

  public static class RepoOnlyOp {
    /**
     * Override this method to update the repo.
     *
     * @param ctx context
     */
    public void updateRepo(RepoContext ctx) throws Exception {}

    /**
     * Override this method to do something after the update e.g. send email or run hooks
     *
     * @param ctx context
     */
    //TODO(dborowitz): Support async operations?
    public void postUpdate(Context ctx) throws Exception {}
  }

  public static class Op extends RepoOnlyOp {
    /**
     * Override this method to modify a change.
     *
     * @param ctx context
     * @return whether anything was changed that might require a write to the metadata storage.
     */
    public boolean updateChange(ChangeContext ctx) throws Exception {
      return false;
    }
  }

  public abstract static class InsertChangeOp extends Op {
    public abstract Change createChange(Context ctx);
  }

  /**
   * Interface for listening during batch update execution.
   *
   * <p>When used during execution of multiple batch updates, the {@code after*} methods are called
   * after that phase has been completed for <em>all</em> updates.
   */
  public static class Listener {
    public static final Listener NONE = new Listener();

    /** Called after updating all repositories and flushing objects but before updating any refs. */
    public void afterUpdateRepos() throws Exception {}

    /** Called after updating all refs. */
    public void afterRefUpdates() throws Exception {}

    /** Called after updating all changes. */
    public void afterUpdateChanges() throws Exception {}
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

  private static Order getOrder(Collection<BatchUpdate> updates) {
    Order o = null;
    for (BatchUpdate u : updates) {
      if (o == null) {
        o = u.order;
      } else if (u.order != o) {
        throw new IllegalArgumentException("cannot mix execution orders");
      }
    }
    return o;
  }

  private static boolean getUpdateChangesInParallel(Collection<BatchUpdate> updates) {
    checkArgument(!updates.isEmpty());
    Boolean p = null;
    for (BatchUpdate u : updates) {
      if (p == null) {
        p = u.updateChangesInParallel;
      } else if (u.updateChangesInParallel != p) {
        throw new IllegalArgumentException("cannot mix parallel and non-parallel operations");
      }
    }
    // Properly implementing this would involve hoisting the parallel loop up
    // even further. As of this writing, the only user is ReceiveCommits,
    // which only executes a single BatchUpdate at a time. So bail for now.
    checkArgument(
        !p || updates.size() <= 1,
        "cannot execute ChangeOps in parallel with more than 1 BatchUpdate");
    return p;
  }

  static void execute(
      Collection<BatchUpdate> updates,
      Listener listener,
      @Nullable RequestId requestId,
      boolean dryrun)
      throws UpdateException, RestApiException {
    if (updates.isEmpty()) {
      return;
    }
    if (requestId != null) {
      for (BatchUpdate u : updates) {
        checkArgument(
            u.requestId == null || u.requestId == requestId,
            "refusing to overwrite RequestId %s in update with %s",
            u.requestId,
            requestId);
        u.setRequestId(requestId);
      }
    }
    try {
      Order order = getOrder(updates);
      boolean updateChangesInParallel = getUpdateChangesInParallel(updates);
      switch (order) {
        case REPO_BEFORE_DB:
          for (BatchUpdate u : updates) {
            u.executeUpdateRepo();
          }
          listener.afterUpdateRepos();
          for (BatchUpdate u : updates) {
            u.executeRefUpdates(dryrun);
          }
          listener.afterRefUpdates();
          for (BatchUpdate u : updates) {
            u.reindexChanges(u.executeChangeOps(updateChangesInParallel, dryrun));
          }
          listener.afterUpdateChanges();
          break;
        case DB_BEFORE_REPO:
          for (BatchUpdate u : updates) {
            u.reindexChanges(u.executeChangeOps(updateChangesInParallel, dryrun));
          }
          listener.afterUpdateChanges();
          for (BatchUpdate u : updates) {
            u.executeUpdateRepo();
          }
          listener.afterUpdateRepos();
          for (BatchUpdate u : updates) {
            u.executeRefUpdates(dryrun);
          }
          listener.afterRefUpdates();
          break;
        default:
          throw new IllegalStateException("invalid execution order: " + order);
      }

      List<CheckedFuture<?, IOException>> indexFutures = new ArrayList<>();
      for (BatchUpdate u : updates) {
        indexFutures.addAll(u.indexFutures);
      }
      ChangeIndexer.allAsList(indexFutures).get();

      for (BatchUpdate u : updates) {
        if (u.batchRefUpdate != null) {
          // Fire ref update events only after all mutations are finished, since
          // callers may assume a patch set ref being created means the change
          // was created, or a branch advancing meaning some changes were
          // closed.
          u.gitRefUpdated.fire(
              u.project,
              u.batchRefUpdate,
              u.getUser().isIdentifiedUser() ? u.getUser().asIdentifiedUser().getAccount() : null);
        }
      }
      if (!dryrun) {
        for (BatchUpdate u : updates) {
          u.executePostOps();
        }
      }
    } catch (UpdateException | RestApiException e) {
      // Propagate REST API exceptions thrown by operations; they commonly throw
      // exceptions like ResourceConflictException to indicate an atomic update
      // failure.
      throw e;

      // Convert other common non-REST exception types with user-visible
      // messages to corresponding REST exception types
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage(), e);
    } catch (NoSuchChangeException | NoSuchRefException | NoSuchProjectException e) {
      throw new ResourceNotFoundException(e.getMessage(), e);

    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new UpdateException(e);
    }
  }

  private final AllUsersName allUsers;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeIndexer indexer;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final GitRepositoryManager repoManager;
  private final ListeningExecutorService changeUpdateExector;
  private final Metrics metrics;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final NotesMigration notesMigration;
  private final ReviewDb db;
  private final SchemaFactory<ReviewDb> schemaFactory;

  private final Project.NameKey project;
  private final CurrentUser user;
  private final Timestamp when;
  private final TimeZone tz;

  private final ListMultimap<Change.Id, Op> ops =
      MultimapBuilder.linkedHashKeys().arrayListValues().build();
  private final Map<Change.Id, Change> newChanges = new HashMap<>();
  private final List<CheckedFuture<?, IOException>> indexFutures = new ArrayList<>();
  private final List<RepoOnlyOp> repoOnlyOps = new ArrayList<>();

  private Repository repo;
  private ObjectInserter inserter;
  private RevWalk revWalk;
  private ChainedReceiveCommands commands;
  private BatchRefUpdate batchRefUpdate;
  private boolean closeRepo;
  private Order order;
  private boolean updateChangesInParallel;
  private RequestId requestId;

  @AssistedInject
  BatchUpdate(
      AllUsersName allUsers,
      ChangeControl.GenericFactory changeControlFactory,
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
    this.allUsers = allUsers;
    this.changeControlFactory = changeControlFactory;
    this.changeNotesFactory = changeNotesFactory;
    this.changeUpdateExector = changeUpdateExector;
    this.changeUpdateFactory = changeUpdateFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.indexer = indexer;
    this.metrics = metrics;
    this.notesMigration = notesMigration;
    this.repoManager = repoManager;
    this.schemaFactory = schemaFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.db = db;
    this.project = project;
    this.user = user;
    this.when = when;
    tz = serverIdent.getTimeZone();
    order = Order.REPO_BEFORE_DB;
  }

  @Override
  public void close() {
    if (closeRepo) {
      revWalk.close();
      inserter.close();
      repo.close();
    }
  }

  public BatchUpdate setRequestId(RequestId requestId) {
    this.requestId = requestId;
    return this;
  }

  public BatchUpdate setRepository(Repository repo, RevWalk revWalk, ObjectInserter inserter) {
    checkState(this.repo == null, "repo already set");
    closeRepo = false;
    this.repo = checkNotNull(repo, "repo");
    this.revWalk = checkNotNull(revWalk, "revWalk");
    this.inserter = checkNotNull(inserter, "inserter");
    commands = new ChainedReceiveCommands(repo);
    return this;
  }

  public BatchUpdate setOrder(Order order) {
    this.order = order;
    return this;
  }

  /** Execute {@link Op#updateChange(ChangeContext)} in parallel for each change. */
  public BatchUpdate updateChangesInParallel() {
    this.updateChangesInParallel = true;
    return this;
  }

  private void initRepository() throws IOException {
    if (repo == null) {
      this.repo = repoManager.openRepository(project);
      closeRepo = true;
      inserter = repo.newObjectInserter();
      revWalk = new RevWalk(inserter.newReader());
      commands = new ChainedReceiveCommands(repo);
    }
  }

  public CurrentUser getUser() {
    return user;
  }

  public Repository getRepository() throws IOException {
    initRepository();
    return repo;
  }

  public RevWalk getRevWalk() throws IOException {
    initRepository();
    return revWalk;
  }

  public ObjectInserter getObjectInserter() throws IOException {
    initRepository();
    return inserter;
  }

  public BatchUpdate addOp(Change.Id id, Op op) {
    checkArgument(!(op instanceof InsertChangeOp), "use insertChange");
    checkNotNull(op);
    ops.put(id, op);
    return this;
  }

  public BatchUpdate addRepoOnlyOp(RepoOnlyOp op) {
    checkArgument(!(op instanceof Op), "use addOp()");
    repoOnlyOps.add(op);
    return this;
  }

  public BatchUpdate insertChange(InsertChangeOp op) {
    Context ctx = new Context();
    Change c = op.createChange(ctx);
    checkArgument(
        !newChanges.containsKey(c.getId()), "only one op allowed to create change %s", c.getId());
    newChanges.put(c.getId(), c);
    ops.get(c.getId()).add(0, op);
    return this;
  }

  public Collection<ReceiveCommand> getRefUpdates() {
    return commands.getCommands().values();
  }

  public void execute() throws UpdateException, RestApiException {
    execute(Listener.NONE);
  }

  public void execute(Listener listener) throws UpdateException, RestApiException {
    execute(ImmutableList.of(this), listener, requestId, false);
  }

  private void executeUpdateRepo() throws UpdateException, RestApiException {
    try {
      logDebug("Executing updateRepo on {} ops", ops.size());
      RepoContext ctx = new RepoContext();
      for (Op op : ops.values()) {
        op.updateRepo(ctx);
      }

      logDebug("Executing updateRepo on {} RepoOnlyOps", repoOnlyOps.size());
      for (RepoOnlyOp op : repoOnlyOps) {
        op.updateRepo(ctx);
      }

      if (inserter != null) {
        logDebug("Flushing inserter");
        inserter.flush();
      } else {
        logDebug("No objects to flush");
      }
    } catch (Exception e) {
      Throwables.throwIfInstanceOf(e, RestApiException.class);
      throw new UpdateException(e);
    }
  }

  private void executeRefUpdates(boolean dryrun) throws IOException, RestApiException {
    if (commands == null || commands.isEmpty()) {
      logDebug("No ref updates to execute");
      return;
    }
    // May not be opened if the caller added ref updates but no new objects.
    initRepository();
    batchRefUpdate = repo.getRefDatabase().newBatchUpdate();
    commands.addTo(batchRefUpdate);
    logDebug("Executing batch of {} ref updates", batchRefUpdate.getCommands().size());
    if (dryrun) {
      return;
    }

    batchRefUpdate.execute(revWalk, NullProgressMonitor.INSTANCE);
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
        if (notesMigration.commitChangeWrites() && repo != null) {
          // A NoteDb change may have been rebuilt since the repo was originally
          // opened, so make sure we see that.
          logDebug("Preemptively scanning for repo changes");
          repo.scanForRepoChanges();
        }
        if (!ops.isEmpty() && notesMigration.failChangeWrites()) {
          // Fail fast before attempting any writes if changes are read-only, as
          // this is a programmer error.
          logDebug("Failing early due to read-only Changes table");
          throw new OrmException(NoteDbUpdateManager.CHANGES_READ_ONLY);
        }
        List<ListenableFuture<?>> futures = new ArrayList<>(ops.keySet().size());
        for (Map.Entry<Change.Id, Collection<Op>> e : ops.asMap().entrySet()) {
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

  private void executeNoteDbUpdates(List<ChangeTask> tasks) {
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
      BatchRefUpdate changeRefUpdate = getRepository().getRefDatabase().newBatchUpdate();
      boolean hasAllUsersCommands = false;
      try (ObjectInserter ins = getRepository().newObjectInserter()) {
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
      // Ignore all errors trying to update NoteDb at this point. We've
      // already written the NoteDbChangeState to ReviewDb, which means
      // if the state is out of date it will be rebuilt the next time it
      // is needed.
      // Always log even without RequestId.
      log.debug("Ignoring NoteDb update error after ReviewDb write", e);
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
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        throw new IOException("Update failed: " + bru);
      }
    }
  }

  private class ChangeTask implements Callable<Void> {
    final Change.Id id;
    private final Collection<Op> changeOps;
    private final Thread mainThread;
    private final boolean dryrun;

    NoteDbUpdateManager.StagedResult noteDbResult;
    boolean dirty;
    boolean deleted;
    private String taskId;

    private ChangeTask(Change.Id id, Collection<Op> changeOps, Thread mainThread, boolean dryrun) {
      this.id = id;
      this.changeOps = changeOps;
      this.mainThread = mainThread;
      this.dryrun = dryrun;
    }

    @Override
    public Void call() throws Exception {
      taskId = id.toString() + "-" + Thread.currentThread().getId();
      if (Thread.currentThread() == mainThread) {
        Repository repo = getRepository();
        try (ObjectReader reader = repo.newObjectReader();
            RevWalk rw = new RevWalk(repo)) {
          call(BatchUpdate.this.db, repo, rw);
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
        PrimaryStorage storage;
        db.changes().beginTransaction(id);
        try {
          ChangeContext ctx = newChangeContext(db, repo, rw, id);
          storage = PrimaryStorage.of(ctx.getChange());
          if (storage == PrimaryStorage.NOTE_DB && !notesMigration.readChanges()) {
            throw new OrmException("must have NoteDb enabled to update change " + id);
          }

          // Call updateChange on each op.
          logDebug("Calling updateChange on {} ops", changeOps.size());
          for (Op op : changeOps) {
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

    private ChangeContext newChangeContext(ReviewDb db, Repository repo, RevWalk rw, Change.Id id)
        throws Exception {
      Change c = newChanges.get(id);
      if (c == null) {
        c = ChangeNotes.readOneReviewDbChange(db, id);
      }
      // Pass in preloaded change to controlFor, to avoid:
      //  - reading from a db that does not belong to this update
      //  - attempting to read a change that doesn't exist yet
      ChangeNotes notes = changeNotesFactory.createForBatchUpdate(c);
      ChangeControl ctl = changeControlFactory.controlFor(notes, user);
      return new ChangeContext(ctl, new BatchUpdateReviewDb(db), repo, rw);
    }

    private NoteDbUpdateManager stageNoteDbUpdate(ChangeContext ctx, boolean deleted)
        throws OrmException, IOException {
      logDebug("Staging NoteDb update");
      NoteDbUpdateManager updateManager =
          updateManagerFactory
              .create(ctx.getProject())
              .setChangeRepo(
                  ctx.getRepository(), ctx.getRevWalk(), null, new ChainedReceiveCommands(repo));
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
        BatchUpdate.this.logDebug("[" + taskId + "]" + msg, t);
      }
    }

    private void logDebug(String msg, Object... args) {
      if (log.isDebugEnabled()) {
        BatchUpdate.this.logDebug("[" + taskId + "]" + msg, args);
      }
    }
  }

  private static Iterable<Change> changesToUpdate(ChangeContext ctx) {
    Change c = ctx.getChange();
    if (ctx.bumpLastUpdatedOn && c.getLastUpdatedOn().before(ctx.getWhen())) {
      c.setLastUpdatedOn(ctx.getWhen());
    }
    return Collections.singleton(c);
  }

  private void executePostOps() throws Exception {
    Context ctx = new Context();
    for (Op op : ops.values()) {
      op.postUpdate(ctx);
    }

    for (RepoOnlyOp op : repoOnlyOps) {
      op.postUpdate(ctx);
    }
  }

  private void logDebug(String msg, Throwable t) {
    if (requestId != null && log.isDebugEnabled()) {
      log.debug(requestId + msg, t);
    }
  }

  private void logDebug(String msg, Object... args) {
    // Only log if there is a requestId assigned, since those are the
    // expensive/complicated requests like MergeOp. Doing it every time would be
    // noisy.
    if (requestId != null && log.isDebugEnabled()) {
      log.debug(requestId + msg, args);
    }
  }
}
