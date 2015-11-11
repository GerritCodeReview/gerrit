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

package com.google.gerrit.server.index;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener.Event;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper for (re)indexing a change document.
 * <p>
 * Indexing is run in the background, as it may require substantial work to
 * compute some of the fields and/or update the index.
 */
public class ChangeIndexer {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeIndexer.class);

  public interface Factory {
    ChangeIndexer create(ListeningExecutorService executor, ChangeIndex index);
    ChangeIndexer create(ListeningExecutorService executor,
        IndexCollection indexes);
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
      } else if (in instanceof ExecutionException
          && in.getCause() instanceof IOException) {
        return (IOException) in.getCause();
      } else {
        return new IOException(in);
      }
    }
  };

  private final IndexCollection indexes;
  private final ChangeIndex index;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ThreadLocalRequestContext context;
  private final ListeningExecutorService executor;
  private final DynamicSet<ChangeIndexedListener> indexedListener;

  @AssistedInject
  ChangeIndexer(SchemaFactory<ReviewDb> schemaFactory,
      ChangeData.Factory changeDataFactory,
      ThreadLocalRequestContext context,
      DynamicSet<ChangeIndexedListener> indexedListener,
      @Assisted ListeningExecutorService executor,
      @Assisted ChangeIndex index) {
    this.executor = executor;
    this.schemaFactory = schemaFactory;
    this.changeDataFactory = changeDataFactory;
    this.context = context;
    this.index = index;
    this.indexes = null;
    this.indexedListener = indexedListener;
  }

  @AssistedInject
  ChangeIndexer(SchemaFactory<ReviewDb> schemaFactory,
      ChangeData.Factory changeDataFactory,
      ThreadLocalRequestContext context,
      DynamicSet<ChangeIndexedListener> indexedListener,
      @Assisted ListeningExecutorService executor,
      @Assisted IndexCollection indexes) {
    this.executor = executor;
    this.schemaFactory = schemaFactory;
    this.changeDataFactory = changeDataFactory;
    this.context = context;
    this.index = null;
    this.indexes = indexes;
    this.indexedListener = indexedListener;
  }

  /**
   * Start indexing a change.
   *
   * @param id change to index.
   * @return future for the indexing task.
   */
  public CheckedFuture<?, IOException> indexAsync(Change.Id id) {
    return executor != null
        ? submit(new IndexTask(id))
        : Futures.<Object, IOException> immediateCheckedFuture(null);
  }

  /**
   * Start indexing multiple changes in parallel.
   *
   * @param ids changes to index.
   * @return future for completing indexing of all changes.
   */
  public CheckedFuture<?, IOException> indexAsync(Collection<Change.Id> ids) {
    List<ListenableFuture<?>> futures = new ArrayList<>(ids.size());
    for (Change.Id id : ids) {
      futures.add(indexAsync(id));
    }
    return allAsList(futures);
  }

  /**
   * Synchronously index a change.
   *
   * @param cd change to index.
   */
  public void index(ChangeData cd) throws IOException {
    for (ChangeIndex i : getWriteIndexes()) {
      i.replace(cd);
    }
    fireChangeIndexedEvent(cd.getId());
  }

  private void fireChangeIndexedEvent(Change.Id id) {
    fireIndexEvent(id);
  }

  private void fireChangeDeletedFromIndexEvent(Change.Id id) {
    fireIndexEvent(id);
  }

  private void fireIndexEvent(Change.Id id) {
    Event indexedEvent = createIndexedEvent(id);
    for (ChangeIndexedListener listener : indexedListener) {
      listener.onChangeIndexed(indexedEvent);
    }
  }

  private ChangeIndexedListener.Event createIndexedEvent(final Change.Id id) {
    return new ChangeIndexedListener.Event() {
      @Override
      public int getChangeId() {
        return id.get();
      }
    };
  }

  /**
   * Synchronously index a change.
   *
   * @param change change to index.
   * @param db review database.
   */
  public void index(ReviewDb db, Change change) throws IOException {
    index(changeDataFactory.create(db, change));
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
        : Futures.<Object, IOException> immediateCheckedFuture(null);
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
    return indexes != null
        ? indexes.getWriteIndexes()
        : Collections.singleton(index);
  }

  private CheckedFuture<?, IOException> submit(Callable<?> task) {
    return Futures.makeChecked(executor.submit(task), MAPPER);
  }

  private class IndexTask implements Callable<Void> {
    private final Change.Id id;

    private IndexTask(Change.Id id) {
      this.id = id;
    }

    @Override
    public Void call() throws Exception {
      try {
        final AtomicReference<Provider<ReviewDb>> dbRef =
            Atomics.newReference();
        RequestContext newCtx = new RequestContext() {
          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            Provider<ReviewDb> db = dbRef.get();
            if (db == null) {
              try {
                db = Providers.of(schemaFactory.open());
              } catch (OrmException e) {
                ProvisionException pe =
                    new ProvisionException("error opening ReviewDb");
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
          ChangeData cd = changeDataFactory.create(
              newCtx.getReviewDbProvider().get(), id);
          index(cd);
          return null;
        } finally  {
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
      fireChangeDeletedFromIndexEvent(id);
      return null;
    }
  }
}
