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

import com.google.gerrit.index.StoredValue;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Stream;
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
    return asStrings().findFirst().orElse(null);
  }

  @Override
  public Stream<String> asStrings() {
    return field.stream().map(f -> f.stringValue());
  }

  @Override
  public Integer asInteger() {
    return asIntegers().findFirst().orElse(null);
  }

  @Override
  public Stream<Integer> asIntegers() {
    return field.stream().map(f -> f.numericValue().intValue());
  }

  @Override
  public Long asLong() {
    return asLongs().findFirst().orElse(null);
  }

  @Override
  public Stream<Long> asLongs() {
    return field.stream().map(f -> f.numericValue().longValue());
  }

  @Override
  public Timestamp asTimestamp() {
    return asLong() == null ? null : new Timestamp(asLong());
  }

  @Override
  public byte[] asByteArray() {
    return asByteArrays().findFirst().orElse(null);
  }

  @Override
  public Stream<byte[]> asByteArrays() {
    return field.stream()
        .map(
            f -> {
              BytesRef ref = f.binaryValue();
              byte[] b = new byte[ref.length];
              System.arraycopy(ref.bytes, ref.offset, b, 0, ref.length);
              return b;
            });
  }
}
