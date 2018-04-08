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

import static com.google.gerrit.server.extensions.events.EventUtil.logEventListenerError;
import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.index.Index;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for (re)indexing a change document.
 *
 * <p>Indexing is run in the background, as it may require substantial work to compute some of the
 * fields and/or update the index.
 */
public class ChangeIndexer {
  private static final Logger log = LoggerFactory.getLogger(ChangeIndexer.class);

  public interface Factory {
    ChangeIndexer create(ListeningExecutorService executor, ChangeIndex index);

    ChangeIndexer create(ListeningExecutorService executor, ChangeIndexCollection indexes);
  }

  @SuppressWarnings("deprecation")
  public static com.google.common.util.concurrent.CheckedFuture<?, IOException> allAsList(
      List<? extends ListenableFuture<?>> futures) {
    // allAsList propagates the first seen exception, wrapped in
    // ExecutionException, so we can reuse the same mapper as for a single
    // future. Assume the actual contents of the exception are not useful to
    // callers. All exceptions are already logged by IndexTask.
    return Futures.makeChecked(Futures.allAsList(futures), IndexUtils.MAPPER);
  }

  @Nullable private final ChangeIndexCollection indexes;
  @Nullable private final ChangeIndex index;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final NotesMigration notesMigration;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ThreadLocalRequestContext context;
  private final ListeningExecutorService batchExecutor;
  private final ListeningExecutorService executor;
  private final DynamicSet<ChangeIndexedListener> indexedListeners;
  private final StalenessChecker stalenessChecker;
  private final boolean autoReindexIfStale;

  @AssistedInject
  ChangeIndexer(
      @GerritServerConfig Config cfg,
      SchemaFactory<ReviewDb> schemaFactory,
      NotesMigration notesMigration,
      ChangeNotes.Factory changeNotesFactory,
      ChangeData.Factory changeDataFactory,
      ThreadLocalRequestContext context,
      DynamicSet<ChangeIndexedListener> indexedListeners,
      StalenessChecker stalenessChecker,
      @IndexExecutor(BATCH) ListeningExecutorService batchExecutor,
      @Assisted ListeningExecutorService executor,
      @Assisted ChangeIndex index) {
    this.executor = executor;
    this.schemaFactory = schemaFactory;
    this.notesMigration = notesMigration;
    this.changeNotesFactory = changeNotesFactory;
    this.changeDataFactory = changeDataFactory;
    this.context = context;
    this.indexedListeners = indexedListeners;
    this.stalenessChecker = stalenessChecker;
    this.batchExecutor = batchExecutor;
    this.autoReindexIfStale = autoReindexIfStale(cfg);
    this.index = index;
    this.indexes = null;
  }

