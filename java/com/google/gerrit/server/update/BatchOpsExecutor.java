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

package com.google.gerrit.server.update;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Helper to execute a set of BatchUpdateOp. This executes a number of BatchUpdateOp, that may or
 * may not be related to each other, combining them in a single {@link BatchRefUpdate}.
 *
 * <p>It preforms post-update steps as soon as the storage mutations for the batch are completed.
 */
public class BatchOpsExecutor extends AbstractBatchUpdate implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Module module() {
    return new FactoryModule() {
      @Override
      public void configure() {
        factory(BatchOpsExecutor.Factory.class);
      }
    };
  }

  public interface Factory {
    BatchOpsExecutor create(Project.NameKey project, CurrentUser user, Timestamp when);
  }

  @Inject
  BatchOpsExecutor(
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverIdent,
      ChangeData.Factory changeDataFactory,
      ChangeNotes.Factory changeNotesFactory,
      ChangeUpdate.Factory changeUpdateFactory,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeIndexer indexer,
      GitReferenceUpdated gitRefUpdated,
      @Assisted Project.NameKey project,
      @Assisted CurrentUser user,
      @Assisted Timestamp when) {
    super(
        repoManager,
        serverIdent,
        changeDataFactory,
        changeNotesFactory,
        changeUpdateFactory,
        updateManagerFactory,
        indexer,
        gitRefUpdated,
        project,
        user,
        when);
  }

  @Override
  public void close() {
    if (repoView != null) {
      repoView.close();
    }
  }

  public void executeAsBatch(ImmutableList<BatchUpdateListener> listeners, boolean dryrun) {}

  /*
    public void collectUpdates(ImmutableList<BatchUpdateListener> listeners, boolean dryrun){
      RepoContext repoContext = new RepoContextImpl();
      logDebug("Executing %d RepoOnlyOps as batch", repoOnlyOps.size());
      for (RepoOnlyOp op : repoOnlyOps) {
        op.updateRepo(repoContext);
      }

      logDebug("Executing %d ops as batch", ops.size());
      initRepository();
      Repository repo = repoView.getRepository();
      checkState(
          repo.getRefDatabase().performsAtomicTransactions(),
          "cannot use NoteDb with a repository that does not support atomic batch ref updates: %s",
          repo);

      ChangesHandle handle =
          new ChangesHandle(
              updateManagerFactory
                  .create(project)
                  .setBatchUpdateListeners(listeners)
                  .setChangeRepo(
                      repo, repoView.getRevWalk(), repoView.getInserter(), repoView.getCommands()),
              dryrun);
      if (user.isIdentifiedUser()) {
        handle.manager.setRefLogIdent(user.asIdentifiedUser().newRefLogIdent(when, tz));
      }
      handle.manager.setRefLogMessage(refLogMessage);
      handle.manager.setPushCertificate(pushCert);
      for (Map.Entry<Change.Id, Collection<BatchUpdateOp>> e : ops.asMap().entrySet()) {
        Change.Id id = e.getKey();
        ChangeContextImpl ctx = newChangeContext(id);
        boolean dirty = false;
        logDebug(
            "Applying %d ops for change %s: %s",
            e.getValue().size(),
            id,
            lazy(() -> e.getValue().stream().map(op -> op.getClass().getName()).collect(toSet())));
        for (BatchUpdateOp op : e.getValue()) {
          try (TraceContext.TraceTimer ignored =
              TraceContext.newTimer(
                  e.getClass().getSimpleName() + "#updateRepo", Metadata.empty())) {
            op.updateRepo(ctx);
          }
          try (TraceContext.TraceTimer ignored =
              TraceContext.newTimer(
                  op.getClass().getSimpleName() + "#updateChange", Metadata.empty())) {
            dirty |= op.updateChange(ctx);
          }
        }
        if (!dirty) {
          logDebug("No ops reported dirty, short-circuiting");
          handle.setResult(id, ChangeResult.SKIPPED);
          continue;
        }
        ctx.defaultUpdates.values().forEach(handle.manager::add);
        ctx.distinctUpdates.values().forEach(handle.manager::add);
        if (ctx.deleted) {
          logDebug("Change %s was deleted", id);
          handle.manager.deleteChange(id);
          handle.setResult(id, ChangeResult.DELETED);
        } else {
          handle.setResult(id, ChangeResult.UPSERTED);
        }
      }
      return handle;
    }
  */
  public int refsInUpdate() {
    return repoOnlyOps.size() + ops.size();
  }

  @Override
  public void addOp(Change.Id id, BatchUpdateOp op) {
    checkArgument(!(op instanceof InsertChangeOp), "use insertChange");
    requireNonNull(op);
    ops.put(id, op);
  }

  @Override
  public void addRepoOnlyOp(RepoOnlyOp op) {
    checkArgument(!(op instanceof BatchUpdateOp), "use addOp()");
    repoOnlyOps.add(op);
  }

  @Override
  public void insertChange(InsertChangeOp op) throws IOException {
    Context ctx = new ContextImpl();
    Change c = op.createChange(ctx);
    checkArgument(
        !newChanges.containsKey(c.getId()), "only one op allowed to create change %s", c.getId());
    newChanges.put(c.getId(), c);
    ops.get(c.getId()).add(0, op);
  }
}
