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

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;

import java.util.concurrent.Callable;

/**
 * Helper for (re)indexing a change document.
 * <p>
 * Indexing is run in the background, as it may require substantial work to
 * compute some of the fields and/or update the index.
 */
public class ChangeIndexerImpl extends ChangeIndexer {
  private final ChangeIndex index;

  @Inject
  ChangeIndexerImpl(@IndexExecutor ListeningScheduledExecutorService executor,
      ChangeIndex index) {
    super(executor);
    this.index = index;
  }

  @Override
  public Callable<Void> indexTask(ChangeData cd) {
    return new Task(cd);
  }

  private class Task implements Callable<Void> {
    private final ChangeData cd;

    private Task(ChangeData cd) {
      this.cd = cd;
    }

    @Override
    public Void call() throws Exception {
      index.replace(cd);
      return null;
    }

    @Override
    public String toString() {
      return "index-change-" + cd.getId().get();
    }
  }
}
