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

package com.google.gerrit.server.index;

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

  private final String name;

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
