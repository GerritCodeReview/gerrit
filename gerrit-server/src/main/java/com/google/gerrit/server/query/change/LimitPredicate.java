// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.gerrit.server.query.change.ChangeQueryBuilder.FIELD_LIMIT;

import com.google.gerrit.server.query.IntPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;

public class LimitPredicate extends IntPredicate<ChangeData> {
  @SuppressWarnings("unchecked")
  public static Integer getLimit(Predicate<ChangeData> p) {
    IntPredicate<?> ip = QueryBuilder.find(p, IntPredicate.class, FIELD_LIMIT);
    return ip != null ? ip.intValue() : null;
  }

  public LimitPredicate(int limit) throws QueryParseException {
    super(ChangeQueryBuilder.FIELD_LIMIT, limit);
    if (limit <= 0) {
      throw new QueryParseException("limit must be positive: " + limit);
    }
  }

  @Override
  public boolean match(ChangeData object) {
    return true;
  }

  @Override
  public int getCost() {
    return 0;
  }
}
