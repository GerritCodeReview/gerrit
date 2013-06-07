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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Helper for (re)indexing a change document.
 * <p>
 * Indexing is run in the background, as it may require substantial work to
 * compute some of the fields and/or update the index.
 */
public class ChangeIndexerImpl implements ChangeIndexer {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeIndexerImpl.class);

  private final ListeningScheduledExecutorService executor;
  private final ChangeIndex index;

  @Inject
  ChangeIndexerImpl(@IndexExecutor ListeningScheduledExecutorService executor,
      ChangeIndex index) throws IOException {
    this.executor = executor;
    this.index = index;
  }

  @Override
  public ListenableFuture<?> index(Change change) {
    return index(change, null);
  }

  @Override
  public ListenableFuture<?> index(Change change,
      RequestScopePropagator prop) {
    Runnable task = new Task(change);
    if (prop != null) {
      task = prop.wrap(task);
    }
    return executor.submit(task);
  }

  private class Task implements Runnable {
    private final Change change;

    private Task(Change change) {
      this.change = change;
    }

    @Override
    public void run() {
      ChangeData cd = new ChangeData(change);
      try {
        index.replace(cd);
      } catch (IOException e) {
        log.error("Error indexing change", e);
      }
    }

    @Override
    public String toString() {
      return "index-change-" + change.getId().get();
    }
  }
}
