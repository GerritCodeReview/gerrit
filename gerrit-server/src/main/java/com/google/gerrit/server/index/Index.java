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

import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import java.io.IOException;

/**
 * Secondary index implementation for arbitrary documents.
 *
 * <p>Documents are inserted into the index and are queried by converting special {@link
 * com.google.gerrit.server.query.Predicate} instances into index-aware predicates that use the
 * index search results as a source.
 *
 * <p>Implementations must be thread-safe and should batch inserts/updates where appropriate.
 */
public interface Index<K, V> {
  /** @return the schema version used by this index. */
  Schema<V> getSchema();

  /** Close this index. */
  void close();

  /**
   * Update a document in the index.
   *
   * <p>Semantically equivalent to deleting the document and reinserting it with new field values. A
   * document that does not already exist is created. Results may not be immediately visible to
   * searchers, but should be visible within a reasonable amount of time.
   *
   * @param obj document object
   * @throws IOException
   */
  void replace(V obj) throws IOException;

  /**
   * Delete a document from the index by key.
   *
   * @param key document key
   * @throws IOException
   */
  void delete(K key) throws IOException;

  /**
   * Delete all documents from the index.
   *
   * @throws IOException
   */
  void deleteAll() throws IOException;

  /**
   * Convert the given operator predicate into a source searching the index and returning only the
   * documents matching that predicate.
   *
   * <p>This method may be called multiple times for variations on the same predicate or multiple
   * predicate subtrees in the course of processing a single query, so it should not have any side
   * effects (e.g. starting a search in the background).
   *
   * @param p the predicate to match. Must be a tree containing only AND, OR, or NOT predicates as
   *     internal nodes, and {@link IndexPredicate}s as leaves.
   * @param opts query options not implied by the predicate, such as start and limit.
   * @return a source of documents matching the predicate, returned in a defined order depending on
   *     the type of documents.
   * @throws QueryParseException if the predicate could not be converted to an indexed data source.
   */
  DataSource<V> getSource(Predicate<V> p, QueryOptions opts) throws QueryParseException;

  /**
   * Mark whether this index is up-to-date and ready to serve reads.
   *
   * @param ready whether the index is ready
   * @throws IOException
   */
  void markReady(boolean ready) throws IOException;
}
