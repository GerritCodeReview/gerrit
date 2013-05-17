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

import com.google.common.util.concurrent.Futures;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.util.RequestScopePropagator;

import java.util.concurrent.Future;

/**
 * Helper for (re)indexing a change document.
 * <p>
 * Indexing is run in the background, as it may require substantial work to
 * compute some of the fields and/or update the index.
 */
public interface ChangeIndexer {
  /** Instance indicating secondary index is disabled. */
  public static final ChangeIndexer DISABLED = new ChangeIndexer() {
    @Override
    public Future<?> index(Change change) {
      return Futures.immediateFuture(null);
    }

    @Override
    public Future<?> index(Change change, RequestScopePropagator prop) {
      return Futures.immediateFuture(null);
    }
  };

  /**
   * Start indexing a change.
   *
   * @param change change to index.
   */
  public Future<?> index(Change change);

  /**
   * Start indexing a change.
   *
   * @param change change to index.
   * @param prop propagator to wrap any created runnables in.
   */
  public Future<?> index(Change change, RequestScopePropagator prop);
}
