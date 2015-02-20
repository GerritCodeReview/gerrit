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
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public BatchUpdate create(ReviewDb db, Project.NameKey project,
        Timestamp when);
  }

  public class Context {
    public Timestamp getWhen() {
      return when;
    }

    public ReviewDb getDb() {
      return db;
    }
  }

  public class RepoContext extends Context {
    public Repository getRepository() throws IOException {
      initRepository();
      return repo;
    }

    public RevWalk getRevWalk() throws IOException {
      initRepository();
      return revWalk;
    }

    public ObjectInserter getInserter() throws IOException {
      initRepository();
      return inserter;
    }

    public BatchRefUpdate getBatchRefUpdate() throws IOException {
      initRepository();
      return batchRefUpdate;
    }
  }

  public class ChangeContext extends Context {
    private final ChangeUpdate update;

    private ChangeContext(ChangeUpdate update) {
      this.update = update;
    }

    public ChangeUpdate getChangeUpdate() {
      return update;
    }

    public Change readChange() throws OrmException {
      return db.changes().get(update.getChange().getId());
    }

    public CurrentUser getUser() {
      return update.getUser();
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

  private final ReviewDb db;
  private final GitRepositoryManager repoManager;
  private final ChangeIndexer indexer;
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final GitReferenceUpdated gitRefUpdated;

  private final Project.NameKey project;
  private final Timestamp when;

  private final ListMultimap<Change.Id, Op> ops = ArrayListMultimap.create();
  private final Map<Change.Id, ChangeControl> changeControls = new HashMap<>();
  private final List<CheckedFuture<?, IOException>> indexFutures =
      new ArrayList<>();

  private Repository repo;
  private ObjectInserter inserter;
  private RevWalk revWalk;
  private BatchRefUpdate batchRefUpdate;
  private boolean closeRepo;

  @AssistedInject
  BatchUpdate(GitRepositoryManager repoManager,
      ChangeIndexer indexer,
      ChangeUpdate.Factory changeUpdateFactory,
      GitReferenceUpdated gitRefUpdated,
      @Assisted ReviewDb db,
      @Assisted Project.NameKey project,
      @Assisted Timestamp when) {
    this.db = db;
    this.repoManager = repoManager;
    this.indexer = indexer;
    this.changeUpdateFactory = changeUpdateFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.project = project;
    this.when = when;
  }

  @Override
  public void close() {
    if (closeRepo) {
      revWalk.release();
      inserter.release();
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

  private void initRepository() throws IOException {
    if (repo == null) {
      this.repo = repoManager.openRepository(project);
      inserter = repo.newObjectInserter();
      revWalk = new RevWalk(inserter.newReader());
      batchRefUpdate = repo.getRefDatabase().newBatchUpdate();
    }
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

  public BatchUpdate addRefUpdate(ReceiveCommand cmd) {
    batchRefUpdate.addCommand(cmd);
    return this;
  }

  public BatchUpdate addOp(ChangeControl ctl, Op op) {
    Change.Id id = ctl.getChange().getId();
    ChangeControl old = changeControls.get(id);
    // TODO(dborowitz): Not sure this is guaranteed in general.
    checkArgument(old == null || old == ctl,
        "mismatched ChangeControls for change %s", id);
    ops.put(id, op);
    changeControls.put(id, ctl);
    return this;
  }

  public void execute() throws UpdateException, RestApiException {
    try {
      executeRefUpdates();
      executeChangeOps();
      reindexChanges();
      executePostOps();
    } catch (UpdateException | RestApiException e) {
      // Propagate REST API exceptions thrown by operations. Most operations
      // should throw non-REST exceptions like NoSuchChangeException, under the
      // assumption that validation is done by REST API handlers in advance of
      // executing the batch. They commonly throw ResourceConflictException to
      // indicate an atomic operation failure.
      throw e;
    } catch (Exception e) {
      Throwables.propagateIfPossible(e);
      throw new UpdateException(e);
    }
  }

  private void executeRefUpdates() throws IOException, UpdateException {
    RepoContext ctx = new RepoContext();
    try {
      for (Op op : ops.values()) {
        op.updateRepo(ctx);
      }
    } catch (Exception e) {
      Throwables.propagateIfPossible(e);
      throw new UpdateException(e);
    }
    if (repo == null || batchRefUpdate.getCommands().isEmpty()) {
      return;
    }
    inserter.flush();
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
    gitRefUpdated.fire(project, batchRefUpdate);
  }

  private void executeChangeOps() throws UpdateException {
    try {
      for (Map.Entry<Change.Id, Collection<Op>> e : ops.asMap().entrySet()) {
        Change.Id id = e.getKey();
        ChangeUpdate update =
            changeUpdateFactory.create(changeControls.get(id), when);
        db.changes().beginTransaction(id);
        try {
          for (Op op : e.getValue()) {
            op.updateChange(new ChangeContext(update));
          }
          db.commit();
        } finally {
          db.rollback();
        }
        update.commit();
        indexFutures.add(indexer.indexAsync(id));
      }
    } catch (Exception e) {
      throw new UpdateException(e);
    }
  }

  private void reindexChanges() throws IOException {
    ChangeIndexer.allAsList(indexFutures).checkedGet();
  }

  private void executePostOps() throws Exception {
    Context ctx = new Context();
    for (Op op : ops.values()) {
      op.postUpdate(ctx);
    }
  }
}
