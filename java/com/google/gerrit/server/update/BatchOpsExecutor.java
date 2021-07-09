// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.InsertedObject;
import com.google.gerrit.server.git.validators.OnSubmitValidators;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Helper to execute a set of {@link BatchUpdateOp} on potentially unrelated changes, combining them
 * in a single {@link BatchRefUpdate} providing the control on number of refs that will be affected
 * by the execution.
 *
 * <p>While {@link BatchUpdate} is used to execute single conceptual operations (e.g. submission or
 * Rest API call), this helper provides a way to execute similar updates on unrelated changes in
 * retryable batches.
 *
 * <ul>
 *   <li>{@link BatchUpdateOp} or {@link RepoOnlyOp} can be added to the planned execution via
 *       {@link #addOpsBatch} and {@link #addRepoOnlyOpsBatch}. If the {@link
 *       BatchUpdateOp#updateChange} on the change has failed, the caller may choose to ignore the
 *       error and continue extending the batch.
 *   <li>The caller can than inspect number of refs via {@link #refsInUpdate} and execute the
 *       update, as soon as number of refs matches their limit.
 *   <li>The caller can than inspect the results of ref updates via {@link #getRefUpdates}, to
 *       decide if any of the operations need to be retried.
 *   <li>The caller can either start a new instance for the next batch or reuse the same instance
 *       after {@link #clear}.
 *   <li>For simple cases of {@link BatchUpdateOp}, that only affect single change and it's
 *       corresponding meta ref in {@link NoteDbUpdateManager}, the caller can configure non-atomic
 *       {@link BatchRefUpdate} and retry the failed ref updates if needed.
 * </ul>
 *
 * Post-updates, such as reindexing, {@link BatchUpdateOp#postUpdate}, {@link GitReferenceUpdated}
 * are applied as soon as the storage mutations for the entire batch are completed.
 */
public class BatchOpsExecutor extends BatchUpdate implements AutoCloseable {
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

  private final boolean nonAtomic;

  private final Map<Change.Id, ChangeContextImpl> changeContexts = new HashMap<>();
  protected final Set<String> refsInUpdate = new HashSet<>();
  private InMemoryInserter tempIns;
  private RevWalk rw;
  private RepoView tempRepoView;

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

  /**
   * An implementation of {@link Context} that allows to delay applying {@link BatchUpdateOp}
   * updates to the global {@link #repoView}, but still takes into account all operations already
   * applied to {@link #repoView}.
   */
  private class PendingUpdateContextImpl extends ContextImpl {

    @Override
    public RepoView getRepoView() throws IOException {
      return getTempRepoView();
    }

    @Override
    public RevWalk getRevWalk() throws IOException {
      return getTempRepoView().getRevWalk();
    }
  }

  protected class PendingRepoContextImpl extends PendingUpdateContextImpl implements RepoContext {

    @Override
    public ObjectInserter getInserter() throws IOException {
      return getTempRepoView().getInserterWrapper();
    }

    @Override
    public void addRefUpdate(ReceiveCommand cmd) throws IOException {
      getTempRepoView().getCommands().add(cmd);
    }
  }

  protected class PendingChangeContextImpl extends ChangeContextImpl implements ChangeContext {

    PendingChangeContextImpl(ChangeNotes notes) {
      super(notes);
    }

    @Override
    public RepoView getRepoView() throws IOException {
      return getTempRepoView();
    }

    @Override
    public RevWalk getRevWalk() throws IOException {
      return getTempRepoView().getRevWalk();
    }
  }

  private RepoView getTempRepoView() {
    return tempRepoView;
  }

  /**
   * Opens new temporary {@link RepoView} based on global {@link #repoView} to avoid mutations of
   * global {@link #repoView} during {@link BatchUpdateOp} context updates.
   */
  private void initNewTempRepoView() throws IOException {
    closeTempRepoView();
    tempIns = new InMemoryInserter(getRepoView().getRevWalk().getObjectReader());
    rw = new RevWalk(tempIns.newReader());
    tempRepoView =
        new RepoView(getRepoView().getRepository(), rw, tempIns, getRepoView().getCommands());
  }

  /**
   * Applies modifications in {@link #tempRepoView} to global {@link #repoView} and closes {@link
   * #tempRepoView}.
   */
  public void applyCommands() throws IOException {
    if (tempRepoView != null) {
      for (InsertedObject obj : tempIns.getInsertedObjects()) {
        repoView.getInserterWrapper().insert(obj.type(), obj.data().toByteArray());
      }
      for (ReceiveCommand receiveCommand : tempRepoView.getCommands().getCommands().values()) {
        getRepoView().getCommands().add(receiveCommand);
        refsInUpdate.add(receiveCommand.getRefName());
      }
      closeTempRepoView();
    }
  }

  protected void closeTempRepoView() {
    if (tempRepoView != null) {
      tempIns.close();
      rw.getObjectReader().close();
      rw.close();
      tempRepoView.close();
      rw = null;
      tempRepoView = null;
      tempIns = null;
    }
  }

  @Override
  public void execute() throws RestApiException, UpdateException {
    execute(/*dryrun=*/ false);
  }

  @Override
  public void execute(BatchUpdateListener listener) throws UpdateException, RestApiException {
    checkNotExecuted();
    // No listeners available
    execute(false);
  }

  public void execute(boolean dryrun) throws RestApiException, UpdateException {
    try {
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
      ChangesHandle handle =
          new ChangesHandle(
              updateManagerFactory
                  .create(project, nonAtomic)
                  .setChangeRepo(
                      repo, repoView.getRevWalk(), repoView.getInserter(), repoView.getCommands()),
              dryrun);
      if (user.isIdentifiedUser()) {
        handle.manager.setRefLogIdent(user.asIdentifiedUser().newRefLogIdent(when, tz));
      }
      handle.manager.setRefLogMessage(refLogMessage);
      handle.manager.setPushCertificate(pushCert);
      changeContexts.forEach(handle::applyUpdates);
      this.executed = true;
      handle.execute();
      List<ListenableFuture<ChangeData>> indexFutures = handle.startIndexFutures();
      handle.close();
      // Execute all post-steps after actual modifications to storage were applied, to avoid
      // blocking on indexing results.
      executePostUpdate(ImmutableList.of(this), indexFutures, dryrun);
    } catch (Exception e) {
      wrapAndThrowException(e);
    }
  }

  /** Returns the number of refs that are planned for update with the current execution batch. */
  public int refsInUpdate() {
    return refsInUpdate.size();
  }

  @Override
  public BatchUpdate setOnSubmitValidators(OnSubmitValidators onSubmitValidators) {
    throw new UnsupportedOperationException(
        "Submit operations are not supported in BatchOpsExecutor");
  }

  /**
   * Base methods are explicitly overridden, since they do not modify {@link Context} and {@link
   * #execute} will be no-op.
   */
  @Override
  public BatchUpdate addOp(Change.Id id, BatchUpdateOp op) {
    throw new UnsupportedOperationException("use addOpBatch()");
  }

  @Override
  public BatchUpdate addRepoOnlyOp(RepoOnlyOp op) {
    throw new UnsupportedOperationException("use addRepoOnlyOpBatch()");
  }

  @Override
  public BatchUpdate insertChange(InsertChangeOp op) throws IOException {
    throw new UnsupportedOperationException(
        "Change insertion is not supported in BatchOpsExecutor");
  }

  /**
   * Adds {@link BatchUpdateOp} to the current planned execution batch. Same change can only be
   * added once to the current execution.
   *
   * <p>Only applies modifications to the global {@link #repoView} if all {@link
   * BatchUpdateOp#updateRepo} and {@link BatchUpdateOp#updateChange} were applied successfully to
   * {@link Context}. Thus, the caller may choose to ignore errors thrown by this method and
   * continue extending the execution batch.
   *
   * @param id change that should be added to the execution batch.
   * @param batchOps operations that should be applied to this change with the current execution.
   * @return this {@link BatchOpsExecutor}
   * @throws Exception if any of the {@code batchOps} failed to update {@link Context}.
   */
  public BatchUpdate addOpsBatch(Change.Id id, Collection<BatchUpdateOp> batchOps)
      throws Exception {
    checkNotExecuted();
    checkArgument(
        !(ops.containsKey(id)) && !changeContexts.containsKey(id),
        "ops for change %s already added to this batch",
        id);
    initNewTempRepoView();
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
    applyCommands();
    changeContexts.put(id, ctx);
    ImmutableList<ChangeUpdate> allUpdates =
        ImmutableList.<ChangeUpdate>builder()
            .addAll(ctx.defaultUpdates.values())
            .addAll(ctx.distinctUpdates.values())
            .build();
    refsInUpdate.addAll(NoteDbUpdateManager.refsToUpdate(id, allUpdates, ctx.deleted));
    this.ops.putAll(id, batchOps);
    return this;
  }

  public BatchUpdate addOpBatch(Change.Id id, BatchUpdateOp op) throws Exception {
    addOpsBatch(id, ImmutableList.of(op));
    return this;
  }

  /**
   * Adds {@link RepoOnlyOp} to the current planned execution batch.
   *
   * <p>Only applies modifications to the global {@link #repoView} if all {@link
   * RepoOnlyOp#updateRepo} were applied successfully to {@link Context}. Thus, the caller may
   * choose to ignore errors thrown by this method and continue extending the execution batch.
   *
   * @param batchOps operations to add to the current execution.
   * @return this {@link BatchOpsExecutor}
   * @throws Exception if any of the {@code batchOps} failed to update {@link Context}
   */
  public BatchUpdate addRepoOnlyOpsBatch(Collection<RepoOnlyOp> batchOps) throws Exception {
    checkNotExecuted();
    initNewTempRepoView();
    PendingRepoContextImpl repoContext = new PendingRepoContextImpl();
    for (RepoOnlyOp op : batchOps) {
      checkArgument(!(op instanceof BatchUpdateOp), "use addOpsBatch()");
      op.updateRepo(repoContext);
    }
    applyCommands();
    for (RepoOnlyOp op : batchOps) {
      repoOnlyOps.add(op);
    }
    return this;
  }

  public BatchUpdate addRepoOnlyOpBatch(RepoOnlyOp op) throws Exception {
    addRepoOnlyOpsBatch(ImmutableList.of(op));
    return this;
  }

  /**
   * Clears the state so that it is possible to reuse same {@link BatchOpsExecutor} for the next
   * batch.
   */
  public void clear() {
    if (!refsInUpdate.isEmpty()) {
      logger.atWarning().log("%d pending ref updates cleared", refsInUpdate.size());
    }
    this.changeContexts.clear();
    this.newChanges.clear();
    this.ops.clear();
    this.repoOnlyOps.clear();
    this.refsInUpdate.clear();
    this.executed = false;
    this.batchRefUpdate = null;
    if (repoView != null) {
      repoView.close();
      repoView = null;
    }
  }

  @Override
  public void close() {
    this.clear();
    super.close();
  }
}
