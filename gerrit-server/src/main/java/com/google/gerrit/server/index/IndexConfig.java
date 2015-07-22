// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;

import org.eclipse.jgit.lib.Config;

/**
 * Implementation-specific configuration for secondary indexes.
 * <p>
 * Contains configuration that is tied to a specific index implementation but is
 * otherwise global, i.e. not tied to a specific {@link ChangeIndex} and schema
 * version.
 */
@AutoValue
public abstract class IndexConfig {
  private static final int DEFAULT_MAX_PREFIX_TERMS = 100;

  public static IndexConfig createDefault() {
    return create(0, 0, DEFAULT_MAX_PREFIX_TERMS);
  }

  public static IndexConfig fromConfig(Config cfg) {
    return create(
        cfg.getInt("index", null, "maxLimit", 0),
        cfg.getInt("index", null, "maxPages", 0),
        cfg.getInt("index", null, "maxPrefixTerms", DEFAULT_MAX_PREFIX_TERMS));
  }

  public static IndexConfig create(int maxLimit, int maxPages,
      int maxPrefixTerms) {
    return new AutoValue_IndexConfig(
        checkLimit(maxLimit, "maxLimit", Integer.MAX_VALUE),
        checkLimit(maxPages, "maxPages", Integer.MAX_VALUE),
        checkLimit(maxPrefixTerms, "maxPrefixTerms", DEFAULT_MAX_PREFIX_TERMS));
  }

  private static int checkLimit(int limit, String name, int defaultValue) {
    if (limit == 0) {
      return defaultValue;
    }
    checkArgument(limit > 0, "%s must be positive: %s", name, limit);
    return limit;
  }

  /**
   * @return maximum limit supported by the underlying index, or limited for
   * performance reasons.
   */
  public abstract int maxLimit();

  /**
   * @return maximum number of pages (limit / start) supported by the
   *     underlying index, or limited for performance reasons.
   */
  public abstract int maxPages();

  /**
   * @return maximum number of prefix terms per query supported by the
   *     underlying index, or limited for performance reasons. Not enforced for
   *     general queries; only for specific cases where the query system can
   *     split into equivalent subqueries.
   */
  public abstract int maxPrefixTerms();
}
