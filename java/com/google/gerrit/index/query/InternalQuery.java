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

package com.google.gerrit.index.query;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.Schema;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Execute a single query over a secondary index, for use by Gerrit internals.
 *
 * <p>By default, visibility of returned entities is not enforced (unlike in {@link
 * QueryProcessor}). The methods in this class are not typically used by user-facing paths, but
 * rather by internal callers that need to process all matching results.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class InternalQuery<T, Q extends InternalQuery<T, Q>> {
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

  @SuppressWarnings("unchecked")
  protected final Q self() {
    return (Q) this;
  }

  final Q setStart(int start) {
    queryProcessor.setStart(start);
    return self();
  }

  public final Q setLimit(int n) {
    queryProcessor.setUserProvidedLimit(n);
    return self();
  }

  public final Q enforceVisibility(boolean enforce) {
    queryProcessor.enforceVisibility(enforce);
    return self();
  }

  @SafeVarargs
  public final Q setRequestedFields(FieldDef<T, ?>... fields) {
    checkArgument(fields.length > 0, "requested field list is empty");
    queryProcessor.setRequestedFields(
        Arrays.stream(fields).map(FieldDef::getName).collect(toSet()));
    return self();
  }

  public final Q noFields() {
    queryProcessor.setRequestedFields(ImmutableSet.of());
    return self();
  }

  public final List<T> query(Predicate<T> p) {
    return queryResults(p).entities();
  }

  final QueryResult<T> queryResults(Predicate<T> p) {
    try {
      return queryProcessor.query(p);
    } catch (QueryParseException e) {
      throw new StorageException(e);
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
  public final List<List<T>> query(List<Predicate<T>> queries) {
    try {
      return Lists.transform(queryProcessor.query(queries), QueryResult::entities);
    } catch (QueryParseException e) {
      throw new StorageException(e);
    }
  }

  protected final Schema<T> schema() {
    Index<?, T> index = indexes != null ? indexes.getSearchIndex() : null;
    return index != null ? index.getSchema() : null;
  }

  /**
   * Query a predicate repeatedly until all results are exhausted.
   *
   * <p>Capable of iterating through all results regardless of limits. The passed {@code
   * querySupplier} may choose to pre-set limits or not; this only affects the number of queries
   * that may be issued, not the size of the final results.
   *
   * <p>Since multiple queries may be issued, this method is subject to races when the result set
   * changes mid-iteration. This may result in skipped results, if an entity gets modified to jump
   * to the front of the list after this method has passed it. It may also result in duplicate
   * results, if an entity at the end of one batch of results gets pushed back further, putting it
   * at the beginning of the next batch. This race cannot be avoided unless we change the underlying
   * index interface to support true continuation tokens.
   *
   * @param querySupplier supplier for queries. Callers will generally pass a lambda that invokes an
   *     underlying {@code Provider<InternalFooQuery>}, since the instances are not reusable. The
   *     lambda may also call additional methods on the newly-created query, such as {@link
   *     #enforceVisibility(boolean)}.
   * @param predicate predicate to search for.
   * @param <T> result type.
   * @return exhaustive list of results, subject to the race condition described above.
   */
  protected static <T> ImmutableList<T> queryExhaustively(
      Supplier<? extends InternalQuery<T, ?>> querySupplier, Predicate<T> predicate) {
    ImmutableList.Builder<T> b = null;
    int start = 0;
    while (true) {
      QueryResult<T> qr = querySupplier.get().setStart(start).queryResults(predicate);
      if (b == null) {
        if (!qr.more()) {
          return qr.entities();
        }
        b = ImmutableList.builder();
      }
      b.addAll(qr.entities());
      if (!qr.more()) {
        return b.build();
      }
      start += qr.entities().size();
    }
  }
}
