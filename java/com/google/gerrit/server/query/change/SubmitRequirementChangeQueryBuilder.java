// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.inject.Inject;

/**
 * A query builder for submit requirement expressions that includes all {@link ChangeQueryBuilder}
 * operators, in addition to extra operators contributed by this class.
 *
 * <p>Operators defined in this class cannot be used in change queries.
 */
public class SubmitRequirementChangeQueryBuilder extends ChangeQueryBuilder {

  private static final QueryBuilder.Definition<ChangeData, ChangeQueryBuilder> def =
      new QueryBuilder.Definition<>(SubmitRequirementChangeQueryBuilder.class);

  private final TruePredicate truePredicate;
  private final FalsePredicate falsePredicate;

  @Inject
  SubmitRequirementChangeQueryBuilder(
      Arguments args, TruePredicate truePredicate, FalsePredicate falsePredicate) {
    super(def, args);
    this.truePredicate = truePredicate;
    this.falsePredicate = falsePredicate;
  }

  @Override
  protected void checkFieldAvailable(FieldDef<ChangeData, ?> field, String operator) {
    // Submit requirements don't rely on the index, so they can be used regardless of index schema
    // version.
  }

  @Override
  public Predicate<ChangeData> is(String value) throws QueryParseException {
    if ("true".equals(value)) {
      return truePredicate;
    }
    if ("false".equals(value)) {
      return falsePredicate;
    }
    return super.is(value);
  }
}
