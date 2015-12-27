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

import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Context for a set of updates that should be applied for a site.
 * <p>
 * An update operation can be divided into three phases:
 * <ol>
 * <li>Git reference updates</li>
 * <li>Database updates</li>
 * <li>Post-update steps<li>
 * </ol>
 * A single conceptual operation, such as a REST API call or a merge operation,
 * may make multiple changes at each step, which all need to be serialized
 * relative to each other. Moreover, for consistency, <em>all</em> git ref
 * updates must be performed before <em>any</em> database updates, since
 * database updates might refer to newly-created patch set refs. And all
 * post-update steps, such as hooks, should run only after all storage
 * mutations have completed.
 * <p>
 * Depending on the backend used, each step might support batching, for example
 * in a {@code BatchRefUpdate} or one or more database transactions. All
 * operations in one phase must complete successfully before proceeding to the
 * next phase.
 */
public class BatchUpdate implements AutoCloseable {
  public interface Factory {
    BatchUpdate create(ReviewDb db, Project.NameKey project,
        CurrentUser user, Timestamp when);
  }

  /** Order of execution of the various phases. */
  public static enum Order {
    /**
     * Update the repository and execute all ref updates before touching the
     * database.
     * <p>
     * The default and most common, as Gerrit does not behave well when a patch
     * set has no corresponding ref in the repo.
     */
    REPO_BEFORE_DB,

    /**
     * Update the database before touching the repository.
     * <p>
     * Generally only used when deleting patch sets, which should be deleted
     * first from the database (for the same reason as above.)
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

    public void addRefUpdate(ReceiveCommand cmd) {
      commands.add(cmd);
    }

    public TimeZone getTimeZone() {
      return tz;
    }
  }

  public class ChangeContext extends Context {
    private final ChangeControl ctl;
    private final ChangeUpdate update;
    private boolean deleted;

    private ChangeContext(ChangeControl ctl) {
      this.ctl = ctl;
      this.update = changeUpdateFactory.create(ctl, when);
    }

    public ChangeUpdate getChangeUpdate() {
      return update;
    }

    public ChangeNotes getChangeNotes() {
      return update.getChangeNotes();
    }

    public ChangeControl getChangeControl() {
      return ctl;
    }

    public Change getChange() {
      return update.getChange();
    }

    public void markDeleted() {
      this.deleted = true;
    }
  }

  public static class Op {
    @SuppressWarnings("unused")
    public void updateRepo(RepoContext ctx) throws Exception {
    }

    @SuppressWarnings("unused")
    public void updateChange(ChangeContext ctx) throws Exception {
    }

    // TODO(dborowitz): Support async operations?
    @SuppressWarnings("unused")
    public void postUpdate(Context ctx) throws Exception {
    }
  }

  public abstract static class InsertChangeOp extends Op {
    public abstract Change getChange();
  }

  private static class ChainedReceiveCommands {
    private final Map<String, ReceiveCommand> commands = new LinkedHashMap<>();

    private boolean isEmpty() {
      return commands.isEmpty();
    }

    private void add(ReceiveCommand cmd) {
      checkArgument(!cmd.getOldId().equals(cmd.getNewId()),
          "ref update is a no-op: %s", cmd);
      ReceiveCommand old = commands.get(cmd.getRefName());
      if (old == null) {
        commands.put(cmd.getRefName(), cmd);
        return;
      }
      checkArgument(old.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED,
          "cannot chain ref update %s after update %s with result %s",
          cmd, old, old.getResult());
      checkArgument(cmd.getOldId().equals(old.getNewId()),
          "cannot chain ref update %s after update %s with different new ID",
          cmd, old);
      commands.put(cmd.getRefName(), new ReceiveCommand(
          old.getOldId(), cmd.getNewId(), cmd.getRefName()));
    }

    private void addTo(BatchRefUpdate bru) {
      checkState(!isEmpty(), "no commands to add");
      for (ReceiveCommand cmd : commands.values()) {
        bru.addCommand(cmd);
      }
    }
  }

  /**
   * Interface for listening during batch update execution.
   * <p>
   * When used during execution of multiple batch updates, the {@code after*}
   * methods are called after that phase has been completed for <em>all</em> updates.
   */
  public static class Listener {
    private static final Listener NONE = new Listener();

    /**
     * Called after updating all repositories and flushing objects but before
     * updating any refs.
     */
    public void afterUpdateRepos() throws Exception {
    }

    /** Called after updating all refs. */
    public void afterRefUpdates() throws Exception {
    }

