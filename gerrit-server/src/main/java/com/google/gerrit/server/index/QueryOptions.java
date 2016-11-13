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
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import java.util.Set;

@AutoValue
public abstract class QueryOptions {
  public static QueryOptions create(IndexConfig config, int start, int limit, Set<String> fields) {
    checkArgument(start >= 0, "start must be nonnegative: %s", start);
    checkArgument(limit > 0, "limit must be positive: %s", limit);
    return new AutoValue_QueryOptions(config, start, limit, ImmutableSet.copyOf(fields));
  }

  public QueryOptions convertForBackend() {
    // Increase the limit rather than skipping, since we don't know how many
    // skipped results would have been filtered out by the enclosing AndSource.
    int backendLimit = config().maxLimit();
    int limit = Ints.saturatedCast((long) limit() + start());
    limit = Math.min(limit, backendLimit);
    return create(config(), 0, limit, fields());
  }

  public abstract IndexConfig config();

  public abstract int start();

  public abstract int limit();

  public abstract ImmutableSet<String> fields();

  public QueryOptions withLimit(int newLimit) {
    return create(config(), start(), newLimit, fields());
  }

  public QueryOptions withStart(int newStart) {
    return create(config(), newStart, limit(), fields());
  }
}
