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

package com.google.gerrit.index;

import java.sql.Timestamp;

/**
 * Representation of a field stored on the index. Used to load field values from different index
 * backends.
 */
public interface StoredField {
  /** Returns the {@link String} value of the field. */
  String stringValue();

  /** Returns the {@link String} values of the field. */
  Iterable<String> stringValues();

  /** Returns the {@link Integer} value of the field. */
  Integer integerValue();

  /** Returns the {@link Integer} values of the field. */
  Iterable<Integer> integerValues();

  /** Returns the {@link Long} value of the field. */
  Long longValue();

  /** Returns the {@link Long} values of the field. */
  Iterable<Long> longValues();

  /** Returns the {@link Timestamp} value of the field. */
  Timestamp timestampValue();

  /** Returns the {@link byte[]} value of the field. */
  byte[] rawValue();

  /** Returns the {@link byte[]} values of the field. */
  Iterable<byte[]> rawValues();
}
