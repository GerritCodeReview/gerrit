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
import java.util.concurrent.atomic.AtomicLong;

public class ConcurrentBloomFilter<K> {
  private final Funnel<K> funnel;
  private final Runnable builder;
  private final AtomicLong invalidatedCount = new AtomicLong();
  private final int maxInvalidated;

  private volatile int estimatedSize;
  private volatile BloomFilter<K> buildingBloomFilter;
  private volatile BloomFilter<K> bloomFilter;

  public ConcurrentBloomFilter(Funnel<K> funnel, Runnable builder, int maxInvalidated) {
    this.funnel = funnel;
    this.builder = builder;
    this.maxInvalidated = maxInvalidated;
  }

  public int getEstimatedSize() {
    return estimatedSize;
  }

  public void setEstimatedSize(int estimatedSize) {
    this.estimatedSize = estimatedSize;
  }

  public synchronized void initIfNeeded() {
    if (bloomFilter == null) {
      startBuild();
    }
  }

  public long getInvalidatedCount() {
    return invalidatedCount.get();
  }

  public void startBuildIfNeeded() {
    if (maxInvalidated > 0 && invalidatedCount.get() >= (estimatedSize * maxInvalidated) / 100) {
      startBuild();
    }
  }

  public boolean mightContain(K key) {
    BloomFilter<K> b = bloomFilter;
    return b == null || b.mightContain(key);
  }

  private synchronized void startBuild() {
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
    invalidatedCount.set(0);
  }

  public void put(K key) {
    boolean referencesChanged;
    do {
      BloomFilter<K> b = putIfFilterNotNull(bloomFilter, key);
      BloomFilter<K> bb = putIfFilterNotNull(buildingBloomFilter, key);
      // Was there a concurrent update by another thread?
      referencesChanged =
          !suppressReferenceEqualityWarning(b, bloomFilter)
              || !suppressReferenceEqualityWarning(bb, buildingBloomFilter);
    } while (referencesChanged);
  }

  public void invalidate(K key) {
    invalidatedCount.incrementAndGet();
  }

  public void clear() {
    bloomFilter = newBloomFilter();
  }

  private BloomFilter<K> newBloomFilter() {
    invalidatedCount.set(0);
    int cnt = Math.max(64 * 1024, 2 * estimatedSize);
    return BloomFilter.create(funnel, cnt);
  }

  private static <K> BloomFilter<K> putIfFilterNotNull(BloomFilter<K> b, K key) {
    if (b != null) {
      b.put(key);
    }
    return b;
  }

  @SuppressWarnings("ReferenceEquality")
  private static <T> boolean suppressReferenceEqualityWarning(T a, T b) {
    return a == b;
  }
}
