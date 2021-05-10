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

package com.google.gerrit.index.testing;

import com.google.gerrit.index.StoredField;
import java.sql.Timestamp;

/** Bridge to recover fields from the elastic index. */
public class FakeStoredField implements StoredField {
  private final Object field;

  FakeStoredField(Object field) {
    this.field = field;
  }

  @Override
  public String stringValue() {
    return (String) field;
  }

  @Override
  public Iterable<String> stringValues() {
    return (Iterable<String>) field;
  }

  @Override
  public Integer integerValue() {
    return (Integer) field;
  }

  @Override
  public Iterable<Integer> integerValues() {
    return (Iterable<Integer>) field;
  }

  @Override
  public Long longValue() {
    return (Long) field;
  }

  @Override
  public Timestamp timestampValue() {
    return (Timestamp) field;
  }

  @Override
  public Iterable<Long> longValues() {
    return (Iterable<Long>) field;
  }

  @Override
  public byte[] rawValue() {
    return (byte[]) field;
  }

  @Override
  public Iterable<byte[]> rawValues() {
    return (Iterable<byte[]>) field;
  }
}
