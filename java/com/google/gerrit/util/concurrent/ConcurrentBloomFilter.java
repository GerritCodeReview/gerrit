// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.util.concurrent;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import java.util.function.Consumer;

public class ConcurrentBloomFilter<K> {
  private final Funnel<K> funnel;
  private final Runnable builder;

  private int estimatedSize;
  private volatile BloomFilter<K> buildingBloomFilter;

  private volatile BloomFilter<K> bloomFilter;

  public ConcurrentBloomFilter(Funnel<K> funnel, Runnable builder) {
    this.funnel = funnel;
    this.builder = builder;
  }

  /** Use only on the builder thread */
  public int getEstimatedSize() {
    return estimatedSize;
  }

  /** Use only on the builder thread */
  public void setEstimatedSize(int estimatedSize) {
    this.estimatedSize = estimatedSize;
  }

  public synchronized void initIfNeeded() {
    if (bloomFilter == null) {
      startBuild();
    }
  }

  public boolean mightContain(K key) {
    BloomFilter<K> b = bloomFilter;
    return b == null || b.mightContain(key);
  }

  private void startBuild() {
    buildingBloomFilter = newBloomFilter();
    builder.run();
  }

  /** Use only on the builder thread */
  public void buildPut(K key) {
    buildingBloomFilter.put(key);
  }

  /** Use only on the builder thread */
  public void build() {
    bloomFilter = buildingBloomFilter;
    buildingBloomFilter = null;
  }

  public void put(K key) {
    boolean isRebuilt;
    do {
      BloomFilter<K> b = putIfFilterNotNull(bloomFilter, key);
      BloomFilter<K> bb = putIfFilterNotNull(buildingBloomFilter, key);
      isRebuilt =
          !referenceEqualsSuppressed(b, bloomFilter)
              || !referenceEqualsSuppressed(bb, buildingBloomFilter);
    } while (isRebuilt);
  }

  public void clear() {
    bloomFilter = newBloomFilter();
  }

  private BloomFilter<K> newBloomFilter() {
    int cnt = Math.max(64 * 1024, 2 * estimatedSize);
    return BloomFilter.create(funnel, cnt);
  }

  private static <K> BloomFilter<K> putIfFilterNotNull(BloomFilter<K> b, K key) {
    acceptIfNotNull(b, bl -> bl.put(key));
    return b;
  }

  private static <T> void acceptIfNotNull(T t, Consumer<T> c) {
    if (t != null) {
      c.accept(t);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private static <T> boolean referenceEqualsSuppressed(T a, T b) {
    return a == b;
  }
}
