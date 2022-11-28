// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.index.testing.TestIndexedFields.INTEGER_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.INTEGER_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.INTEGER_RANGE_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.INTEGER_RANGE_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.ITERABLE_INTEGER_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.ITERABLE_INTEGER_RANGE_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.ITERABLE_LONG_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.ITERABLE_LONG_RANGE_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.ITERABLE_PROTO_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.ITERABLE_STORED_BYTE_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.ITERABLE_STORED_BYTE_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.ITERABLE_STORED_PROTO_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.ITERABLE_STRING_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.ITERABLE_STRING_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.LONG_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.LONG_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.LONG_RANGE_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.LONG_RANGE_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.STORED_BYTE_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.STORED_BYTE_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.STORED_PROTO_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.STORED_PROTO_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.STRING_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.STRING_FIELD_SPEC;
import static com.google.gerrit.index.testing.TestIndexedFields.TIMESTAMP_FIELD;
import static com.google.gerrit.index.testing.TestIndexedFields.TIMESTAMP_FIELD_SPEC;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.StoredValue;
import com.google.gerrit.index.testing.FakeStoredValue;
import com.google.gerrit.index.testing.TestIndexedFields;
import com.google.gerrit.index.testing.TestIndexedFields.TestIndexedData;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Protos;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Map.Entry;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Tests for {@link com.google.gerrit.index.IndexedField} */
@SuppressWarnings("serial")
@RunWith(Theories.class)
public class IndexedFieldTest {

  @DataPoints("nonProtoTypes")
  public static final ImmutableList<
          Entry<IndexedField<TestIndexedData, ?>.SearchSpec, Serializable>>
      fieldToStoredValue =
          new ImmutableMap.Builder<IndexedField<TestIndexedData, ?>.SearchSpec, Serializable>()
              .put(INTEGER_FIELD_SPEC, 123456)
              .put(INTEGER_RANGE_FIELD_SPEC, 123456)
              .put(ITERABLE_INTEGER_FIELD_SPEC, ImmutableList.of(123456, 654321))
              .put(ITERABLE_INTEGER_RANGE_FIELD_SPEC, ImmutableList.of(123456, 654321))
              .put(LONG_FIELD_SPEC, 123456L)
              .put(LONG_RANGE_FIELD_SPEC, 123456L)
              .put(ITERABLE_LONG_FIELD_SPEC, ImmutableList.of(123456L, 654321L))
              .put(ITERABLE_LONG_RANGE_FIELD_SPEC, ImmutableList.of(123456L, 654321L))
              .put(TIMESTAMP_FIELD_SPEC, new Timestamp(1234567L))
              .put(STRING_FIELD_SPEC, "123456")
              .put(ITERABLE_STRING_FIELD_SPEC, ImmutableList.of("123456"))
              .put(
                  ITERABLE_STORED_BYTE_SPEC,
                  ImmutableList.of("123456".getBytes(StandardCharsets.UTF_8)))
              .put(STORED_BYTE_SPEC, "123456".getBytes(StandardCharsets.UTF_8))
              .build()
              .entrySet()
              .asList();

  @DataPoints("protoTypes")
  public static final ImmutableList<
          Entry<IndexedField<TestIndexedData, ?>.SearchSpec, Serializable>>
      protoFieldToStoredValue =
          ImmutableMap.<IndexedField<TestIndexedData, ?>.SearchSpec, Serializable>of(
                  STORED_PROTO_FIELD_SPEC,
                  TestIndexedFields.createChangeProto(12345),
                  ITERABLE_PROTO_FIELD_SPEC,
                  ImmutableList.of(
                      TestIndexedFields.createChangeProto(12345),
                      TestIndexedFields.createChangeProto(54321)))
              .entrySet()
              .asList();

  @Theory
  public void testSetIfPossible(
      @FromDataPoints("nonProtoTypes")
          Entry<IndexedField<TestIndexedData, StoredValue>.SearchSpec, StoredValue>
              fieldToStoredValue) {
    Object docValue = fieldToStoredValue.getValue();
    IndexedField<TestIndexedData, StoredValue>.SearchSpec searchSpec = fieldToStoredValue.getKey();
    StoredValue storedValue = new FakeStoredValue(fieldToStoredValue.getValue());
    TestIndexedData testIndexedData = new TestIndexedData();
    searchSpec.setIfPossible(testIndexedData, storedValue);
    assertThat(testIndexedData.getTestField()).isEqualTo(docValue);
  }

  @Test
  public void testSetIfPossible_protoFromBytes() {
    Entities.Change changeProto = TestIndexedFields.createChangeProto(12345);
    StoredValue storedValue = new FakeStoredValue(Protos.toByteArray(changeProto));
    TestIndexedData testIndexedData = new TestIndexedData();
    STORED_PROTO_FIELD_SPEC.setIfPossible(testIndexedData, storedValue);
    assertThat(testIndexedData.getTestField()).isEqualTo(changeProto);
  }

