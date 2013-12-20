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
import com.google.common.util.concurrent.ListeningExecutorService;
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
    ChangeIndexer create(ChangeIndex index);
    ChangeIndexer create(IndexCollection indexes);
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
  private final ThreadLocalRequestContext context;
  private final ListeningExecutorService executor;

  @AssistedInject
  ChangeIndexer(@IndexExecutor ListeningExecutorService executor,
      SchemaFactory<ReviewDb> schemaFactory,
      ThreadLocalRequestContext context,
      @Assisted ChangeIndex index) {
    this.executor = executor;
    this.schemaFactory = schemaFactory;
    this.context = context;
    this.index = index;
    this.indexes = null;
  }

  @AssistedInject
  ChangeIndexer(@IndexExecutor ListeningExecutorService executor,
      SchemaFactory<ReviewDb> schemaFactory,
      ThreadLocalRequestContext context,
      @Assisted IndexCollection indexes) {
    this.executor = executor;
    this.schemaFactory = schemaFactory;
    this.context = context;
    this.index = null;
    this.indexes = indexes;
  }

  /**
   * Start indexing a change.
   *
   * @param change change to index.
   * @return future for the indexing task.
   */
  public CheckedFuture<?, IOException> indexAsync(Change change) {
    return indexAsync(new ChangeData(change));
  }

  /**
   * Start indexing a change.
   *
   * @param cd change to index.
   * @return future for the indexing task.
   */
  public CheckedFuture<?, IOException> indexAsync(ChangeData cd) {
    return executor != null
        ? submit(new Task(cd, false))
        : Futures.<Object, IOException> immediateCheckedFuture(null);
  }

  /**
   * Synchronously index a change.
   *
   * @param change change to index.
   */
  public void index(Change change) throws IOException {
    index(new ChangeData(change));
  }

  /**
   * Synchronously index a change.
   *
   * @param cd change to index.
   */
  public void index(ChangeData cd) throws IOException {
    try {
      new Task(cd, false).call();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw MAPPER.apply(e);
    }
  }

  /**
   * Start deleting a change.
   *
   * @param change change to delete.
   * @return future for the deleting task.
   */
  public CheckedFuture<?, IOException> deleteAsync(Change change) {
    return deleteAsync(new ChangeData(change));
  }

  /**
   * Start deleting a change.
   *
   * @param cd change to delete.
   * @return future for the deleting task.
   */
  public CheckedFuture<?, IOException> deleteAsync(ChangeData cd) {
    return executor != null
        ? submit(new Task(cd, true))
        : Futures.<Object, IOException> immediateCheckedFuture(null);
  }

  /**
   * Synchronously delete a change.
   *
   * @param change change to delete.
   */
  public void delete(Change change) throws IOException {
    delete(new ChangeData(change));
  }

  /**
   * Synchronously delete a change.
   *
   * @param cd change to delete.
   */
  public void delete(ChangeData cd) throws IOException {
    try {
      new Task(cd, true).call();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw MAPPER.apply(e);
    }
  }

  private CheckedFuture<?, IOException> submit(Callable<?> task) {
    return Futures.makeChecked(executor.submit(task), MAPPER);
  }

  private class Task implements Callable<Void> {
    private final ChangeData cd;
    private final boolean delete;

    private Task(ChangeData cd, boolean delete) {
      this.cd = cd;
      this.delete = delete;
    }

    @Override
    public Void call() throws Exception {
      try {
        final AtomicReference<Provider<ReviewDb>> dbRef =
            Atomics.newReference();
        RequestContext oldCtx = context.setContext(new RequestContext() {
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
          public CurrentUser getCurrentUser() {
            throw new OutOfScopeException("No user during ChangeIndexer");
          }
        });
        try {
          if (indexes != null) {
            for (ChangeIndex i : indexes.getWriteIndexes()) {
              apply(i, cd);
            }
          } else {
            apply(index, cd);
          }
          return null;
        } finally  {
          context.setContext(oldCtx);
          Provider<ReviewDb> db = dbRef.get();
          if (db != null) {
            db.get().close();
          }
        }
      } catch (Exception e) {
        log.error(String.format(
            "Failed to index change %d in %s",
            cd.getId().get(), cd.getChange().getProject().get()), e);
        throw e;
      }
    }

    private void apply(ChangeIndex i, ChangeData cd) throws IOException {
      if (delete) {
        i.delete(cd);
      } else {
        i.replace(cd);
      }
    }

    @Override
    public String toString() {
      return "index-change-" + cd.getId().get();
    }
  }
}
