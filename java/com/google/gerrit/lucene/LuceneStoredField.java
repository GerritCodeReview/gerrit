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

package com.google.gerrit.lucene;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.gerrit.index.StoredField;
import java.sql.Timestamp;
import java.util.List;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;

/** Bridge to recover fields from the lucene index. */
public class LuceneStoredField implements StoredField {
  private final List<IndexableField> field;

  LuceneStoredField(List<IndexableField> field) {
    this.field = field;
  }

  @Override
  public String stringValue() {
    return Iterables.getFirst(stringValues(), null);
  }

  @Override
  public Iterable<String> stringValues() {
    return field.stream().map(f -> f.stringValue()).collect(toImmutableList());
  }

  @Override
  public Integer integerValue() {
    return Iterables.getFirst(integerValues(), null);
  }

  @Override
  public Iterable<Integer> integerValues() {
    return field.stream().map(f -> f.numericValue().intValue()).collect(toImmutableList());
  }

  @Override
  public Long longValue() {
    return Iterables.getFirst(longValues(), null);
  }

  @Override
  public Iterable<Long> longValues() {
    return field.stream().map(f -> f.numericValue().longValue()).collect(toImmutableList());
  }

  @Override
  public Timestamp timestampValue() {
    return longValue() == null ? null : new Timestamp(longValue());
  }

  @Override
  public byte[] rawValue() {
    return Iterables.getFirst(rawValues(), null);
  }

  @Override
  public Iterable<byte[]> rawValues() {
    return copyAsBytes(field);
  }

  private static List<byte[]> copyAsBytes(List<IndexableField> fields) {
    return fields.stream()
        .map(
            f -> {
              BytesRef ref = f.binaryValue();
              byte[] b = new byte[ref.length];
              System.arraycopy(ref.bytes, ref.offset, b, 0, ref.length);
              return b;
            })
        .collect(toList());
  }
}
