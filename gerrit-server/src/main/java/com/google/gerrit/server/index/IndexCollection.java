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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/** Dynamic pointers to the index versions used for searching and writing. */
@Singleton
public class IndexCollection implements LifecycleListener {
  private final CopyOnWriteArrayList<ChangeIndex> writeIndexes;
  private final AtomicReference<ChangeIndex> searchIndex;

  @Inject
  @VisibleForTesting
  public IndexCollection() {
    this.writeIndexes = Lists.newCopyOnWriteArrayList();
    this.searchIndex = new AtomicReference<ChangeIndex>();
  }

  /**
   * @return the current search index version, or null if the secondary index is
   *     disabled.
   */
  @Nullable
  public ChangeIndex getSearchIndex() {
    return searchIndex.get();
  }

  public void setSearchIndex(ChangeIndex index) {
    searchIndex.set(index);
  }

  public Collection<ChangeIndex> getWriteIndexes() {
    return Collections.unmodifiableCollection(writeIndexes);
  }

  public synchronized void addWriteIndex(ChangeIndex index) {
    int version = index.getSchema().getVersion();
    for (ChangeIndex i : writeIndexes) {
      if (i.getSchema().getVersion() == version) {
        throw new IllegalArgumentException(
            "Write index version " + version + " already in list");
      }
    }
    writeIndexes.add(index);
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
    ChangeIndex read = searchIndex.get();
    if (read != null) {
      read.close();
    }
    for (ChangeIndex write : writeIndexes) {
      if (write != read) {
        write.close();
      }
    }
  }
}
