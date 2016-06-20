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

package com.google.gerrit.server.index.change;

import static com.google.gerrit.server.index.change.ChangeField.CHANGE;
import static com.google.gerrit.server.index.change.ChangeField.PROJECT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.IndexedQuery;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;

import java.util.HashSet;
import java.util.Set;

/**
 * Wrapper combining an {@link IndexPredicate} together with a
 * {@link ChangeDataSource} that returns matching results from the index.
 * <p>
 * Appropriate to return as the rootmost predicate that can be processed using
 * the secondary index; such predicates must also implement
 * {@link ChangeDataSource} to be chosen by the query processor.
 */
public class IndexedChangeQuery extends IndexedQuery<Change.Id, ChangeData>
    implements ChangeDataSource {
  public static QueryOptions oneResult() {
    return createOptions(IndexConfig.createDefault(), 0, 1,
        ImmutableSet.<String> of());
  }

  public static QueryOptions createOptions(IndexConfig config, int start,
      int limit, Set<String> fields) {
    // Always include project since it is needed to load the change from NoteDb.
    if (!fields.contains(CHANGE.getName())
        && !fields.contains(PROJECT.getName())) {
      fields = new HashSet<>(fields);
      fields.add(PROJECT.getName());
    }
    return QueryOptions.create(config, start, limit, fields);
  }

  @VisibleForTesting
  static QueryOptions convertOptions(QueryOptions opts) {
    // Increase the limit rather than skipping, since we don't know how many
    // skipped results would have been filtered out by the enclosing AndSource.
    int backendLimit = opts.config().maxLimit();
    int limit = Ints.saturatedCast((long) opts.limit() + opts.start());
    limit = Math.min(limit, backendLimit);
    return IndexedChangeQuery.createOptions(opts.config(), 0, limit, opts.fields());
  }

  public IndexedChangeQuery(ChangeIndex index, Predicate<ChangeData> pred,
      QueryOptions opts) throws QueryParseException {
    super(index, pred, convertOptions(opts));
  }

  @Override
  public boolean hasChange() {
    return index.getSchema().hasField(ChangeField.CHANGE);
  }
}
