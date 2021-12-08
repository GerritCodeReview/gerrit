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

import com.google.gerrit.index.StoredValue;
import java.sql.Timestamp;

/** Bridge to recover fields from the fake index. */
public class FakeStoredValue implements StoredValue {
  private final Object field;

  public FakeStoredValue(Object field) {
    this.field = field;
  }

  @Override
  public String asString() {
    return (String) field;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<String> asStrings() {
    return (Iterable<String>) field;
  }

  @Override
  public Integer asInteger() {
    return (Integer) field;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<Integer> asIntegers() {
    return (Iterable<Integer>) field;
  }

  @Override
  public Long asLong() {
    return (Long) field;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<Long> asLongs() {
    return (Iterable<Long>) field;
  }

  @Override
  public Timestamp asTimestamp() {
    return (Timestamp) field;
  }

  @Override
  public byte[] asByteArray() {
    return (byte[]) field;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<byte[]> asByteArrays() {
    return (Iterable<byte[]>) field;
  }
}
