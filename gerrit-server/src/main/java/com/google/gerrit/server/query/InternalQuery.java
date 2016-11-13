// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.query;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.Schema;
import com.google.gwtorm.server.OrmException;
import java.util.List;
import java.util.Set;

/**
 * Execute a single query over a secondary index, for use by Gerrit internals.
 *
 * <p>By default, visibility of returned entities is not enforced (unlike in {@link
 * QueryProcessor}). The methods in this class are not typically used by user-facing paths, but
 * rather by internal callers that need to process all matching results.
 */
public class InternalQuery<T> {
  private final QueryProcessor<T> queryProcessor;
  private final IndexCollection<?, T, ? extends Index<?, T>> indexes;

  protected final IndexConfig indexConfig;

  protected InternalQuery(
      QueryProcessor<T> queryProcessor,
      IndexCollection<?, T, ? extends Index<?, T>> indexes,
      IndexConfig indexConfig) {
    this.queryProcessor = queryProcessor.enforceVisibility(false);
    this.indexes = indexes;
    this.indexConfig = indexConfig;
  }

  public InternalQuery<T> setLimit(int n) {
    queryProcessor.setLimit(n);
    return this;
  }

  public InternalQuery<T> enforceVisibility(boolean enforce) {
    queryProcessor.enforceVisibility(enforce);
    return this;
  }

  public InternalQuery<T> setRequestedFields(Set<String> fields) {
    queryProcessor.setRequestedFields(fields);
    return this;
  }

  public InternalQuery<T> noFields() {
    queryProcessor.setRequestedFields(ImmutableSet.<String>of());
    return this;
  }

  public List<T> query(Predicate<T> p) throws OrmException {
    try {
      return queryProcessor.query(p).entities();
    } catch (QueryParseException e) {
      throw new OrmException(e);
    }
  }

  /**
   * Run multiple queries in parallel.
   *
   * <p>If a limit was specified using {@link #setLimit(int)}, that limit is applied to each query
   * independently.
   *
   * @param queries list of queries.
   * @return results of the queries, one list of results per input query, in the same order as the
   *     input.
   */
  public List<List<T>> query(List<Predicate<T>> queries) throws OrmException {
    try {
      return Lists.transform(queryProcessor.query(queries), QueryResult::entities);
    } catch (QueryParseException e) {
      throw new OrmException(e);
    }
  }

  protected Schema<T> schema() {
    Index<?, T> index = indexes != null ? indexes.getSearchIndex() : null;
    return index != null ? index.getSchema() : null;
  }
}
