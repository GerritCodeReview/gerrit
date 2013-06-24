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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.index;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Helper for (re)indexing a change document.
 * <p>
 * Indexing is run in the background, as it may require substantial work to
 * compute some of the fields and/or update the index.
 */
public class ChangeIndexerImpl implements ChangeIndexer {
  private final ListeningScheduledExecutorService executor;
  private final DynamicSet<ChangeIndex> indexes;

  @Inject
  ChangeIndexerImpl(@IndexExecutor ListeningScheduledExecutorService executor,
      DynamicSet<ChangeIndex> indexes) {
    this.executor = executor;
    this.indexes = indexes;
  }

  @Override
  public ListenableFuture<?> index(Change change) {
    return index(change, null);
  }

  @Override
  public ListenableFuture<?> index(Change change,
      RequestScopePropagator prop) {
    List<ListenableFuture<?>> futures = Lists.newArrayListWithExpectedSize(2);
    for (ChangeIndex index : indexes) {
      Callable<?> task = new Task(index, change);
      if (prop != null) {
        task = prop.wrap(task);
      }
      futures.add(executor.submit(task));
    }
    return Futures.allAsList(futures);
  }

  private static class Task implements Callable<Void> {
    private final ChangeIndex index;
    private final Change change;

    private Task(ChangeIndex index, Change change) {
      this.index = index;
      this.change = change;
    }

    @Override
    public Void call() throws Exception {
      index.replace(new ChangeData(change));
      return null;
    }

    @Override
    public String toString() {
      return "index-change-" + change.getId().get();
    }
  }
}
