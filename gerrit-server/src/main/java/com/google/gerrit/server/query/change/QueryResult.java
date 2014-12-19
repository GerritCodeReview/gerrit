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

package com.google.gerrit.server.query.change;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.query.Predicate;

import java.util.List;

/** Results of a query over changes. */
@AutoValue
public abstract class QueryResult {
  static QueryResult create(@Nullable String query,
      Predicate<ChangeData> predicate, int limit, List<ChangeData> changes) {
    boolean moreChanges;
    if (changes.size() > limit) {
      moreChanges = true;
      changes = changes.subList(0, limit);
    } else {
      moreChanges = false;
    }
    return new AutoValue_QueryResult(query, predicate, changes, moreChanges);
  }

  /**
   * @return the original query string, or null if the query was created
   *     programmatically.
   */
  @Nullable public abstract String query();

  /**
   * @return the predicate after all rewriting and other modification by the
   *     query subsystem.
   */
  public abstract Predicate<ChangeData> predicate();

  /** @return the query results. */
  public abstract List<ChangeData> changes();

  /**
   * @return whether the query could be retried with
   *     {@link QueryProcessor#setStart(int)} to produce more results. Never
   *     true if {@link #changes()} is empty.
   */
  public abstract boolean moreChanges();
}
