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

package com.google.gerrit.index.query;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import java.util.List;

/**
 * Results of a query over entities.
 *
 * @param query Returns the original query string, or null if the query was created
 *     programmatically.
 * @param predicate Returns the predicate after all rewriting and other modification by the query
 *     subsystem.
 * @param entities Returns the query results.
 * @param more Returns whether the query could be retried with a higher start/limit to produce more
 *     results. Never true if {@link #entities()} is empty.
 */
public record QueryResult<T>(
    @Nullable String query, Predicate<T> predicate, ImmutableList<T> entities, boolean more) {
  public QueryResult {
    requireNonNull(predicate, "predicate");
    requireNonNull(entities, "entities");
  }

  public static <T> QueryResult<T> create(
      @Nullable String query, Predicate<T> predicate, int limit, List<T> entities) {
    boolean more;
    if (entities.size() > limit) {
      more = true;
      entities = entities.subList(0, limit);
    } else {
      more = false;
    }
    return new QueryResult<>(query, predicate, ImmutableList.copyOf(entities), more);
  }
}
