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
import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;
import static com.google.common.flogger.LazyArgs.lazy;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
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
import com.google.gerrit.server.git.validators.OnSubmitValidators;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/**
 * Helper for a set of change updates that should be applied to the NoteDb database.
 *
 * <p>An update operation can be divided into three phases:
 *
 * <ol>
 *   <li>Git reference updates
 *   <li>Review metadata updates
 *   <li>Post-update steps
 *   <li>
 * </ol>
 *
 * A single conceptual operation, such as a REST API call or a merge operation, may make multiple
 * changes at each step, which all need to be serialized relative to each other. Moreover, for
 * consistency, the git ref updates must be visible to the review metadata updates, since for
 * example the metadata might refer to newly-created patch set refs. In NoteDb, this is accomplished
 * by combining these two phases into a single {@link BatchRefUpdate}.
 *
 * <p>Similarly, all post-update steps, such as sending email, must run only after all storage
 * mutations have completed.
 */
public class BatchUpdate extends AbstractBatchUpdate implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Module module() {
    return new FactoryModule() {
      @Override
      public void configure() {
        factory(BatchUpdate.Factory.class);
      }
    };
  }

  public interface Factory {
    BatchUpdate create(Project.NameKey project, CurrentUser user, Timestamp when);
  }

  public static void execute(
      Collection<BatchUpdate> updates, ImmutableList<BatchUpdateListener> listeners, boolean dryrun)
      throws UpdateException, RestApiException {
    requireNonNull(listeners);
    if (updates.isEmpty()) {
      return;
    }

    checkDifferentProject(updates);

    try {
      List<ListenableFuture<ChangeData>> indexFutures = new ArrayList<>();
      List<ChangesHandle> changesHandles = new ArrayList<>(updates.size());
      try {
        for (BatchUpdate u : updates) {
          u.executeUpdateRepo();
        }
        notifyAfterUpdateRepo(listeners);
        for (BatchUpdate u : updates) {
          changesHandles.add(u.executeChangeOps(listeners, dryrun));
        }
        for (ChangesHandle h : changesHandles) {
          h.execute();
          indexFutures.addAll(h.startIndexFutures());
        }
        notifyAfterUpdateRefs(listeners);
        notifyAfterUpdateChanges(listeners);
      } finally {
        for (ChangesHandle h : changesHandles) {
          h.close();
        }
      }
      // Fire ref update events only after all mutations are finished, since callers may assume a
      // patch set ref being created means the change was created, or a branch advancing meaning
      // some changes were closed.
      executePostSteps(updates, indexFutures, dryrun);
    } catch (Exception e) {
      wrapAndThrowException(e);
    }
  }

  private static void notifyAfterUpdateRepo(ImmutableList<BatchUpdateListener> listeners)
      throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateRepos();
    }
  }

  private static void notifyAfterUpdateRefs(ImmutableList<BatchUpdateListener> listeners)
      throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateRefs();
    }
  }

  private static void notifyAfterUpdateChanges(ImmutableList<BatchUpdateListener> listeners)
      throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateChanges();
    }
  }

  private static void checkDifferentProject(Collection<BatchUpdate> updates) {
    Multiset<Project.NameKey> projectCounts =
        updates.stream().map(u -> u.project).collect(toImmutableMultiset());
    checkArgument(
        projectCounts.entrySet().size() == updates.size(),
        "updates must all be for different projects, got: %s",
        projectCounts);
  }

  private OnSubmitValidators onSubmitValidators;

  @Inject
  BatchUpdate(
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

  public void execute(BatchUpdateListener listener) throws UpdateException, RestApiException {
    checkNotExecuted();
    execute(ImmutableList.of(this), ImmutableList.of(listener), false);
  }

  public void execute() throws UpdateException, RestApiException {
    checkNotExecuted();
    execute(ImmutableList.of(this), ImmutableList.of(), false);
  }

  /**
   * Add a validation step for intended ref operations, which will be performed at the end of {@link
   * RepoOnlyOp#updateRepo(RepoContext)} step.
   */
  public void setOnSubmitValidators(OnSubmitValidators onSubmitValidators) {
    this.onSubmitValidators = onSubmitValidators;
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

  public void insertChange(InsertChangeOp op) throws IOException {
    Context ctx = new ContextImpl();
    Change c = op.createChange(ctx);
    checkArgument(
        !newChanges.containsKey(c.getId()), "only one op allowed to create change %s", c.getId());
    newChanges.put(c.getId(), c);
    ops.get(c.getId()).add(0, op);
  }

  private void executeUpdateRepo() throws UpdateException, RestApiException {
    try {
      logDebug("Executing updateRepo on %d ops", ops.size());
      RepoContextImpl ctx = new RepoContextImpl();
      for (BatchUpdateOp op : ops.values()) {
        try (TraceContext.TraceTimer ignored =
            TraceContext.newTimer(
                op.getClass().getSimpleName() + "#updateRepo", Metadata.empty())) {
          op.updateRepo(ctx);
        }
      }

      logDebug("Executing updateRepo on %d RepoOnlyOps", repoOnlyOps.size());
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
    } catch (Exception e) {
      Throwables.throwIfInstanceOf(e, RestApiException.class);
      throw new UpdateException(e);
    }
  }

  private ChangesHandle executeChangeOps(
      ImmutableList<BatchUpdateListener> batchUpdateListeners, boolean dryrun) throws Exception {
    logDebug("Executing change ops");
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
                .setBatchUpdateListeners(batchUpdateListeners)
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
      logDebug(
          "Applying %d ops for change %s: %s",
          e.getValue().size(),
          id,
          lazy(() -> e.getValue().stream().map(op -> op.getClass().getName()).collect(toSet())));
      for (BatchUpdateOp op : e.getValue()) {
        try (TraceContext.TraceTimer ignored =
            TraceContext.newTimer(
                op.getClass().getSimpleName() + "#updateChange", Metadata.empty())) {
          ctx.dirty |= op.updateChange(ctx);
        }
      }
      handle.applyUpdates(id, ctx);
    }
    return handle;
  }
}
