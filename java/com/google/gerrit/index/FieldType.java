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

package com.google.gerrit.index;

import com.google.common.collect.ImmutableList;
import java.sql.Timestamp;

/** Document field types supported by the secondary index system. */
public class FieldType<T> {
  /** A single integer-valued field. */
  public static final FieldType<Integer> INTEGER = new FieldType<>("INTEGER");

  /** A single-integer-valued field matched using range queries. */
  public static final FieldType<Integer> INTEGER_RANGE = new FieldType<>("INTEGER_RANGE");

  /** A single integer-valued field. */
  public static final FieldType<Long> LONG = new FieldType<>("LONG");

  /** A single date/time-valued field. */
  public static final FieldType<Timestamp> TIMESTAMP = new FieldType<>("TIMESTAMP");

  /** A string field searched using exact-match semantics. */
  public static final FieldType<String> EXACT = new FieldType<>("EXACT");

  /** A string field searched using prefix. */
  public static final FieldType<String> PREFIX = new FieldType<>("PREFIX");

  /** A string field searched using fuzzy-match semantics. */
  public static final FieldType<String> FULL_TEXT = new FieldType<>("FULL_TEXT");

  /** A field that is only stored as raw bytes and cannot be queried. */
  public static final FieldType<byte[]> STORED_ONLY = new FieldType<>("STORED_ONLY");

  /** List of all types that are stored as {@link String} in the index. */
  public static final ImmutableList<FieldType<String>> STRING_TYPES =
      ImmutableList.of(EXACT, PREFIX, FULL_TEXT);

  /** List of all types that are stored as {@link Integer} in the index. */
  public static final ImmutableList<FieldType<Integer>> INTEGER_TYPES =
      ImmutableList.of(INTEGER_RANGE, INTEGER);

  private final String name;

  /**
   * Note that {@link FieldDef} does not override {@link Object#equals(Object)}. It relies on
   * instances being singletons so that the default (i.e. reference) comparison works.
   */
  private FieldType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }

  public static IllegalArgumentException badFieldType(FieldType<?> t) {
    return new IllegalArgumentException("unknown index field type " + t);
  }
}
