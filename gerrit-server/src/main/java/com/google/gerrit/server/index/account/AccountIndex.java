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

package com.google.gerrit.server.index.account;

import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.account.AccountDataSource;

import java.io.IOException;

/**
 * Secondary index implementation for accounts.
 * <p>
 * {@link AccountInfo} objects are inserted into the index and are queried by
 * converting special {@link com.google.gerrit.server.query.Predicate} instances
 * into index-aware predicates that use the index search results as a source.
 * <p>
 * Implementations must be thread-safe and should batch inserts/updates where
 * appropriate.
 */
public interface AccountIndex {
  /** @return the schema version used by this index. */
  public Schema<AccountInfo> getSchema();

  /** Close this index. */
  public void close();

  /**
   * Insert an account document into the index.
   * <p>
   * Results may not be immediately visible to searchers, but should be visible
   * within a reasonable amount of time.
   *
   * @param account account document
   *
   * @throws IOException if the change could not be inserted.
   */
  public void insert(AccountInfo account) throws IOException;

  /**
   * Update an account document in the index.
   * <p>
   * Semantically equivalent to deleting the document and reinserting it with
   * new field values. Results may not be immediately visible to searchers, but
   * should be visible within a reasonable amount of time.
   *
   * @param account account document
   *
   * @throws IOException
   */
  public void replace(AccountInfo account) throws IOException;

  /**
   * Delete an account document from the index.
   *
   * @param account account document
   *
   * @throws IOException
   */
  public void delete(AccountInfo account) throws IOException;

  /**
   * Delete all change documents from the index.
   *
   * @throws IOException
   */
  public void deleteAll() throws IOException;

  /**
   * Convert the given operator predicate into a source searching the index and
   * returning only the documents matching that predicate.
   * <p>
   * This method may be called multiple times for variations on the same
   * predicate or multiple predicate subtrees in the course of processing a
   * single query, so it should not have any side effects (e.g. starting a
   * search in the background).
   *
   * @param p the predicate to match. Must be a tree containing only AND, OR,
   *     or NOT predicates as internal nodes, and {@link IndexPredicate}s as
   *     leaves.
   * @param start offset in results list at which to start returning results.
   * @param limit maximum number of results to return.
   * @return a source of documents matching the predicate. Documents must be
   *     returned in descending sort key order, unless a {@code sortkey_after}
   *     predicate (with a cut point not at {@link Long#MAX_VALUE}) is provided,
   *     in which case the source should return documents in ascending sort key
   *     order starting from the sort key cut point.
   *
   * @throws QueryParseException if the predicate could not be converted to an
   *     indexed data source.
   */
  public AccountDataSource getSource(Predicate<AccountInfo> p, int start,
      int limit) throws QueryParseException;

  /**
   * Mark whether this index is up-to-date and ready to serve reads.
   *
   * @param ready whether the index is ready
   * @throws IOException
   */
  public void markReady(boolean ready) throws IOException;
}
