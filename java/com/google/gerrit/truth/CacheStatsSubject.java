// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.truth;

import static com.google.common.truth.Truth.assertAbout;
import static java.util.Objects.requireNonNull;

import com.google.common.cache.CacheStats;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;

@UsedAt(Project.PLUGINS_ALL)
public class CacheStatsSubject extends Subject {
  public static CacheStatsSubject assertThat(CacheStats stats) {
    return assertAbout(CacheStatsSubject::new).that(stats);
  }

  public static CacheStats cloneStats(CacheStats other) {
    return new CacheStats(
        other.hitCount(),
        other.missCount(),
        other.loadSuccessCount(),
        other.loadExceptionCount(),
        other.totalLoadTime(),
        other.evictionCount());
  }

  private final CacheStats stats;
  private CacheStats start = new CacheStats(0, 0, 0, 0, 0, 0);

  private CacheStatsSubject(FailureMetadata failureMetadata, CacheStats stats) {
    super(failureMetadata, stats);
    this.stats = stats;
  }

  public CacheStatsSubject since(CacheStats start) {
    this.start = requireNonNull(start);
    return this;
  }

  public void hasHitCount(int expectedHitCount) {
    isNotNull();
    check("hitCount()").that(stats.minus(start).hitCount()).isEqualTo(expectedHitCount);
  }

  public void hasMissCount(int expectedMissCount) {
    isNotNull();
    check("missCount()").that(stats.minus(start).missCount()).isEqualTo(expectedMissCount);
  }
}
