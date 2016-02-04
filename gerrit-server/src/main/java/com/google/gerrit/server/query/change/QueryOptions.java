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

package com.google.gerrit.server.query.change;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.index.ChangeField.CHANGE;
import static com.google.gerrit.server.index.ChangeField.PROJECT;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.index.IndexConfig;

import java.util.HashSet;
import java.util.Set;

@AutoValue
public abstract class QueryOptions {
  public static QueryOptions create(IndexConfig config, int start, int limit,
      Set<String> fields) {
    checkArgument(start >= 0, "start must be nonnegative: %s", start);
    checkArgument(limit > 0, "limit must be positive: %s", limit);

    // Always include project since it is needed to load the change from notedb.
    if (!fields.contains(CHANGE.getName())
        && !fields.contains(PROJECT.getName())) {
      fields = new HashSet<>(fields);
      fields.add(PROJECT.getName());
    }

    return new AutoValue_QueryOptions(config, start, limit,
        ImmutableSet.copyOf(fields));
  }

  public static QueryOptions oneResult() {
    return create(IndexConfig.createDefault(), 0, 1,
        ImmutableSet.<String> of());
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
