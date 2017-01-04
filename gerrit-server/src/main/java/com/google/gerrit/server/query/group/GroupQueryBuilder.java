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

import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.query.LimitPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.inject.Inject;

/**
 * Parses a query string meant to be applied to group objects.
 */
public class GroupQueryBuilder extends QueryBuilder<AccountGroup> {
  public static final String FIELD_UUID = "uuid";
  public static final String FIELD_DESCRIPTION = "description";
  public static final String FIELD_INNAME = "inname";
  public static final String FIELD_NAME = "name";
  public static final String FIELD_OWNER = "owner";
  public static final String FIELD_LIMIT = "limit";

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

  @Operator
  public Predicate<AccountGroup> description(String description) {
    return GroupPredicates.description(description);
  }

  @Operator
  public Predicate<AccountGroup> inname(String namePart) {
    if (namePart.isEmpty()) {
      return name(namePart);
    }
    return GroupPredicates.inname(namePart);
  }

  @Operator
  public Predicate<AccountGroup> name(String name) {
    return GroupPredicates.name(name);
  }

  @Operator
  public Predicate<AccountGroup> owner(String owner) {
    return GroupPredicates.owner(owner);
  }

  @Operator
  public Predicate<AccountGroup> is(String value) throws QueryParseException {
    if ("visibleToAll".equalsIgnoreCase(value)) {
      return GroupPredicates.isVisibleToAll();
    }
    throw error("Invalid query");
  }

  @Operator
  public Predicate<AccountGroup> limit(String query)
      throws QueryParseException {
    Integer limit = Ints.tryParse(query);
    if (limit == null) {
      throw error("Invalid limit: " + query);
    }
    return new LimitPredicate<>(FIELD_LIMIT, limit);
  }
}
