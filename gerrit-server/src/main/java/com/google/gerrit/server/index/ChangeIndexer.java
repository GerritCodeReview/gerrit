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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.change.ChangeData;

import java.util.concurrent.Callable;

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
    public ListenableFuture<?> index(ChangeData cd) {
      return Futures.immediateFuture(null);
    }

    @Override
    public Callable<?> indexTask(ChangeData cd) {
      return new Callable<Void>() {
        @Override
        public Void call() {
          return null;
        }
      };
    }

    @Override
    public Callable<?> deleteTask(ChangeData cd) {
      return new Callable<Void>() {
        @Override
        public Void call() {
          return null;
        }
      };
    }
  };

  private final ListeningScheduledExecutorService executor;

  protected ChangeIndexer(ListeningScheduledExecutorService executor) {
    this.executor = executor;
  }

  /**
   * Start indexing a change.
   *
   * @param change change to index.
   * @return future for the indexing task.
   */
  public ListenableFuture<?> index(Change change) {
    return index(new ChangeData(change));
  }

  /**
   * Start indexing a change.
   *
   * @param cd change to index.
   * @return future for the indexing task.
   */
  public ListenableFuture<?> index(ChangeData cd) {
    return executor != null
        ? executor.submit(indexTask(cd))
        : Futures.immediateFuture(null);
  }

  /**
   * Create a runnable to index a change.
   *
   * @param cd change to index.
   * @return unstarted runnable to index the change.
   */
  public abstract Callable<?> indexTask(ChangeData cd);

  /**
   * Start deleting a change.
   *
   * @param change change to delete.
   * @return future for the deleting task.
   */
  public ListenableFuture<?> delete(Change change) {
    return delete(new ChangeData(change));
  }

  /**
   * Start deleting a change.
   *
   * @param cd change to delete.
   * @return future for the deleting task.
   */
  public ListenableFuture<?> delete(ChangeData cd) {
    return executor != null
        ? executor.submit(deleteTask(cd))
        : Futures.immediateFuture(null);
  }

  /**
   * Create a runnable to delete a change.
   *
   * @param cd change to delete.
   * @return unstarted runnable to delete the change.
   */
  public abstract Callable<?> deleteTask(ChangeData cd);
}
