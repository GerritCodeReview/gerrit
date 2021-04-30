// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.index.query;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.FieldType;
import java.sql.Timestamp;

/** Predicate that is mapped to a field in the index. */
public abstract class IndexPredicate<I> extends OperatorPredicate<I> implements Matchable<I> {
  private final FieldDef<I, ?> def;

  protected IndexPredicate(FieldDef<I, ?> def, String value) {
    super(def.getName(), value);
    this.def = def;
  }

  protected IndexPredicate(FieldDef<I, ?> def, String name, String value) {
    super(name, value);
    this.def = def;
  }

  public FieldDef<I, ?> getField() {
    return def;
  }

  public FieldType<?> getType() {
    return def.getType();
  }

  @Override
  public boolean match(I object) {
    if (getField().isRepeatable()) {
      Iterable<Object> values = (Iterable<Object>) getField().get(object);
      for (Object v : values) {
        if (matchesSingleObject(v)) {
          return true;
        }
      }
      return false;
    } else {
      return matchesSingleObject(getField().get(object));
    }
  }

  @Override
  public int getCost() {
    return 1;
  }

  private boolean matchesSingleObject(Object fieldValueFromObject) {
    String fieldTypeName = getField().getType().getName();
    if (fieldTypeName.equals(FieldType.LONG.getName())) {
      return fieldValueFromObject.equals(Longs.tryParse(value));
    } else if (fieldTypeName.equals(FieldType.INTEGER.getName())) {
      return fieldValueFromObject.equals(Ints.tryParse(value));
    } else if (fieldTypeName.equals(FieldType.EXACT.getName())) {
      return String.valueOf(fieldValueFromObject).equals(value);
    } else if (fieldTypeName.equals(FieldType.FULL_TEXT.getName())) {
      return String.valueOf(fieldValueFromObject).contains(value);
    } else if (fieldTypeName.equals(FieldType.STORED_ONLY.getName())) {
      throw new IllegalStateException("can't filter for storedOnly field " + getField().getName());
    } else if (fieldTypeName.equals(FieldType.PREFIX.getName())) {
      return String.valueOf(fieldValueFromObject).startsWith(value);
    } else if (fieldTypeName.equals(FieldType.TIMESTAMP.getName())) {
      Timestamp ts = (Timestamp) fieldValueFromObject;
      return ts.equals(new Timestamp(Longs.tryParse(value)));
    } else if (fieldTypeName.equals(FieldType.INTEGER_RANGE.getName())) {
      Integer valueInIndex = (Integer) fieldValueFromObject;

      throw new IllegalStateException("unrecognized field " + getField().getName());
    } else {
      throw new IllegalStateException("unrecognized field " + fieldTypeName);
    }
  }
}
