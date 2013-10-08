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
import com.google.common.util.concurrent.Callables;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.change.ChangeData;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Helper for (re)indexing a change document.
 * <p>
 * Indexing is run in the background, as it may require substantial work to
 * compute some of the fields and/or update the index.
 */
public abstract class ChangeIndexer {
  public interface Factory {
    ChangeIndexer create(ChangeIndex index);
    ChangeIndexer create(IndexCollection indexes);
  }

  /** Instance indicating secondary index is disabled. */
  public static final ChangeIndexer DISABLED = new ChangeIndexer(null) {
    @Override
    public CheckedFuture<?, IOException> indexAsync(ChangeData cd) {
      return Futures.immediateCheckedFuture(null);
    }

    @Override
    protected Callable<?> indexTask(ChangeData cd) {
      return Callables.returning(null);
    }

    @Override
    protected Callable<?> deleteTask(ChangeData cd) {
      return Callables.returning(null);
    }
  };

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

  private final ListeningExecutorService executor;

  protected ChangeIndexer(ListeningExecutorService executor) {
    this.executor = executor;
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
        ? submit(indexTask(cd))
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
      indexTask(cd).call();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw MAPPER.apply(e);
    }
  }

  /**
   * Create a runnable to index a change.
   *
   * @param cd change to index.
   * @return unstarted runnable to index the change.
   */
  protected abstract Callable<?> indexTask(ChangeData cd);

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
        ? submit(deleteTask(cd))
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
      deleteTask(cd).call();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw MAPPER.apply(e);
    }
  }

  /**
   * Create a runnable to delete a change.
   *
   * @param cd change to delete.
   * @return unstarted runnable to delete the change.
   */
  protected abstract Callable<?> deleteTask(ChangeData cd);

  private CheckedFuture<?, IOException> submit(Callable<?> task) {
    return Futures.makeChecked(executor.submit(task), MAPPER);
  }
}