  @AssistedInject
  ChangeIndexer(
      SchemaFactory<ReviewDb> schemaFactory,
      @GerritServerConfig Config cfg,
      NotesMigration notesMigration,
      ChangeNotes.Factory changeNotesFactory,
      ChangeData.Factory changeDataFactory,
      ThreadLocalRequestContext context,
      DynamicSet<ChangeIndexedListener> indexedListeners,
      StalenessChecker stalenessChecker,
      @IndexExecutor(BATCH) ListeningExecutorService batchExecutor,
      @Assisted ListeningExecutorService executor,
      @Assisted ChangeIndexCollection indexes) {
    this.executor = executor;
    this.schemaFactory = schemaFactory;
    this.notesMigration = notesMigration;
    this.changeNotesFactory = changeNotesFactory;
    this.changeDataFactory = changeDataFactory;
    this.context = context;
    this.indexedListeners = indexedListeners;
    this.stalenessChecker = stalenessChecker;
    this.batchExecutor = batchExecutor;
    this.autoReindexIfStale = autoReindexIfStale(cfg);
    this.index = null;
    this.indexes = indexes;
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
  @SuppressWarnings("deprecation")
  public com.google.common.util.concurrent.CheckedFuture<?, IOException> indexAsync(
      Project.NameKey project, Change.Id id) {
    return submit(new IndexTask(project, id));
  }

  /**
   * Start indexing multiple changes in parallel.
   *
   * @param ids changes to index.
   * @return future for completing indexing of all changes.
   */
  @SuppressWarnings("deprecation")
  public com.google.common.util.concurrent.CheckedFuture<?, IOException> indexAsync(
      Project.NameKey project, Collection<Change.Id> ids) {
    List<ListenableFuture<?>> futures = new ArrayList<>(ids.size());
    for (Change.Id id : ids) {
      futures.add(indexAsync(project, id));
    }
    return allAsList(futures);
  }

  /**
   * Synchronously index a change.
   *
   * @param cd change to index.
   */
  public void index(ChangeData cd) throws IOException {
    for (Index<?, ChangeData> i : getWriteIndexes()) {
      i.replace(cd);
    }
    fireChangeIndexedEvent(cd.getId().get());

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

  private void fireChangeIndexedEvent(int id) {
    for (ChangeIndexedListener listener : indexedListeners) {
      try {
        listener.onChangeIndexed(id);
      } catch (Exception e) {
        logEventListenerError(listener, e);
      }
    }
  }

  private void fireChangeDeletedFromIndexEvent(int id) {
    for (ChangeIndexedListener listener : indexedListeners) {
      try {
        listener.onChangeDeleted(id);
      } catch (Exception e) {
        logEventListenerError(listener, e);
      }
    }
  }

  /**
   * Synchronously index a change.
   *
   * @param db review database.
   * @param change change to index.
   */
  public void index(ReviewDb db, Change change) throws IOException, OrmException {
    index(newChangeData(db, change));
    // See comment in #index(ChangeData).
    autoReindexIfStale(change.getProject(), change.getId());
  }

  /**
   * Synchronously index a change.
   *
   * @param db review database.
   * @param project the project to which the change belongs.
   * @param changeId ID of the change to index.
   */
  public void index(ReviewDb db, Project.NameKey project, Change.Id changeId)
      throws IOException, OrmException {
    ChangeData cd = newChangeData(db, project, changeId);
    index(cd);
    // See comment in #index(ChangeData).
    autoReindexIfStale(cd);
  }

  /**
   * Start deleting a change.
   *
   * @param id change to delete.
   * @return future for the deleting task.
   */
  @SuppressWarnings("deprecation")
  public com.google.common.util.concurrent.CheckedFuture<?, IOException> deleteAsync(Change.Id id) {
    return submit(new DeleteTask(id));
  }

  /**
   * Synchronously delete a change.
   *
   * @param id change ID to delete.
   */
  public void delete(Change.Id id) throws IOException {
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
  @SuppressWarnings("deprecation")
  public com.google.common.util.concurrent.CheckedFuture<Boolean, IOException> reindexIfStale(
      Project.NameKey project, Change.Id id) {
    return submit(new ReindexIfStaleTask(project, id), batchExecutor);
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

  @SuppressWarnings("deprecation")
  private <T> com.google.common.util.concurrent.CheckedFuture<T, IOException> submit(
      Callable<T> task) {
    return submit(task, executor);
  }

  @SuppressWarnings("deprecation")
  private static <T> com.google.common.util.concurrent.CheckedFuture<T, IOException> submit(
      Callable<T> task, ListeningExecutorService executor) {
    return Futures.makeChecked(
        Futures.nonCancellationPropagating(executor.submit(task)), IndexUtils.MAPPER);
  }

  private abstract class AbstractIndexTask<T> implements Callable<T> {
    protected final Project.NameKey project;
    protected final Change.Id id;

    protected AbstractIndexTask(Project.NameKey project, Change.Id id) {
      this.project = project;
      this.id = id;
    }

    protected abstract T callImpl(Provider<ReviewDb> db) throws Exception;

    @Override
    public abstract String toString();

    @Override
    public final T call() throws Exception {
      try {
        final AtomicReference<Provider<ReviewDb>> dbRef = Atomics.newReference();
        RequestContext newCtx =
            new RequestContext() {
              @Override
              public Provider<ReviewDb> getReviewDbProvider() {
                Provider<ReviewDb> db = dbRef.get();
                if (db == null) {
                  try {
                    db = Providers.of(schemaFactory.open());
                  } catch (OrmException e) {
                    ProvisionException pe = new ProvisionException("error opening ReviewDb");
                    pe.initCause(e);
                    throw pe;
                  }
                  dbRef.set(db);
                }
                return db;
              }

              @Override
              public CurrentUser getUser() {
                throw new OutOfScopeException("No user during ChangeIndexer");
              }
            };
        RequestContext oldCtx = context.setContext(newCtx);
        try {
          return callImpl(newCtx.getReviewDbProvider());
        } finally {
          context.setContext(oldCtx);
          Provider<ReviewDb> db = dbRef.get();
          if (db != null) {
            db.get().close();
          }
        }
      } catch (Exception e) {
        log.error("Failed to execute " + this, e);
        throw e;
      }
    }
  }

  private class IndexTask extends AbstractIndexTask<Void> {
    private IndexTask(Project.NameKey project, Change.Id id) {
      super(project, id);
    }

    @Override
    public Void callImpl(Provider<ReviewDb> db) throws Exception {
      ChangeData cd = newChangeData(db.get(), project, id);
      index(cd);
      return null;
    }

    @Override
    public String toString() {
      return "index-change-" + id;
    }
  }

  // Not AbstractIndexTask as it doesn't need ReviewDb.
  private class DeleteTask implements Callable<Void> {
    private final Change.Id id;

    private DeleteTask(Change.Id id) {
      this.id = id;
    }

    @Override
    public Void call() throws IOException {
      // Don't bother setting a RequestContext to provide the DB.
      // Implementations should not need to access the DB in order to delete a
      // change ID.
      for (ChangeIndex i : getWriteIndexes()) {
        i.delete(id);
      }
      log.info("Deleted change {} from index.", id.get());
      fireChangeDeletedFromIndexEvent(id.get());
      return null;
    }
  }

  private class ReindexIfStaleTask extends AbstractIndexTask<Boolean> {
    private ReindexIfStaleTask(Project.NameKey project, Change.Id id) {
      super(project, id);
    }

    @Override
    public Boolean callImpl(Provider<ReviewDb> db) throws Exception {
      try {
        if (stalenessChecker.isStale(id)) {
          index(newChangeData(db.get(), project, id));
          return true;
        }
      } catch (NoSuchChangeException nsce) {
        log.debug("Change {} was deleted, aborting reindexing the change.", id.get());
      } catch (Exception e) {
        if (!isCausedByRepositoryNotFoundException(e)) {
          throw e;
        }
        log.debug(
            "Change {} belongs to deleted project {}, aborting reindexing the change.",
            id.get(),
            project.get());
      }
      return false;
    }

    @Override
    public String toString() {
      return "reindex-if-stale-change-" + id;
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

  // Avoid auto-rebuilding when reindexing if reading is disabled. This just
  // increases contention on the meta ref from a background indexing thread
  // with little benefit. The next actual write to the entity may still incur a
  // less-contentious rebuild.
  private ChangeData newChangeData(ReviewDb db, Change change) throws OrmException {
    if (!notesMigration.readChanges()) {
      ChangeNotes notes = changeNotesFactory.createWithAutoRebuildingDisabled(change, null);
      return changeDataFactory.create(db, notes);
    }
    return changeDataFactory.create(db, change);
  }

  private ChangeData newChangeData(ReviewDb db, Project.NameKey project, Change.Id changeId)
      throws OrmException {
    if (!notesMigration.readChanges()) {
      ChangeNotes notes =
          changeNotesFactory.createWithAutoRebuildingDisabled(db, project, changeId);
      return changeDataFactory.create(db, notes);
    }
    return changeDataFactory.create(db, project, changeId);
  }
}
