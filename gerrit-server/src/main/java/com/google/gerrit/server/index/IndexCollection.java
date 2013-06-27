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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/** Dynamic pointer to the index version used for searching. */
@Singleton
public class IndexCollection implements LifecycleListener {
  private final ConcurrentMap<Integer, ChangeIndex> writeIndexes;
  private final AtomicReference<ChangeIndex> ref;

  @Inject
  @VisibleForTesting
  public IndexCollection() {
    this.writeIndexes = Maps.newConcurrentMap();
    this.ref = new AtomicReference<ChangeIndex>();
  }

  public ChangeIndex getSearchIndex() {
    return ref.get();
  }

  public void setSearchIndex(ChangeIndex index) {
    ref.set(index);
  }

  public Collection<ChangeIndex> getWriteIndexes() {
    return Collections.unmodifiableCollection(writeIndexes.values());
  }

  public void addWriteIndex(ChangeIndex index) {
    int version = index.getSchema().getVersion();
    checkState(writeIndexes.putIfAbsent(version, index) == null,
        "Write index version %s already in map", version);
  }

  public void removeWriteIndex(int version) {
    writeIndexes.remove(version);
  }

  public ChangeIndex getWriteIndex(int version) {
    return writeIndexes.get(version);
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
    ChangeIndex read = ref.get();
    if (read != null) {
      read.close();
    }
    for (ChangeIndex write : writeIndexes.values()) {
      if (write != read) {
        read.close();
      }
    }
  }
}
