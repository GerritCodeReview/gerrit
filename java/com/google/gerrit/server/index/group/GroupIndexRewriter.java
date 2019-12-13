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

import static java.util.Objects.requireNonNull;

import com.google.gerrit.index.IndexRewriter;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.group.InternalGroup;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Rewriter for the group index. See {@link IndexRewriter} for details. */
@Singleton
public class GroupIndexRewriter implements IndexRewriter<InternalGroup> {
  private final GroupIndexCollection indexes;

  @Inject
  GroupIndexRewriter(GroupIndexCollection indexes) {
    this.indexes = indexes;
  }

  @Override
  public Predicate<InternalGroup> rewrite(Predicate<InternalGroup> in, QueryOptions opts)
      throws QueryParseException {
    GroupIndex index = indexes.getSearchIndex();
    requireNonNull(index, "no active search index configured for groups");
    return new IndexedGroupQuery(index, in, opts);
  }
}
