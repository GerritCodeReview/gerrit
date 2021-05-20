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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.FieldType;
import java.util.Objects;
import java.util.Set;
import java.util.stream.StreamSupport;

/** Predicate that is mapped to a field in the index. */
public abstract class IndexPredicate<I> extends OperatorPredicate<I> implements Matchable<I> {
  /**
   * Text segmentation to be applied to both the query string and the indexed field for full-text
   * queries. This is inspired by http://unicode.org/reports/tr29/ which is what Lucene uses, but
   * complexity was reduced to the bare minimum at the cost of small discrepancies to the Unicode
   * spec.
   */
  private static final Splitter FULL_TEXT_SPLITTER = Splitter.on(CharMatcher.anyOf(" ,.-\\/_\n"));

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
  public boolean match(I cd) {
    if (getField().isRepeatable()) {
      Iterable<Object> values = (Iterable<Object>) getField().get(cd);
      for (Object v : values) {
        if (matchesSingleObject(v)) {
          return true;
        }
      }
      return false;
    } else {
      return matchesSingleObject(getField().get(cd));
    }
  }

  @Override
  public int getCost() {
    return 1;
  }

  private boolean matchesSingleObject(Object fieldValueFromObject) {
    String fieldTypeName = getField().getType().getName();
    if (fieldTypeName.equals(FieldType.INTEGER.getName())) {
      return Objects.equals(fieldValueFromObject, Ints.tryParse(value));
    } else if (fieldTypeName.equals(FieldType.EXACT.getName())) {
      return Objects.equals(fieldValueFromObject, value);
    } else if (fieldTypeName.equals(FieldType.LONG.getName())) {
      return Objects.equals(fieldValueFromObject, Longs.tryParse(value));
    } else if (fieldTypeName.equals(FieldType.PREFIX.getName())) {
      return String.valueOf(fieldValueFromObject).startsWith(value);
    } else if (fieldTypeName.equals(FieldType.FULL_TEXT.getName())) {
      Set<String> tokenizedField = tokenizeString(String.valueOf(fieldValueFromObject));
      Set<String> tokenizedValue = tokenizeString(value);
      return !Sets.intersection(tokenizedField, tokenizedValue).isEmpty();
    } else if (fieldTypeName.equals(FieldType.STORED_ONLY.getName())) {
      throw new IllegalStateException("can't filter for storedOnly field " + getField().getName());
    } else if (fieldTypeName.equals(FieldType.TIMESTAMP.getName())) {
      throw new IllegalStateException("timestamp queries must be handled in subclasses");
    } else if (fieldTypeName.equals(FieldType.INTEGER_RANGE.getName())) {
      throw new IllegalStateException("integer range queries must be handled in subclasses");
    } else {
      throw new IllegalStateException("unrecognized field " + fieldTypeName);
    }
  }

  private static ImmutableSet<String> tokenizeString(String value) {
    return StreamSupport.stream(FULL_TEXT_SPLITTER.split(value.toLowerCase()).spliterator(), false)
        .filter(s -> !s.trim().isEmpty())
        .collect(toImmutableSet());
  }
}
