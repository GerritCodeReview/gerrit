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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Dynamic pointers to the index versions used for searching and writing. */
@Singleton
public class IndexCollection implements LifecycleListener {
  private final CopyOnWriteArrayList<ChangeIndex> writeIndexes;
  private final AtomicReference<ChangeIndex> searchIndex;

  @Inject
  @VisibleForTesting
  public IndexCollection(MetricMaker metrics) {
    this.writeIndexes = Lists.newCopyOnWriteArrayList();
    this.searchIndex = new AtomicReference<>();

    export(metrics);
  }

  private void export(MetricMaker metrics) {
    metrics.newCallbackMetric(
        "change/index/search_version",
        Integer.class,
        new Description("Version of the index read by searches.")
          .setGauge(),
        new Supplier<Integer>() {
          @Override
          public Integer get() {
            ChangeIndex idx = getSearchIndex();
            return idx != null ? idx.getSchema().getVersion() : 0;
          }
        });

    metrics.newCallbackMetric(
        "change/index/write_versions",
        Integer.class,
        new Description("Number of index versions being written to.")
          .setGauge(),
        new Supplier<Integer>() {
          @Override
          public Integer get() {
            return writeIndexes.size();
          }
        });
  }

  /** @return the current search index version. */
  public ChangeIndex getSearchIndex() {
    return searchIndex.get();
  }

  public void setSearchIndex(ChangeIndex index) {
    ChangeIndex old = searchIndex.getAndSet(index);
    if (old != null && old != index && !writeIndexes.contains(old)) {
      old.close();
    }
  }

  public Collection<ChangeIndex> getWriteIndexes() {
    return Collections.unmodifiableCollection(writeIndexes);
  }

  public synchronized ChangeIndex addWriteIndex(ChangeIndex index) {
    int version = index.getSchema().getVersion();
    for (int i = 0; i < writeIndexes.size(); i++) {
      if (writeIndexes.get(i).getSchema().getVersion() == version) {
        return writeIndexes.set(i, index);
      }
    }
    writeIndexes.add(index);
    return null;
  }

  public synchronized void removeWriteIndex(int version) {
    int removeIndex = -1;
    for (int i = 0; i < writeIndexes.size(); i++) {
      if (writeIndexes.get(i).getSchema().getVersion() == version) {
        removeIndex = i;
        break;
      }
    }
    if (removeIndex >= 0) {
      try {
        writeIndexes.get(removeIndex).close();
      } finally {
        writeIndexes.remove(removeIndex);
      }
    }
  }

  public ChangeIndex getWriteIndex(int version) {
    for (ChangeIndex i : writeIndexes) {
      if (i.getSchema().getVersion() == version) {
        return i;
      }
    }
    return null;
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
