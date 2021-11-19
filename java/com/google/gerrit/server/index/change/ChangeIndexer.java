// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.index.change;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.base.Objects;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.index.Index;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.StalenessCheckResult;
import com.google.gerrit.server.index.options.IsFirstInsertForEntry;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.OutOfScopeException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;

/**
 * Helper for (re)indexing a change document.
 *
 * <p>Indexing is run in the background, as it may require substantial work to compute some of the
 * fields and/or update the index.
 */
public class ChangeIndexer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ChangeIndexer create(ListeningExecutorService executor, ChangeIndex index);

    ChangeIndexer create(ListeningExecutorService executor, ChangeIndexCollection indexes);
  }

  @Nullable private final ChangeIndexCollection indexes;
  @Nullable private final ChangeIndex index;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeNotes.Factory notesFactory;
  private final ThreadLocalRequestContext context;
  private final ListeningExecutorService batchExecutor;
  private final ListeningExecutorService executor;
  private final PluginSetContext<ChangeIndexedListener> indexedListeners;
  private final StalenessChecker stalenessChecker;
  private final boolean autoReindexIfStale;
  private final IsFirstInsertForEntry isFirstInsertForEntry;

  private final Set<IndexTask> queuedIndexTasks =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<ReindexIfStaleTask> queuedReindexIfStaleTasks =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  @AssistedInject
  ChangeIndexer(
      @GerritServerConfig Config cfg,
      ChangeData.Factory changeDataFactory,
      ChangeNotes.Factory notesFactory,
      ThreadLocalRequestContext context,
      PluginSetContext<ChangeIndexedListener> indexedListeners,
      StalenessChecker stalenessChecker,
      @IndexExecutor(BATCH) ListeningExecutorService batchExecutor,
      @Assisted ListeningExecutorService executor,
      @Assisted ChangeIndex index,
      IsFirstInsertForEntry isFirstInsertForEntry) {
    this.executor = executor;
    this.changeDataFactory = changeDataFactory;
    this.notesFactory = notesFactory;
    this.context = context;
    this.indexedListeners = indexedListeners;
    this.stalenessChecker = stalenessChecker;
    this.batchExecutor = batchExecutor;
    this.autoReindexIfStale = autoReindexIfStale(cfg);
    this.index = index;
    this.indexes = null;
    this.isFirstInsertForEntry = isFirstInsertForEntry;
  }

  @AssistedInject
  ChangeIndexer(
      @GerritServerConfig Config cfg,
      ChangeData.Factory changeDataFactory,
      ChangeNotes.Factory notesFactory,
      ThreadLocalRequestContext context,
      PluginSetContext<ChangeIndexedListener> indexedListeners,
      StalenessChecker stalenessChecker,
      @IndexExecutor(BATCH) ListeningExecutorService batchExecutor,
      @Assisted ListeningExecutorService executor,
      @Assisted ChangeIndexCollection indexes,
      IsFirstInsertForEntry isFirstInsertForEntry) {
    this.executor = executor;
    this.changeDataFactory = changeDataFactory;
    this.notesFactory = notesFactory;
    this.context = context;
    this.indexedListeners = indexedListeners;
    this.stalenessChecker = stalenessChecker;
    this.batchExecutor = batchExecutor;
    this.autoReindexIfStale = autoReindexIfStale(cfg);
    this.index = null;
    this.indexes = indexes;
    this.isFirstInsertForEntry = isFirstInsertForEntry;
  }

  private static boolean autoReindexIfStale(Config cfg) {
    return cfg.getBoolean("index", null, "autoReindexIfStale", false);
  }

  /**
   * Start indexing a change.
   *
   * @param id change to index.
   * @return future for the indexing task.
   */
  public ListenableFuture<?> indexAsync(Project.NameKey project, Change.Id id) {
    IndexTask task = new IndexTask(project, id);
    if (queuedIndexTasks.add(task)) {
      fireChangeScheduledForIndexingEvent(project.get(), id.get());
      return submit(task);
    }
    return Futures.immediateFuture(null);
  }

  /**
   * Start indexing multiple changes in parallel.
   *
   * @param ids changes to index.
   * @return future for completing indexing of all changes.
   */
  public ListenableFuture<?> indexAsync(Project.NameKey project, Collection<Change.Id> ids) {
    List<ListenableFuture<?>> futures = new ArrayList<>(ids.size());
    for (Change.Id id : ids) {
      futures.add(indexAsync(project, id));
    }
    return Futures.allAsList(futures);
  }

  /**
   * Synchronously index a change, then check if the index is stale due to a race condition.
   *
   * @param cd change to index.
   */
  public void index(ChangeData cd) {
    fireChangeScheduledForIndexingEvent(cd.project().get(), cd.getId().get());
    doIndex(cd);
  }

  private void doIndex(ChangeData cd) {
    indexImpl(cd);

    // Always double-check whether the change might be stale immediately after
    // interactively indexing it. This fixes up the case where two writers write
    // to the primary storage in one order, and the corresponding index writes
    // happen in the opposite order:
    //  1. Writer A writes to primary storage.
    //  2. Writer B writes to primary storage.
    //  3. Writer B updates index.
    //  4. Writer A updates index.
    //
    // Without the extra reindexIfStale step, A has no way of knowing that it's
    // about to overwrite the index document with stale data. It doesn't work to
    // have A check for staleness before attempting its index update, because
    // B's index update might not have happened when it does the check.
    //
    // With the extra reindexIfStale step after (3)/(4), we are able to detect
    // and fix the staleness. It doesn't matter which order the two
    // reindexIfStale calls actually execute in; we are guaranteed that at least
    // one of them will execute after the second index write, (4).
    autoReindexIfStale(cd);
  }

  private void indexImpl(ChangeData cd) {
    logger.atFine().log("Reindex change %d in index.", cd.getId().get());
    for (Index<?, ChangeData> i : getWriteIndexes()) {
      try (TraceTimer traceTimer =
          TraceContext.newTimer(
              "Reindexing change in index",
              Metadata.builder()
                  .changeId(cd.getId().get())
                  .patchSetId(cd.currentPatchSet().number())
                  .indexVersion(i.getSchema().getVersion())
                  .build())) {
        if (isFirstInsertForEntry.equals(isFirstInsertForEntry.YES)) {
          i.insert(cd);
        } else {
          i.replace(cd);
        }
      } catch (RuntimeException e) {
        throw new StorageException(
            String.format(
                "Failed to reindex change %d in index version %d (current patch set = %d)",
                cd.getId().get(), i.getSchema().getVersion(), cd.currentPatchSet().number()),
            e);
      }
    }
    fireChangeIndexedEvent(cd.project().get(), cd.getId().get());
  }

  private void fireChangeScheduledForIndexingEvent(String projectName, int id) {
    indexedListeners.runEach(l -> l.onChangeScheduledForIndexing(projectName, id));
  }

  private void fireChangeIndexedEvent(String projectName, int id) {
    indexedListeners.runEach(l -> l.onChangeIndexed(projectName, id));
  }

  private void fireChangeScheduledForDeletionFromIndexEvent(int id) {
    indexedListeners.runEach(l -> l.onChangeScheduledForDeletionFromIndex(id));
  }

  private void fireChangeDeletedFromIndexEvent(int id) {
    indexedListeners.runEach(l -> l.onChangeDeleted(id));
  }

  /**
   * Synchronously index a change.
   *
   * @param change change to index.
   */
  public void index(Change change) {
    index(changeDataFactory.create(change));
  }

  /**
   * Synchronously index a change.
   *
   * @param project the project to which the change belongs.
   * @param changeId ID of the change to index.
   */
  public void index(Project.NameKey project, Change.Id changeId) {
    index(changeDataFactory.create(project, changeId));
  }

  /**
   * Start deleting a change.
   *
   * @param id change to delete.
   * @return future for the deleting task.
   */
  public ListenableFuture<?> deleteAsync(Change.Id id) {
    fireChangeScheduledForDeletionFromIndexEvent(id.get());
    return submit(new DeleteTask(id));
  }

  /**
   * Synchronously delete a change.
   *
   * @param id change ID to delete.
   */
  public void delete(Change.Id id) {
    fireChangeScheduledForDeletionFromIndexEvent(id.get());
    doDelete(id);
  }

  private void doDelete(Change.Id id) {
    new DeleteTask(id).call();
  }

  /**
   * Asynchronously check if a change is stale, and reindex if it is.
   *
   * <p>Always run on the batch executor, even if this indexer instance is configured to use a
   * different executor.
   *
   * @param project the project to which the change belongs.
   * @param id ID of the change to index.
   * @return future for reindexing the change; returns true if the change was stale.
   */
  public ListenableFuture<Boolean> reindexIfStale(Project.NameKey project, Change.Id id) {
    ReindexIfStaleTask task = new ReindexIfStaleTask(project, id);
    if (queuedReindexIfStaleTasks.add(task)) {
      return submit(task, batchExecutor);
    }
    return Futures.immediateFuture(false);
  }

  private void autoReindexIfStale(ChangeData cd) {
    autoReindexIfStale(cd.project(), cd.getId());
  }

  private void autoReindexIfStale(Project.NameKey project, Change.Id id) {
    if (autoReindexIfStale) {
      // Don't retry indefinitely; if this fails the change will be stale.
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError = reindexIfStale(project, id);
    }
  }

  private Collection<ChangeIndex> getWriteIndexes() {
    return indexes != null ? indexes.getWriteIndexes() : Collections.singleton(index);
  }

  private <T> ListenableFuture<T> submit(Callable<T> task) {
    return submit(task, executor);
  }

  private static <T> ListenableFuture<T> submit(
      Callable<T> task, ListeningExecutorService executor) {
    return Futures.nonCancellationPropagating(executor.submit(task));
  }

  private abstract class AbstractIndexTask<T> implements Callable<T> {
    protected final Project.NameKey project;
    protected final Change.Id id;

    protected AbstractIndexTask(Project.NameKey project, Change.Id id) {
      this.project = project;
      this.id = id;
    }

    protected abstract T callImpl() throws Exception;

    protected abstract void remove();

    @Override
    public abstract String toString();

    @Override
    public final T call() throws Exception {
      try {
        RequestContext newCtx =
            () -> {
              throw new OutOfScopeException("No user during ChangeIndexer");
            };
        RequestContext oldCtx = context.setContext(newCtx);
        try {
          return callImpl();
        } finally {
          context.setContext(oldCtx);
        }
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Failed to execute %s", this);
        throw e;
      }
    }
  }

  private class IndexTask extends AbstractIndexTask<Void> {
    private IndexTask(Project.NameKey project, Change.Id id) {
      super(project, id);
    }

    @Override
    public Void callImpl() throws Exception {
      remove();
      try {
        ChangeNotes changeNotes = notesFactory.createChecked(project, id);
        doIndex(changeDataFactory.create(changeNotes));
      } catch (NoSuchChangeException e) {
        doDelete(id);
      }
      return null;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(IndexTask.class, id.get());
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexTask)) {
        return false;
      }
      IndexTask other = (IndexTask) obj;
      return id.get() == other.id.get();
    }

    @Override
    public String toString() {
      return "index-change-" + id;
    }

    @Override
    protected void remove() {
      queuedIndexTasks.remove(this);
    }
  }

  // Not AbstractIndexTask as it doesn't need a request context.
  private class DeleteTask implements Callable<Void> {
    private final Change.Id id;

    private DeleteTask(Change.Id id) {
      this.id = id;
    }

    @Override
    public Void call() {
      logger.atFine().log("Delete change %d from index.", id.get());
      // Don't bother setting a RequestContext to provide the DB.
      // Implementations should not need to access the DB in order to delete a
      // change ID.
      for (ChangeIndex i : getWriteIndexes()) {
        try (TraceTimer traceTimer =
            TraceContext.newTimer(
                "Deleting change in index",
                Metadata.builder()
                    .changeId(id.get())
                    .indexVersion(i.getSchema().getVersion())
                    .build())) {
          i.delete(id);
        } catch (RuntimeException e) {
          throw new StorageException(
              String.format(
                  "Failed to delete change %d from index version %d",
                  id.get(), i.getSchema().getVersion()),
              e);
        }
      }
      fireChangeDeletedFromIndexEvent(id.get());
      return null;
    }
  }

  private class ReindexIfStaleTask extends AbstractIndexTask<Boolean> {
    private ReindexIfStaleTask(Project.NameKey project, Change.Id id) {
      super(project, id);
    }

    @Override
    public Boolean callImpl() throws Exception {
      remove();
      try {
        StalenessCheckResult stalenessCheckResult = stalenessChecker.check(id);
        if (stalenessCheckResult.isStale()) {
          logger.atInfo().log("Reindexing stale document %s", stalenessCheckResult);
          indexImpl(changeDataFactory.create(project, id));
          return true;
        }
      } catch (Exception e) {
        if (!isCausedByRepositoryNotFoundException(e)) {
          throw e;
        }
        logger.atFine().log(
            "Change %s belongs to deleted project %s, aborting reindexing the change.",
            id.get(), project.get());
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(ReindexIfStaleTask.class, id.get());
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ReindexIfStaleTask)) {
        return false;
      }
      ReindexIfStaleTask other = (ReindexIfStaleTask) obj;
      return id.get() == other.id.get();
    }

    @Override
    public String toString() {
      return "reindex-if-stale-change-" + id;
    }

    @Override
    protected void remove() {
      queuedReindexIfStaleTasks.remove(this);
    }
  }

  private boolean isCausedByRepositoryNotFoundException(Throwable throwable) {
    while (throwable != null) {
      if (throwable instanceof RepositoryNotFoundException) {
        return true;
      }
      throwable = throwable.getCause();
    }
    return false;
  }
}
