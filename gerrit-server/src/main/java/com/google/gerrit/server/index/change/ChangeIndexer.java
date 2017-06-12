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

import com.google.common.base.Function;
import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
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

  public static CheckedFuture<?, IOException> allAsList(
      List<? extends ListenableFuture<?>> futures) {
    // allAsList propagates the first seen exception, wrapped in
    // ExecutionException, so we can reuse the same mapper as for a single
    // future. Assume the actual contents of the exception are not useful to
    // callers. All exceptions are already logged by IndexTask.
    return Futures.makeChecked(Futures.allAsList(futures), MAPPER);
  }

  private static final Function<Exception, IOException> MAPPER =
      new Function<Exception, IOException>() {
        @Override
        public IOException apply(Exception in) {
          if (in instanceof IOException) {
            return (IOException) in;
          } else if (in instanceof ExecutionException && in.getCause() instanceof IOException) {
            return (IOException) in.getCause();
          } else {
            return new IOException(in);
          }
        }
      };

  private final ChangeIndexCollection indexes;
  private final ChangeIndex index;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final NotesMigration notesMigration;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ThreadLocalRequestContext context;
  private final ListeningExecutorService executor;
  private final DynamicSet<ChangeIndexedListener> indexedListeners;

  @AssistedInject
  ChangeIndexer(
      SchemaFactory<ReviewDb> schemaFactory,
      NotesMigration notesMigration,
      ChangeNotes.Factory changeNotesFactory,
      ChangeData.Factory changeDataFactory,
      ThreadLocalRequestContext context,
      DynamicSet<ChangeIndexedListener> indexedListeners,
      @Assisted ListeningExecutorService executor,
      @Assisted ChangeIndex index) {
    this.executor = executor;
    this.schemaFactory = schemaFactory;
    this.notesMigration = notesMigration;
    this.changeNotesFactory = changeNotesFactory;
    this.changeDataFactory = changeDataFactory;
    this.context = context;
    this.indexedListeners = indexedListeners;
    this.index = index;
    this.indexes = null;
  }

  @AssistedInject
  ChangeIndexer(
      SchemaFactory<ReviewDb> schemaFactory,
      NotesMigration notesMigration,
      ChangeNotes.Factory changeNotesFactory,
      ChangeData.Factory changeDataFactory,
      ThreadLocalRequestContext context,
      DynamicSet<ChangeIndexedListener> indexedListeners,
      @Assisted ListeningExecutorService executor,
      @Assisted ChangeIndexCollection indexes) {
    this.executor = executor;
    this.schemaFactory = schemaFactory;
    this.notesMigration = notesMigration;
    this.changeNotesFactory = changeNotesFactory;
    this.changeDataFactory = changeDataFactory;
    this.context = context;
    this.indexedListeners = indexedListeners;
    this.index = null;
    this.indexes = indexes;
  }

  /**
   * Start indexing a change.
   *
   * @param id change to index.
   * @return future for the indexing task.
   */
  public CheckedFuture<?, IOException> indexAsync(Project.NameKey project, Change.Id id) {
    return executor != null
        ? submit(new IndexTask(project, id))
        : Futures.<Object, IOException>immediateCheckedFuture(null);
  }

  /**
   * Start indexing multiple changes in parallel.
   *
   * @param ids changes to index.
   * @return future for completing indexing of all changes.
   */
  public CheckedFuture<?, IOException> indexAsync(
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
    index(newChangeData(db, project, changeId));
  }

  /**
   * Start deleting a change.
   *
   * @param id change to delete.
   * @return future for the deleting task.
   */
  public CheckedFuture<?, IOException> deleteAsync(Change.Id id) {
    return executor != null
        ? submit(new DeleteTask(id))
        : Futures.<Object, IOException>immediateCheckedFuture(null);
  }

  /**
   * Synchronously delete a change.
   *
   * @param id change ID to delete.
   */
  public void delete(Change.Id id) throws IOException {
    new DeleteTask(id).call();
  }

  private Collection<ChangeIndex> getWriteIndexes() {
    return indexes != null ? indexes.getWriteIndexes() : Collections.singleton(index);
  }

  private CheckedFuture<?, IOException> submit(Callable<?> task) {
    return Futures.makeChecked(Futures.nonCancellationPropagating(executor.submit(task)), MAPPER);
  }

  private class IndexTask implements Callable<Void> {
    private final Project.NameKey project;
    private final Change.Id id;

    private IndexTask(Project.NameKey project, Change.Id id) {
      this.project = project;
      this.id = id;
    }

    @Override
    public Void call() throws Exception {
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
          ChangeData cd = newChangeData(newCtx.getReviewDbProvider().get(), project, id);
          index(cd);
          return null;
        } finally {
          context.setContext(oldCtx);
          Provider<ReviewDb> db = dbRef.get();
          if (db != null) {
            db.get().close();
          }
        }
      } catch (Exception e) {
        log.error(String.format("Failed to index change %d", id.get()), e);
        throw e;
      }
    }

    @Override
    public String toString() {
      return "index-change-" + id.get();
    }
  }

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
