// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.index.group;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.IndexedQuery;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import java.util.HashSet;
import java.util.Set;

/**
 * Wrapper around {@link Predicate}s that are returned by the {@link
 * com.google.gerrit.index.IndexRewriter}. See {@link IndexedQuery}.
 */
public class IndexedGroupQuery extends IndexedQuery<AccountGroup.UUID, InternalGroup>
    implements DataSource<InternalGroup>, Matchable<InternalGroup> {

  public static QueryOptions createOptions(
      IndexConfig config, int start, int pageSize, int limit, Set<String> fields) {
    // Always include GroupField.UUID since it is needed to load the group from NoteDb.
    if (!fields.contains(GroupField.UUID_FIELD_SPEC.getName())) {
      fields = new HashSet<>(fields);
      fields.add(GroupField.UUID_FIELD_SPEC.getName());
    }
    return QueryOptions.create(config, start, pageSize, limit, fields);
  }

  public IndexedGroupQuery(
      Index<AccountGroup.UUID, InternalGroup> index,
      Predicate<InternalGroup> pred,
      QueryOptions opts)
      throws QueryParseException {
    super(index, pred, opts.convertForBackend());
  }

  @Override
  public boolean match(InternalGroup object) {
    Predicate<InternalGroup> pred = getChild(0);
    checkState(
        pred.isMatchable(),
        "match invoked, but child predicate %s doesn't implement %s",
        pred,
        Matchable.class.getName());
    return pred.asMatchable().match(object);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
