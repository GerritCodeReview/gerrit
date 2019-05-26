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

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.IndexedQuery;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.group.InternalGroup;
import java.util.HashSet;
import java.util.Set;

public class IndexedGroupQuery extends IndexedQuery<AccountGroup.UUID, InternalGroup>
    implements DataSource<InternalGroup> {

  public static QueryOptions createOptions(
      IndexConfig config, int start, int limit, Set<String> fields) {
    // Always include GroupField.UUID since it is needed to load the group from NoteDb.
    if (!fields.contains(GroupField.UUID.getName())) {
      fields = new HashSet<>(fields);
      fields.add(GroupField.UUID.getName());
    }
    return QueryOptions.create(config, start, limit, fields);
  }

  public IndexedGroupQuery(
      Index<AccountGroup.UUID, InternalGroup> index,
      Predicate<InternalGroup> pred,
      QueryOptions opts)
      throws QueryParseException {
    super(index, pred, opts.convertForBackend());
  }
}
