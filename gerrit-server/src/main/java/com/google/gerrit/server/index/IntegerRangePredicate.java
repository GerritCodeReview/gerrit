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

package com.google.gerrit.server.index;

import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;

public abstract class IntegerRangePredicate<T> extends IndexPredicate<T> {
  protected static enum Relation {
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    EQUAL,
  }

  private final Relation relation;
  private final int queryInt;

  protected IntegerRangePredicate(FieldDef<T, Integer> type,
      String value) throws QueryParseException {
    super(type, value);

    if (value.startsWith("<=")) {
      relation = Relation.LESS_THAN_OR_EQUAL;
      value = value.substring(2);
    } else if (value.startsWith("<")) {
      relation = Relation.LESS_THAN;
      value = value.substring(1);
    } else if (value.startsWith(">=")) {
      relation = Relation.GREATER_THAN_OR_EQUAL;
      value = value.substring(2);
    } else if (value.startsWith(">")) {
      relation = Relation.GREATER_THAN;
      value = value.substring(1);
    } else {
      relation = Relation.EQUAL;
    }

    try {
      queryInt = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new QueryParseException(e.getMessage(), e);
    }
  }

  protected abstract int getValueInt(T object) throws OrmException;

  @Override
  public boolean match(T object) throws OrmException {
    int valueInt = getValueInt(object);
    switch (relation) {
      case LESS_THAN:
        return queryInt < valueInt;
      case LESS_THAN_OR_EQUAL:
        return queryInt <= valueInt;
      case GREATER_THAN:
        return queryInt > valueInt;
      case GREATER_THAN_OR_EQUAL:
        return queryInt >= valueInt;
      case EQUAL:
        return queryInt == valueInt;
      default:
        throw new IllegalStateException("Unknown relation " + relation);
    }
  }

  public int getMinimumValue() {
    switch (relation) {
      case LESS_THAN:
      case LESS_THAN_OR_EQUAL:
        return Integer.MIN_VALUE;
      case GREATER_THAN_OR_EQUAL:
      case EQUAL:
        return queryInt;
      case GREATER_THAN:
        if (queryInt == Integer.MAX_VALUE) {
          return queryInt;
        } else {
          return queryInt + 1;
        }
      default:
        throw new IllegalStateException("Unknown relation " + relation);
    }
  }

  public int getMaximumValue() {
    switch (relation) {
      case GREATER_THAN:
      case GREATER_THAN_OR_EQUAL:
        return Integer.MAX_VALUE;
      case LESS_THAN_OR_EQUAL:
      case EQUAL:
        return queryInt;
      case LESS_THAN:
        if (queryInt == Integer.MIN_VALUE) {
          return queryInt;
        } else {
          return queryInt - 1;
        }
      default:
        throw new IllegalStateException("Unknown relation " + relation);
    }
  }

  @Override
  public int getCost() {
    return 1;
  }
}