    /** Called after updating all changes. */
    public void afterUpdateChanges() throws Exception {
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

  static void execute(Collection<BatchUpdate> updates, Listener listener)
      throws UpdateException, RestApiException {
    if (updates.isEmpty()) {
      return;
    }
    try {
      Order order = getOrder(updates);
      switch (order) {
        case REPO_BEFORE_DB:
          for (BatchUpdate u : updates) {
            u.executeUpdateRepo();
          }
          listener.afterUpdateRepos();
          for (BatchUpdate u : updates) {
            u.executeRefUpdates();
          }
          listener.afterRefUpdates();
          for (BatchUpdate u : updates) {
            u.executeChangeOps();
          }
          listener.afterUpdateChanges();
          break;
        case DB_BEFORE_REPO:
          for (BatchUpdate u : updates) {
            u.executeChangeOps();
          }
          listener.afterUpdateChanges();
          for (BatchUpdate u : updates) {
            u.executeUpdateRepo();
          }
          listener.afterUpdateRepos();
          for (BatchUpdate u : updates) {
            u.executeRefUpdates();
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
          u.gitRefUpdated.fire(u.project, u.batchRefUpdate);
        }
      }

      for (BatchUpdate u : updates) {
        u.executePostOps();
      }
    } catch (UpdateException | RestApiException e) {
      // Propagate REST API exceptions thrown by operations; they commonly throw
      // exceptions like ResourceConflictException to indicate an atomic update
      // failure.
      throw e;
    } catch (Exception e) {
      Throwables.propagateIfPossible(e);
      throw new UpdateException(e);
    }
  }

  private final ReviewDb db;
  private final GitRepositoryManager repoManager;
  private final ChangeIndexer indexer;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final GitReferenceUpdated gitRefUpdated;

  private final Project.NameKey project;
  private final CurrentUser user;
  private final Timestamp when;
  private final TimeZone tz;

  private final ListMultimap<Change.Id, Op> ops = ArrayListMultimap.create();
  private final Map<Change.Id, Change> newChanges = new HashMap<>();
  private final List<CheckedFuture<?, IOException>> indexFutures =
      new ArrayList<>();

  private Repository repo;
  private ObjectInserter inserter;
  private RevWalk revWalk;
  private ChainedReceiveCommands commands = new ChainedReceiveCommands();
  private BatchRefUpdate batchRefUpdate;
  private boolean closeRepo;
  private Order order;

  @AssistedInject
  BatchUpdate(GitRepositoryManager repoManager,
      ChangeIndexer indexer,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeUpdate.Factory changeUpdateFactory,
      GitReferenceUpdated gitRefUpdated,
      @GerritPersonIdent PersonIdent serverIdent,
      @Assisted ReviewDb db,
      @Assisted Project.NameKey project,
      @Assisted CurrentUser user,
      @Assisted Timestamp when) {
    this.db = db;
    this.repoManager = repoManager;
    this.indexer = indexer;
    this.changeControlFactory = changeControlFactory;
    this.changeUpdateFactory = changeUpdateFactory;
    this.gitRefUpdated = gitRefUpdated;
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

  public BatchUpdate setRepository(Repository repo, RevWalk revWalk,
      ObjectInserter inserter) {
    checkState(this.repo == null, "repo already set");
    closeRepo = false;
    this.repo = checkNotNull(repo, "repo");
    this.revWalk = checkNotNull(revWalk, "revWalk");
    this.inserter = checkNotNull(inserter, "inserter");
    return this;
  }

  public BatchUpdate setOrder(Order order) {
    this.order = order;
    return this;
  }

  private void initRepository() throws IOException {
    if (repo == null) {
      this.repo = repoManager.openRepository(project);
      closeRepo = true;
      inserter = repo.newObjectInserter();
      revWalk = new RevWalk(inserter.newReader());
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
    ops.put(id, op);
    return this;
  }

  public BatchUpdate insertChange(InsertChangeOp op) {
    Change c = op.getChange();
    checkArgument(!newChanges.containsKey(c.getId()),
        "only one op allowed to create change %s", c.getId());
    newChanges.put(c.getId(), c);
    ops.get(c.getId()).add(0, op);
    return this;
  }

  public void execute() throws UpdateException, RestApiException {
    execute(Listener.NONE);
  }

  public void execute(Listener listener)
      throws UpdateException, RestApiException {
    execute(ImmutableList.of(this), listener);
  }

  private void executeUpdateRepo() throws UpdateException, RestApiException {
    try {
      RepoContext ctx = new RepoContext();
      for (Op op : ops.values()) {
        op.updateRepo(ctx);
      }
      if (inserter != null) {
        inserter.flush();
      }
    } catch (Exception e) {
      Throwables.propagateIfPossible(e, RestApiException.class);
      throw new UpdateException(e);
    }
  }

  private void executeRefUpdates() throws IOException, UpdateException {
    if (commands.isEmpty()) {
      return;
    }
    // May not be opened if the caller added ref updates but no new objects.
    initRepository();
    batchRefUpdate = repo.getRefDatabase().newBatchUpdate();
    commands.addTo(batchRefUpdate);
    batchRefUpdate.execute(revWalk, NullProgressMonitor.INSTANCE);
    boolean ok = true;
    for (ReceiveCommand cmd : batchRefUpdate.getCommands()) {
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        ok = false;
        break;
      }
    }
    if (!ok) {
      throw new UpdateException("BatchRefUpdate failed: " + batchRefUpdate);
    }
  }

  private void executeChangeOps() throws UpdateException, RestApiException {
    try {
      for (Map.Entry<Change.Id, Collection<Op>> e : ops.asMap().entrySet()) {
        Change.Id id = e.getKey();
        db.changes().beginTransaction(id);
        ChangeContext ctx;
        try {
          ctx = newChangeContext(id);
          for (Op op : e.getValue()) {
            op.updateChange(ctx);
          }
          db.commit();
        } finally {
          db.rollback();
        }
        ctx.getChangeUpdate().commit();
        if (ctx.deleted) {
          indexFutures.add(indexer.deleteAsync(id));
        } else {
          indexFutures.add(indexer.indexAsync(id));
        }
      }
    } catch (Exception e) {
      Throwables.propagateIfPossible(e, RestApiException.class);
      throw new UpdateException(e);
    }
  }

  private ChangeContext newChangeContext(Change.Id id) throws Exception {
    Change c = newChanges.get(id);
    if (c == null) {
      c = db.changes().get(id);
    }
    // Pass in preloaded change to controlFor, to avoid:
    //  - reading from a db that does not belong to this update
    //  - attempting to read a change that doesn't exist yet
    return new ChangeContext(
      changeControlFactory.controlFor(c, user));
  }

  private void executePostOps() throws Exception {
    Context ctx = new Context();
    for (Op op : ops.values()) {
      op.postUpdate(ctx);
    }
  }
}