  @Test
  public void testSetIfPossible_iterableProtoFromIterableBytes() {
    ImmutableList<Entities.Change> changeProtos =
        ImmutableList.of(
            TestIndexedFields.createChangeProto(12345), TestIndexedFields.createChangeProto(54321));
    StoredValue storedValue =
        new FakeStoredValue(
            changeProtos.stream()
                .map(proto -> Protos.toByteArray(proto))
                .collect(toImmutableList()));
    TestIndexedData testIndexedData = new TestIndexedData();
    ITERABLE_STORED_PROTO_FIELD.setIfPossible(testIndexedData, storedValue);
    assertThat(testIndexedData.getTestField()).isEqualTo(changeProtos);
  }

  @Theory
  public void testSetIfPossible_fromProto(
      @FromDataPoints("protoTypes")
          Entry<IndexedField<TestIndexedData, StoredValue>.SearchSpec, StoredValue>
              fieldToStoredValue) {
    Object docValue = fieldToStoredValue.getValue();
    IndexedField<TestIndexedData, StoredValue>.SearchSpec searchSpec = fieldToStoredValue.getKey();
    StoredValue storedValue = new FakeStoredValue(fieldToStoredValue.getValue(), /*isProto=*/ true);
    TestIndexedData testIndexedData = new TestIndexedData();
    searchSpec.setIfPossible(testIndexedData, storedValue);
    assertThat(testIndexedData.getTestField()).isEqualTo(docValue);
  }

  @Test
  public void test_isProtoType() {
    assertThat(STORED_PROTO_FIELD.isProtoType()).isTrue();

    assertThat(ITERABLE_STORED_PROTO_FIELD.isProtoType()).isFalse();
    assertThat(INTEGER_FIELD.isProtoType()).isFalse();
    assertThat(ITERABLE_STRING_FIELD.isProtoType()).isFalse();
    assertThat(STORED_BYTE_FIELD.isProtoType()).isFalse();
    assertThat(ITERABLE_STORED_BYTE_FIELD.isProtoType()).isFalse();
  }

  @Test
  public void test_isProtoIterableType() {

    assertThat(ITERABLE_STORED_PROTO_FIELD.isProtoIterableType()).isTrue();

    assertThat(STORED_PROTO_FIELD.isProtoIterableType()).isFalse();
    assertThat(INTEGER_FIELD.isProtoIterableType()).isFalse();
    assertThat(ITERABLE_STRING_FIELD.isProtoIterableType()).isFalse();
    assertThat(STORED_BYTE_FIELD.isProtoIterableType()).isFalse();
    assertThat(ITERABLE_STORED_BYTE_FIELD.isProtoType()).isFalse();
  }

  @Test
  public void test_isStoredSearchSpec() {
    assertThat(STORED_PROTO_FIELD_SPEC.isStored()).isTrue();
    assertThat(ITERABLE_PROTO_FIELD_SPEC.isStored()).isTrue();
    assertThat(STORED_BYTE_SPEC.isStored()).isTrue();
    assertThat(ITERABLE_STORED_BYTE_SPEC.isStored()).isTrue();

    assertThat(STRING_FIELD.storedExact("stored_spec").isStored()).isTrue();
    assertThat(STRING_FIELD.exact("non_stored_spec").isStored()).isFalse();

    assertThat(INTEGER_FIELD.storedInteger("stored_spec").isStored()).isTrue();
    assertThat(INTEGER_FIELD.integer("non_stored_spec").isStored()).isFalse();

    assertThat(TIMESTAMP_FIELD.storedTimestamp("stored_spec").isStored()).isTrue();
    assertThat(TIMESTAMP_FIELD.timestamp("non_stored_spec").isStored()).isFalse();

    assertThat(LONG_FIELD.storedLongSearch("stored_spec").isStored()).isTrue();
    assertThat(LONG_FIELD.longSearch("non_stored_spec").isStored()).isFalse();

    assertThat(STRING_FIELD.storedFullText("stored_spec_full_text").isStored()).isTrue();
    assertThat(STRING_FIELD.fullText("non_stored_spec_full_text").isStored()).isFalse();

    assertThat(LONG_RANGE_FIELD.storedRange("stored_spec").isStored()).isTrue();
    assertThat(LONG_RANGE_FIELD.range("non_stored_spec").isStored()).isFalse();

    assertThat(INTEGER_RANGE_FIELD.storedIntegerRange("stored_spec").isStored()).isTrue();
    assertThat(INTEGER_RANGE_FIELD.integerRange("non_stored_spec").isStored()).isFalse();

    assertThat(STRING_FIELD.storedPrefix("stored_spec_prefix").isStored()).isTrue();
    assertThat(STRING_FIELD.prefix("non_stored_spec_prefix").isStored()).isFalse();
  }
}
