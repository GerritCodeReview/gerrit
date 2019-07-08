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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.index.IndexRewriter;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GroupIndexRewriter implements IndexRewriter<AccountGroup> {
  private final GroupIndexCollection indexes;

  @Inject
  GroupIndexRewriter(GroupIndexCollection indexes) {
    this.indexes = indexes;
  }

  @Override
  public Predicate<AccountGroup> rewrite(Predicate<AccountGroup> in, QueryOptions opts)
      throws QueryParseException {
    GroupIndex index = indexes.getSearchIndex();
    checkNotNull(index, "no active search index configured for groups");
    return new IndexedGroupQuery(index, in, opts);
  }
}
