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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import java.util.List;

/** Results of a query over entities. */
@AutoValue
public abstract class QueryResult<T> {
  public static <T> QueryResult<T> create(
      @Nullable String query, Predicate<T> predicate, int limit, List<T> entities) {
    boolean more;
    if (entities.size() > limit) {
      more = true;
      entities = entities.subList(0, limit);
    } else {
      more = false;
    }
    return new AutoValue_QueryResult<>(query, predicate, ImmutableList.copyOf(entities), more);
  }

  /** @return the original query string, or null if the query was created programmatically. */
  @Nullable
  public abstract String query();

  /** @return the predicate after all rewriting and other modification by the query subsystem. */
  public abstract Predicate<T> predicate();

  /** @return the query results. */
  public abstract ImmutableList<T> entities();

  /**
   * @return whether the query could be retried with a higher start/limit to produce more results.
   *     Never true if {@link #entities()} is empty.
   */
  public abstract boolean more();
}
