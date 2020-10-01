// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.inject.Provider;

public class PeersPredicate extends PostFilterPredicate<ChangeData> {
  protected final Provider<InternalChangeQuery> queryProvider;
  protected Predicate<ChangeData> predicate;

  PeersPredicate(ChangeQueryBuilder.Arguments qbArgs, String query) throws QueryParseException {
    super(ChangeQueryBuilder.FIELD_PEERS, query);
    this.queryProvider = qbArgs.queryProvider;

    ChangeQueryBuilder builder = new ChangeQueryBuilder(qbArgs);
    QueryOptions opts = QueryOptions.create(
        qbArgs.indexConfig,
        0,
        1,
        qbArgs.index.getSchema().getStoredFields().keySet());
    predicate = qbArgs.rewriter.rewrite(builder.parse(query), opts);
  }

  @Override
  public boolean match(ChangeData cd) {
    return queryProvider.get().byKey(cd.change().getKey()).stream()
        .anyMatch(peer -> !cd.getId().equals(peer.getId()) && predicate.asMatchable().match(peer));
  }

  @Override
  public int getCost() {
    return 3;
  }
}
