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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.QueryOptions;

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
  /** @return the schema version used by this index. */
  Schema<ChangeData> getSchema();

  /** Close this index. */
  void close();

  /**
   * Update a change document in the index.
   * <p>
   * Semantically equivalent to deleting the document and reinserting it with
   * new field values. A document that does not already exist is created. Results
   * may not be immediately visible to searchers, but should be visible within a
   * reasonable amount of time.
   *
   * @param cd change document
   *
   * @throws IOException
   */
  void replace(ChangeData cd) throws IOException;

  /**
   * Delete a change document from the index by id.
   *
   * @param id change id
   *
   * @throws IOException
   */
  void delete(Change.Id id) throws IOException;

  /**
   * Delete all change documents from the index.
   *
   * @throws IOException
   */
  void deleteAll() throws IOException;

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
   * @param opts query options not implied by the predicate, such as start and
   *     limit.
   * @return a source of documents matching the predicate. Documents must be
   *     returned in descending updated timestamp order.
   *
   * @throws QueryParseException if the predicate could not be converted to an
   *     indexed data source.
   */
  ChangeDataSource getSource(Predicate<ChangeData> p, QueryOptions opts)
      throws QueryParseException;

  /**
   * Mark whether this index is up-to-date and ready to serve reads.
   *
   * @param ready whether the index is ready
   * @throws IOException
   */
  void markReady(boolean ready) throws IOException;
}
