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
import com.google.gerrit.index.StoredValue;
import com.google.protobuf.MessageLite;
import java.sql.Timestamp;
import java.util.List;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;

/** Bridge to recover fields from the lucene index. */
public class LuceneStoredValue implements StoredValue {
  /**
   * Lucene represents repeated fields as a list of {@link IndexableField}, so we hold onto a list
   * here to cover both repeated and non-repeated fields.
   */
  private final List<IndexableField> field;

  LuceneStoredValue(List<IndexableField> field) {
    this.field = field;
  }

  @Override
  public String asString() {
    return Iterables.getFirst(asStrings(), null);
  }

  @Override
  public Iterable<String> asStrings() {
    return field.stream().map(f -> f.stringValue()).collect(toImmutableList());
  }

  @Override
  public Integer asInteger() {
    return Iterables.getFirst(asIntegers(), null);
  }

  @Override
  public Iterable<Integer> asIntegers() {
    return field.stream().map(f -> f.numericValue().intValue()).collect(toImmutableList());
  }

  @Override
  public Long asLong() {
    return Iterables.getFirst(asLongs(), null);
  }

  @Override
  public Iterable<Long> asLongs() {
    return field.stream().map(f -> f.numericValue().longValue()).collect(toImmutableList());
  }

  @Override
  public Timestamp asTimestamp() {
    return asLong() == null ? null : new Timestamp(asLong());
  }

  @Override
  public byte[] asByteArray() {
    return Iterables.getFirst(asByteArrays(), null);
  }

  @Override
  public Iterable<byte[]> asByteArrays() {
    return copyAsBytes(field);
  }

  @Override
  public MessageLite asProto() {
    // Lucene does not sore protos
    return null;
  }

  @Override
  public Iterable<MessageLite> asProtos() {
    // Lucene does not store protos
    return null;
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
