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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.events.LifecycleListener;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Dynamic pointers to the index versions used for searching and writing. */
public abstract class IndexCollection<K, V, I extends Index<K, V>> implements LifecycleListener {
  private final CopyOnWriteArrayList<I> writeIndexes;
  private final AtomicReference<I> searchIndex;

  protected IndexCollection() {
    this.writeIndexes = Lists.newCopyOnWriteArrayList();
    this.searchIndex = new AtomicReference<>();
  }

  /** @return the current search index version. */
  public I getSearchIndex() {
    return searchIndex.get();
  }

  public void setSearchIndex(I index) {
    I old = searchIndex.getAndSet(index);
    if (old != null && old != index && !writeIndexes.contains(old)) {
      old.close();
    }
  }

  public Collection<I> getWriteIndexes() {
    return Collections.unmodifiableCollection(writeIndexes);
  }

  public synchronized I addWriteIndex(I index) {
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

  public I getWriteIndex(int version) {
    for (I i : writeIndexes) {
      if (i.getSchema().getVersion() == version) {
        return i;
      }
    }
    return null;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    I read = searchIndex.get();
    if (read != null) {
      read.close();
    }
    for (I write : writeIndexes) {
      if (write != read) {
        write.close();
      }
    }
  }
}
