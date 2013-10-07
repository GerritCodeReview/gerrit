// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;

import java.io.IOException;

/**
 * Secondary index implementation for change documents.
 * <p>
 * {@link ChangeData} objects are inserted into the index and are queried by
 * converting special {@link com.google.gerrit.server.query.Predicate} instances
 * into index-aware predicates that use the index search results as a source.
 * <p>
 * Implementations must be thread-safe and should batch inserts/updates where
 * appropriate.
 */
public interface ChangeIndex {
  /** Instance indicating secondary index is disabled. */
  public static final ChangeIndex DISABLED = new ChangeIndex() {
    @Override
    public Schema<ChangeData> getSchema() {
      return null;
    }

    @Override
    public void insert(ChangeData cd) throws IOException {
      // Do nothing.
    }

    @Override
    public void replace(ChangeData cd) throws IOException {
      // Do nothing.
    }

    @Override
    public void delete(ChangeData cd) throws IOException {
      // Do nothing.
    }

    @Override
    public void deleteAll() throws IOException {
      // Do nothing.
    }

    @Override
    public ChangeDataSource getSource(Predicate<ChangeData> p, int limit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
      // Do nothing.
    }

    @Override
    public void markReady(boolean ready) {
      throw new UnsupportedOperationException();
    }
  };

  /** @return the schema version used by this index. */
  public Schema<ChangeData> getSchema();

  /** Close this index. */
  public void close();

  /**
   * Insert a change document into the index.
   * <p>
   * Results may not be immediately visible to searchers, but should be visible
   * within a reasonable amount of time.
   *
   * @param cd change document
   *
   * @throws IOException if the change could not be inserted.
   */
  public void insert(ChangeData cd) throws IOException;

  /**
   * Update a change document in the index.
   * <p>
   * Semantically equivalent to deleting the document and reinserting it with
   * new field values. Results may not be immediately visible to searchers, but
   * should be visible within a reasonable amount of time.
   *
   * @param cd change document
   *
   * @throws IOException
   */
  public void replace(ChangeData cd) throws IOException;

  /**
   * Delete a change document from the index.
   *
   * @param cd change document.
   *
   * @throws IOException
   */
  public void delete(ChangeData cd) throws IOException;

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
  public ChangeDataSource getSource(Predicate<ChangeData> p, int limit)
      throws QueryParseException;

  /**
   * Mark whether this index is up-to-date and ready to serve reads.
   *
   * @param ready whether the index is ready
   * @throws IOException
   */
  public void markReady(boolean ready) throws IOException;
}
