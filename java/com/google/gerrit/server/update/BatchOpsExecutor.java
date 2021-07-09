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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

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

    BatchOpsExecutor create(
        Project.NameKey project, CurrentUser user, Timestamp when, boolean nonAtomic);
  }

  private final Map<Change.Id, ChangeContextImpl> changeContexts = new HashMap<>();
  private final boolean nonAtomic;

  @AssistedInject
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
      @Assisted Timestamp when,
      @Assisted boolean nonAtomic) {
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
    this.nonAtomic = nonAtomic;
  }

  @AssistedInject
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
    this(
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
        when,
        false);
  }

  public void execute() throws IOException, RestApiException, UpdateException {
    execute(/*dryrun=*/ false);
  }

  public void execute(boolean dryrun) throws RestApiException, UpdateException, IOException {
    checkNotExecuted();
    logger.atFine().log(
        "Executing %d repoOnlyOps and %d bacthUpdateOps as batch on %d refs",
        repoOnlyOps.size(), ops.size(), refsInUpdate());
    initRepository();
    Repository repo = repoView.getRepository();
    checkState(
        repo.getRefDatabase().performsAtomicTransactions(),
        "cannot use NoteDb with a repository that does not support atomic batch ref updates: %s",
        repo);
    try {
      ChangesHandle handle =
          new ChangesHandle(
              updateManagerFactory
                  .create(project, nonAtomic)
                  // .setBatchUpdateListeners(listeners)
                  .setChangeRepo(
                      repo, repoView.getRevWalk(), repoView.getInserter(), repoView.getCommands()),
              dryrun);
      if (user.isIdentifiedUser()) {
        handle.manager.setRefLogIdent(user.asIdentifiedUser().newRefLogIdent(when, tz));
      }
      handle.manager.setRefLogMessage(refLogMessage);
      handle.manager.setPushCertificate(pushCert);
      changeContexts.forEach(handle::applyUpdates);
      // needed?
      this.executed = true;
      handle.execute();
      handle.close();
      List<ListenableFuture<ChangeData>> indexFutures = new ArrayList<>();
      indexFutures.addAll(handle.startIndexFutures());
      executePostSteps(ImmutableList.of(this), indexFutures, dryrun);
    } catch (Exception e) {
      wrapAndThrowException(e);
    }
  }

  public int refsInUpdate() {
    return refsInUpdate.size();
  }

  public void addOps(Change.Id id, Collection<BatchUpdateOp> batchOps) throws Exception {
    checkNotExecuted();
    checkArgument(
        !(ops.containsKey(id)) && !changeContexts.containsKey(id),
        "ops for change %s already added to this batch",
        id);
    PendingRepoContextImpl repoContext = new PendingRepoContextImpl();
    ChangeContextImpl ctx =
        changeContexts.containsKey(id) ? changeContexts.get(id) : newChangeContext(id);
    for (BatchUpdateOp op : batchOps) {
      try (TraceContext.TraceTimer ignored =
          TraceContext.newTimer(op.getClass().getSimpleName() + "#updateRepo", Metadata.empty())) {
        op.updateRepo(repoContext);
      }
      try (TraceContext.TraceTimer ignored =
          TraceContext.newTimer(
              op.getClass().getSimpleName() + "#updateChange", Metadata.empty())) {
        ctx.dirty |= op.updateChange(ctx);
      }
    }
    ImmutableList<ChangeUpdate> allUpdates =
        ImmutableList.<ChangeUpdate>builder()
            .addAll(ctx.defaultUpdates.values())
            .addAll(ctx.distinctUpdates.values())
            .build();
    changeContexts.put(id, ctx);
    repoContext.applyCommands();
    repoContext.close();
    refsInUpdate.addAll(NoteDbUpdateManager.refsInUpdate(id, allUpdates, ctx.deleted));
    this.ops.putAll(id, batchOps);
  }

  @Override
  public void addOp(Change.Id id, BatchUpdateOp op) throws Exception {
    addOps(id, ImmutableList.of(op));
  }

  @Override
  public void addRepoOnlyOp(RepoOnlyOp op) throws Exception {
    checkNotExecuted();
    checkArgument(!(op instanceof BatchUpdateOp), "use addOp()");
    PendingRepoContextImpl repoContext = new PendingRepoContextImpl();
    op.updateRepo(repoContext);
    repoContext.applyCommands();
    repoContext.close();
    repoOnlyOps.add(op);
  }

  @Override
  public void clear() {
    if (!refsInUpdate.isEmpty()) {
      logger.atWarning().log("%d pending ref updates cleared", refsInUpdate.size());
    }
    this.changeContexts.clear();
    super.clear();
  }

  @Override
  public void close() {
    this.clear();
    super.close();
  }
}
