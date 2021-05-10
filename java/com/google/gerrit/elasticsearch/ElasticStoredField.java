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

package com.google.gerrit.elasticsearch;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gerrit.index.StoredField;
import com.google.gson.JsonElement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.stream.StreamSupport;

/** Bridge to recover fields from the elastic index. */
public class ElasticStoredField implements StoredField {
  private final JsonElement field;

  ElasticStoredField(JsonElement field) {
    this.field = field;
  }

  @Override
  public String stringValue() {
    return field.getAsString();
  }

  @Override
  public Iterable<String> stringValues() {
    return StreamSupport.stream(field.getAsJsonArray().spliterator(), false)
        .map(f -> f.getAsString())
        .collect(toImmutableList());
  }

  @Override
  public Integer integerValue() {
    return field.getAsInt();
  }

  @Override
  public Iterable<Integer> integerValues() {
    return StreamSupport.stream(field.getAsJsonArray().spliterator(), false)
        .map(f -> f.getAsInt())
        .collect(toImmutableList());
  }

  @Override
  public Long longValue() {
    return field.getAsLong();
  }

  @Override
  public Iterable<Long> longValues() {
    return StreamSupport.stream(field.getAsJsonArray().spliterator(), false)
        .map(f -> f.getAsLong())
        .collect(toImmutableList());
  }

  @Override
  public Timestamp timestampValue() {
    return Timestamp.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(field.getAsString())));
  }

  @Override
  public byte[] rawValue() {
    return AbstractElasticIndex.decodeBase64(field.getAsString());
  }

  @Override
  public Iterable<byte[]> rawValues() {
    return StreamSupport.stream(field.getAsJsonArray().spliterator(), false)
        .map(f -> AbstractElasticIndex.decodeBase64(f.getAsString()))
        .collect(toImmutableList());
  }
}
