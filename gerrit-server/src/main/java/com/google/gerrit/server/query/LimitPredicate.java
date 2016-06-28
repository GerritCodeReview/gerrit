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

package com.google.gerrit.server.query;

public class LimitPredicate<T> extends IntPredicate<T> implements Matchable<T> {
  @SuppressWarnings("unchecked")
  public static Integer getLimit(String fieldName, Predicate<?> p) {
    IntPredicate<?> ip = QueryBuilder.find(p, IntPredicate.class, fieldName);
    return ip != null ? ip.intValue() : null;
  }

  public LimitPredicate(String fieldName, int limit) throws QueryParseException {
    super(fieldName, limit);
    if (limit <= 0) {
      throw new QueryParseException("limit must be positive: " + limit);
    }
  }

  @Override
  public boolean match(T object) {
    return true;
  }

  @Override
  public int getCost() {
    return 0;
  }
}
