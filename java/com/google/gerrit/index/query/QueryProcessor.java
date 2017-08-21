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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.IndexRewriter;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.SchemaDefinitions;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.gwtorm.server.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

/**
 * Lower-level implementation for executing a single query over a secondary index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public abstract class QueryProcessor<T> {
  protected static class Metrics {
    final Timer1<String> executionTime;

    Metrics(MetricMaker metricMaker) {
      Field<String> index = Field.ofString("index", "index name");
      executionTime =
          metricMaker.newTimer(
              "query/query_latency",
              new Description("Successful query latency, accumulated over the life of the process")
                  .setCumulative()
                  .setUnit(Description.Units.MILLISECONDS),
              index);
    }
  }

  private final Metrics metrics;
  private final SchemaDefinitions<T> schemaDef;
  private final IndexConfig indexConfig;
  private final IndexCollection<?, T, ? extends Index<?, T>> indexes;
  private final IndexRewriter<T> rewriter;
  private final String limitField;
  private final IntSupplier permittedLimit;

  // This class is not generally thread-safe, but programmer error may result in it being shared
  // across threads. At least ensure the bit for checking if it's been used is threadsafe.
  private final AtomicBoolean used;

  protected int start;

  private boolean enforceVisibility = true;
  private int userProvidedLimit;
  private Set<String> requestedFields;

  protected QueryProcessor(
      MetricMaker metricMaker,
      SchemaDefinitions<T> schemaDef,
      IndexConfig indexConfig,
      IndexCollection<?, T, ? extends Index<?, T>> indexes,
      IndexRewriter<T> rewriter,
      String limitField,
      IntSupplier permittedLimit) {
    this.metrics = new Metrics(metricMaker);
    this.schemaDef = schemaDef;
    this.indexConfig = indexConfig;
    this.indexes = indexes;
    this.rewriter = rewriter;
    this.limitField = limitField;
    this.permittedLimit = permittedLimit;
    this.used = new AtomicBoolean(false);
  }

  public QueryProcessor<T> setStart(int n) {
    start = n;
    return this;
  }

  /**
   * Specify whether to enforce visibility by filtering out results that are not visible to the
   * user.
   *
   * <p>Enforcing visibility may have performance consequences, as the index system may need to
   * post-filter a large number of results to fill even a modest limit.
   *
   * <p>If visibility is enforced, the user's {@code queryLimit} global capability is also used to
   * bound the total number of results. If this capability is non-positive, this results in the
   * entire query processor being {@link #isDisabled() disabled}.
   *
   * @param enforce whether to enforce visibility.
   * @return this.
   */
  public QueryProcessor<T> enforceVisibility(boolean enforce) {
    enforceVisibility = enforce;
    return this;
  }

  /**
   * Set an end-user-provided limit on the number of results returned.
   *
   * <p>Since this limit is provided by an end user, it may exceed the limit that they are
   * authorized to use. This is allowed; the processor will take multiple possible limits into
   * account and choose the one that makes the most sense.
   *
   * @param n limit; zero or negative means no limit.
   * @return this.
   */
  public QueryProcessor<T> setUserProvidedLimit(int n) {
    userProvidedLimit = n;
    return this;
  }

  public QueryProcessor<T> setRequestedFields(Set<String> fields) {
    requestedFields = fields;
    return this;
  }

  /**
   * Query for entities that match a structured query.
   *
   * @see #query(List)
   * @param query the query.
   * @return results of the query.
   */
  public QueryResult<T> query(Predicate<T> query) throws OrmException, QueryParseException {
    return query(ImmutableList.of(query)).get(0);
  }

  /**
   * Perform multiple queries in parallel.
   *
   * <p>If querying is disabled, short-circuits the index and returns empty results. Callers that
   * wish to distinguish this case from a query returning no results from the index may call {@link
   * #isDisabled()} themselves.
   *
   * @param queries list of queries.
   * @return results of the queries, one QueryResult per input query, in the same order as the
   *     input.
   */
  public List<QueryResult<T>> query(List<Predicate<T>> queries)
      throws OrmException, QueryParseException {
    try {
      return query(null, queries);
    } catch (OrmRuntimeException e) {
      throw new OrmException(e.getMessage(), e);
    } catch (OrmException e) {
      if (e.getCause() != null) {
        Throwables.throwIfInstanceOf(e.getCause(), QueryParseException.class);
      }
      throw e;
    }
  }

  private List<QueryResult<T>> query(
      @Nullable List<String> queryStrings, List<Predicate<T>> queries)
      throws OrmException, QueryParseException {
    long startNanos = System.nanoTime();
    checkState(!used.getAndSet(true), "%s has already been used", getClass().getSimpleName());
    int cnt = queries.size();
    if (queryStrings != null) {
      int qs = queryStrings.size();
      checkArgument(qs == cnt, "got %s query strings but %s predicates", qs, cnt);
    }
    if (cnt == 0) {
      return ImmutableList.of();
    }
    if (isDisabled()) {
      return disabledResults(queryStrings, queries);
    }

    // Parse and rewrite all queries.
    List<Integer> limits = new ArrayList<>(cnt);
    List<Predicate<T>> predicates = new ArrayList<>(cnt);
    List<DataSource<T>> sources = new ArrayList<>(cnt);
    for (Predicate<T> q : queries) {
      int limit = getEffectiveLimit(q);
      limits.add(limit);

      if (limit == getBackendSupportedLimit()) {
        limit--;
      }

      int page = (start / limit) + 1;
      if (page > indexConfig.maxPages()) {
        throw new QueryParseException(
            "Cannot go beyond page " + indexConfig.maxPages() + " of results");
      }

      // Always bump limit by 1, even if this results in exceeding the permitted
      // max for this user. The only way to see if there are more entities is to
      // ask for one more result from the query.
      QueryOptions opts = createOptions(indexConfig, start, limit + 1, getRequestedFields());
      Predicate<T> pred = rewriter.rewrite(q, opts);
      if (enforceVisibility) {
        pred = enforceVisibility(pred);
      }
      predicates.add(pred);

      @SuppressWarnings("unchecked")
      DataSource<T> s = (DataSource<T>) pred;
      sources.add(s);
    }

    // Run each query asynchronously, if supported.
    List<ResultSet<T>> matches = new ArrayList<>(cnt);
    for (DataSource<T> s : sources) {
      matches.add(s.read());
    }

    List<QueryResult<T>> out = new ArrayList<>(cnt);
    for (int i = 0; i < cnt; i++) {
      out.add(
          QueryResult.create(
              queryStrings != null ? queryStrings.get(i) : null,
              predicates.get(i),
              limits.get(i),
              matches.get(i).toList()));
    }

    // Only measure successful queries that actually touched the index.
    metrics.executionTime.record(
        schemaDef.getName(), System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    return out;
  }

  private static <T> ImmutableList<QueryResult<T>> disabledResults(
      List<String> queryStrings, List<Predicate<T>> queries) {
    return IntStream.range(0, queries.size())
        .mapToObj(
            i ->
                QueryResult.create(
                    queryStrings != null ? queryStrings.get(i) : null,
                    queries.get(i),
                    0,
                    ImmutableList.of()))
        .collect(toImmutableList());
  }

  protected QueryOptions createOptions(
      IndexConfig indexConfig, int start, int limit, Set<String> requestedFields) {
    return QueryOptions.create(indexConfig, start, limit, requestedFields);
  }

  /**
   * Invoked after the query was rewritten. Subclasses must overwrite this method to filter out
   * results that are not visible to the calling user.
   *
   * @param pred the query
   * @return the modified query
   */
  protected abstract Predicate<T> enforceVisibility(Predicate<T> pred);

  private Set<String> getRequestedFields() {
    if (requestedFields != null) {
      return requestedFields;
    }
    Index<?, T> index = indexes.getSearchIndex();
    return index != null ? index.getSchema().getStoredFields().keySet() : ImmutableSet.<String>of();
  }

  /**
   * Check whether querying should be disabled.
   *
   * <p>Currently, the only condition that can disable the whole query processor is if both {@link
   * #enforceVisibility(boolean) visibility is enforced} and the user has a non-positive maximum
   * value for the {@code queryLimit} capability.
   *
   * <p>If querying is disabled, all calls to {@link #query(Predicate)} and {@link #query(List)}
   * will return empty results. This method can be used if callers wish to distinguish this case
   * from a query returning no results from the index.
   *
   * @return true if querying should be disabled.
   */
  public boolean isDisabled() {
    return enforceVisibility && getPermittedLimit() <= 0;
  }

  private int getPermittedLimit() {
    return enforceVisibility ? permittedLimit.getAsInt() : Integer.MAX_VALUE;
  }

  private int getBackendSupportedLimit() {
    return indexConfig.maxLimit();
  }

  private int getEffectiveLimit(Predicate<T> p) {
    List<Integer> possibleLimits = new ArrayList<>(4);
    possibleLimits.add(getBackendSupportedLimit());
    possibleLimits.add(getPermittedLimit());
    if (userProvidedLimit > 0) {
      possibleLimits.add(userProvidedLimit);
    }
    if (limitField != null) {
      Integer limitFromPredicate = LimitPredicate.getLimit(limitField, p);
      if (limitFromPredicate != null) {
        possibleLimits.add(limitFromPredicate);
      }
    }
    int result = Ordering.natural().min(possibleLimits);
    // Should have short-circuited from #query or thrown some other exception before getting here.
    checkState(result > 0, "effective limit should be positive");
    return result;
  }
}
