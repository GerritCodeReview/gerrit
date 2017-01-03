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

package com.google.gerrit.server.query.group;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.inject.Inject;

/**
 * Parses a query string meant to be applied to group objects.
 */
public class GroupQueryBuilder extends QueryBuilder<AccountGroup> {
  public static final String FIELD_UUID = "uuid";

  private static final QueryBuilder.Definition<AccountGroup, GroupQueryBuilder> mydef =
      new QueryBuilder.Definition<>(GroupQueryBuilder.class);

  @Inject
  GroupQueryBuilder() {
    super(mydef);
  }

  @Operator
  public Predicate<AccountGroup> uuid(String uuid) {
    return GroupPredicates.uuid(new AccountGroup.UUID(uuid));
  }
}
