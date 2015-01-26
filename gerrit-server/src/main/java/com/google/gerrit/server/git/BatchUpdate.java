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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.Inject;
import com.google.inject.Singleton;

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
import java.util.concurrent.Callable;

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
  public abstract static class ChangeOp {
    private final ChangeControl ctl;

    public ChangeOp(ChangeControl ctl) {
      this.ctl = ctl;
    }

    public abstract void call(ReviewDb db, ChangeUpdate update)
        throws Exception;
  }

  // TODO(dborowitz): Manual injection so we can throw IOException from the
  // create method. Let's hope this doesn't get too unwieldy; at the upper
  // bound, it shouldn't be uglier than SubmitStrategy.Arguments.
  @Singleton
  public static class Factory {
    private final GitRepositoryManager repoManager;
    private final ChangeIndexer indexer;
    private final ChangeUpdate.Factory changeUpdateFactory;
    private final GitReferenceUpdated gitRefUpdated;

    @Inject
    Factory(GitRepositoryManager repoManager,
        ChangeIndexer indexer,
        ChangeUpdate.Factory changeUpdateFactory,
        GitReferenceUpdated gitRefUpdated) {
      this.repoManager = repoManager;
      this.indexer = indexer;
      this.changeUpdateFactory = changeUpdateFactory;
      this.gitRefUpdated = gitRefUpdated;
    }

    public BatchUpdate create(ReviewDb db, Project.NameKey project,
        Timestamp when) throws IOException {
      return new BatchUpdate(db, repoManager, indexer, changeUpdateFactory,
          gitRefUpdated, project, when);
    }
  }

  private final ReviewDb db;
  private final ChangeIndexer indexer;
  private final ChangeUpdate.Factory changeUpdateFactory;
  private final GitReferenceUpdated gitRefUpdated;

  private final Project.NameKey project;
  private final Timestamp when;

  private final Repository repo;
  private final ObjectInserter inserter;
  private final RevWalk revWalk;
  private final BatchRefUpdate batchRefUpdate;

  private final ListMultimap<Change.Id, ChangeOp> changeOps;
  private final Map<Change.Id, ChangeControl> changeControls;
  private final List<Callable<?>> postOps;
  private final List<CheckedFuture<?, IOException>> indexFutures;

  private BatchUpdate(ReviewDb db, GitRepositoryManager repoManager,
      ChangeIndexer indexer, ChangeUpdate.Factory changeUpdateFactory,
      GitReferenceUpdated gitRefUpdated, Project.NameKey project,
      Timestamp when) throws IOException {
    this.db = db;
    this.indexer = indexer;
    this.changeUpdateFactory = changeUpdateFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.project = project;
    this.when = when;
    repo = repoManager.openRepository(project);
    inserter = repo.newObjectInserter();
    revWalk = new RevWalk(inserter.newReader());
    batchRefUpdate = repo.getRefDatabase().newBatchUpdate();
    changeOps = ArrayListMultimap.create();
    postOps = new ArrayList<>();
    changeControls = new HashMap<>();
    indexFutures = new ArrayList<>();
  }

  @Override
  public void close() {
    revWalk.release();
    inserter.release();
    repo.close();
  }

  public RevWalk getRevWalk() {
    return revWalk;
  }

  public Repository getRepository() {
    return repo;
  }

  public ObjectInserter getObjectInserter() {
    return inserter;
  }

  public BatchRefUpdate getBatchRefUpdate() {
    return batchRefUpdate;
  }

  public BatchUpdate addRefUpdate(ReceiveCommand cmd) {
    batchRefUpdate.addCommand(cmd);
    return this;
  }

  public BatchUpdate addChangeOp(ChangeOp op) {
    Change.Id id = op.ctl.getChange().getId();
    ChangeControl old = changeControls.get(id);
    // TODO(dborowitz): Not sure this is guaranteed in general.
    checkArgument(old == null || old == op.ctl,
        "mismatched ChangeControls for change %s", id);
    changeOps.put(id, op);
    changeControls.put(id, op.ctl);
    return this;
  }

  // TODO(dborowitz): Support async operations?
  public BatchUpdate addPostOp(Callable<?> update) {
    postOps.add(update);
    return this;
  }

  public void execute() throws UpdateException {
    executeRefUpdates();
    executeChangeOps();
    reindexChanges();
    executePostOps();
  }

  private void executeRefUpdates() throws UpdateException {
    if (batchRefUpdate.getCommands().isEmpty()) {
      return;
    }
    try {
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
    } catch (IOException e) {
      throw new UpdateException(e);
    }
  }

  private void executeChangeOps() throws UpdateException {
    try {
      for (Map.Entry<Change.Id, Collection<ChangeOp>> e
          : changeOps.asMap().entrySet()) {
        Change.Id id = e.getKey();
        ChangeUpdate update =
            changeUpdateFactory.create(changeControls.get(id), when);
        db.changes().beginTransaction(id);
        try {
          for (ChangeOp op : e.getValue()) {
            op.call(db, update);
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

  private void reindexChanges() throws UpdateException {
    try {
      ChangeIndexer.allAsList(indexFutures).checkedGet();
    } catch (IOException e) {
      throw new UpdateException(e);
    }
  }

  private void executePostOps() throws UpdateException {
    try {
      for (Callable<?> op : postOps) {
        op.call();
      }
    } catch (Exception e) {
      throw new UpdateException(e);
    }
  }
}
