// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.cache;

public interface PersistentCache {

  DiskStats diskStats();

  class DiskStats {
    private final long size;
    private final long space;
    private final long hitCount;
    private final long missCount;

    public DiskStats(long size, long space, long hitCount, long missCount) {
      this.size = size;
      this.space = space;
      this.hitCount = hitCount;
      this.missCount = missCount;
    }

    public long size() {
      return size;
    }

    public long space() {
      return space;
    }

    public long hitCount() {
      return hitCount;
    }

    public long requestCount() {
      return hitCount + missCount;
    }
  }
}
