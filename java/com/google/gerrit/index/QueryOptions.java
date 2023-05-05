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

package com.google.gerrit.index;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import java.util.Set;
import java.util.function.Function;

@AutoValue
public abstract class QueryOptions {
  public static QueryOptions create(IndexConfig config, int start, int limit, Set<String> fields) {
    return create(config, start, null, limit, config.pageSizeMultiplier(), limit, fields);
  }

  public static QueryOptions create(
      IndexConfig config, int start, int pageSize, int limit, Set<String> fields) {
    return create(config, start, null, pageSize, config.pageSizeMultiplier(), limit, fields);
  }

  public static QueryOptions create(
      IndexConfig config,
      int start,
      int pageSize,
      int pageSizeMultiplier,
      int limit,
      Set<String> fields) {
    return create(config, start, null, pageSize, pageSizeMultiplier, limit, fields);
  }

  public static QueryOptions create(
      IndexConfig config,
      int start,
      Object searchAfter,
      int pageSize,
      int pageSizeMultiplier,
      int limit,
      Set<String> fields) {
    checkArgument(start >= 0, "start must be nonnegative: %s", start);
    checkArgument(limit > 0, "limit must be positive: %s", limit);
    if (searchAfter != null) {
      checkArgument(start == 0, "start must be 0 when searchAfter is specified: %s", start);
    }
    return new AutoValue_QueryOptions(
        config,
        start,
        searchAfter,
        pageSize,
        pageSizeMultiplier,
        limit,
        ImmutableSet.copyOf(fields));
  }

  public QueryOptions convertForBackend() {
    // Increase the limit rather than skipping, since we don't know how many
    // skipped results would have been filtered out by the enclosing AndSource.
    int backendLimit = config().maxLimit();
    int limit = Ints.saturatedCast((long) limit() + start());
    limit = Math.min(limit, backendLimit);
    int pageSize =
        Math.min(
            Math.min(Ints.saturatedCast((long) pageSize() + start()), config().maxPageSize()),
            backendLimit);
    return create(config(), 0, null, pageSize, pageSizeMultiplier(), limit, fields());
  }

  public abstract IndexConfig config();

  public abstract int start();

  @Nullable
  public abstract Object searchAfter();

  public abstract int pageSize();

  public abstract int pageSizeMultiplier();

  public abstract int limit();

  public abstract ImmutableSet<String> fields();

  public QueryOptions withPageSize(int pageSize) {
    return create(
        config(), start(), searchAfter(), pageSize, pageSizeMultiplier(), limit(), fields());
  }

  public QueryOptions withLimit(int newLimit) {
    return create(
        config(), start(), searchAfter(), pageSize(), pageSizeMultiplier(), newLimit, fields());
  }

  public QueryOptions withStart(int newStart) {
    return create(
        config(), newStart, searchAfter(), pageSize(), pageSizeMultiplier(), limit(), fields());
  }

  public QueryOptions withSearchAfter(Object newSearchAfter) {
    // Index search-after APIs don't use 'start', so set it to 0 to be safe. ElasticSearch for
    // example, expects it to be 0 when using search-after APIs.
    return create(
            config(), start(), newSearchAfter, pageSize(), pageSizeMultiplier(), limit(), fields())
        .withStart(0);
  }

  public QueryOptions filterFields(Function<QueryOptions, Set<String>> filter) {
    return create(
        config(),
        start(),
        searchAfter(),
        pageSize(),
        pageSizeMultiplier(),
        limit(),
        filter.apply(this));
  }
}
