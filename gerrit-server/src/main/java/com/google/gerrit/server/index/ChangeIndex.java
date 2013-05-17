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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.index;

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
  /**
   * Insert a change document into the index.
   * <p>
   * Results may not be immediately visible to searchers, but should be visible
   * within a reasonable amount of time.
   *
   * @param cd change document with all index fields prepopulated; see
   *     {@link ChangeData#fillIndexFields}.
   *
   * @throws IOException if the change could not be inserted.
   */
  public void insert(ChangeData cd) throws IOException;

  /**
   * Update a change document in the index.
   * <p>
   * Semantically equivalent to removing the document and reinserting it with
   * new field values. Results may not be immediately visible to searchers, but
   * should be visible within a reasonable amount of time.
   *
   * @param cd change document with all index fields prepopulated; see
   *     {@link ChangeData#fillIndexFields}.
   *
   * @throws IOException
   */
  public void update(ChangeData cd) throws IOException;

  /**
   * Convert the given operator predicate into a source searching the index and
   * returning only the documents matching that predicate.
   *
   * @param p the predicate to match.
   * @return a source of documents matching the predicate.
   *
   * @throws QueryParseException if the predicate could not be converted to an
   *     indexed data source.
   */
  public ChangeDataSource getSource(IndexPredicate<ChangeData> p)
      throws QueryParseException;
}
