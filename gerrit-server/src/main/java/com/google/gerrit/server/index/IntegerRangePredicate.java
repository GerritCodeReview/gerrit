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
import com.google.gerrit.server.util.RangeUtil;
import com.google.gerrit.server.util.RangeUtil.Range;
import com.google.gwtorm.server.OrmException;

public abstract class IntegerRangePredicate<T> extends IndexPredicate<T> {
  private final Range range;

  protected IntegerRangePredicate(FieldDef<T, Integer> type, String value)
      throws QueryParseException {
    super(type, value);
    range = RangeUtil.getRange(value, Integer.MIN_VALUE, Integer.MAX_VALUE);
    if (range == null) {
      throw new QueryParseException("Invalid range predicate: " + value);
    }
  }

  protected abstract Integer getValueInt(T object) throws OrmException;

  public boolean match(T object) throws OrmException {
    Integer valueInt = getValueInt(object);
    if (valueInt == null) {
      return false;
    }
    return valueInt >= range.min && valueInt <= range.max;
  }

  /** Return the minimum value of this predicate's range, inclusive. */
  public int getMinimumValue() {
    return range.min;
  }

  /** Return the maximum value of this predicate's range, inclusive. */
  public int getMaximumValue() {
    return range.max;
  }
}
