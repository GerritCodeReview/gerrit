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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.gerrit.entities.converter.ChangeProtoConverter;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.IndexedField.SearchSpec;
import com.google.gerrit.index.SchemaFieldDefs.Getter;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.SchemaFieldDefs.Setter;
import com.google.gerrit.index.StoredValue;
import com.google.gerrit.index.testing.FakeStoredValue;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Entities.Change;
import com.google.gerrit.proto.Entities.Change_Id;
import com.google.gerrit.proto.Protos;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map.Entry;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Tests for {@link com.google.gerrit.index.IndexedField} */
@RunWith(Theories.class)
public class IndexedFieldTest {

  /** Test input object for {@link IndexedField} */
  static class TestIndexedData {

    private Object testField;

    public Object getTestField() {
      return testField;
    }

    public void setTestField(Object testField) {
      this.testField = testField;
    }
  }

  private static class TestIndexedDataSetter<T> implements Setter<TestIndexedData, T> {
    @Override
    public void set(TestIndexedData testIndexedData, T value) {
      testIndexedData.setTestField(value);
    }
  }

  private static class TestIndexedDataGetter<T> implements Getter<TestIndexedData, T> {
    @Override
    public T get(TestIndexedData input) throws IOException {
      return (T) input.getTestField();
    }
  }

  public static <T> TestIndexedDataSetter<T> setter() {
    return new TestIndexedDataSetter<>();
  }

  public static <T> TestIndexedDataGetter<T> getter() {
    return new TestIndexedDataGetter<>();
  }

  static IndexedField<TestIndexedData, Integer> INTEGER_FIELD =
      IndexedField.<TestIndexedData>integerBuilder("TestField").build(getter(), setter());

  static SearchSpec INTEGER_FIELD_SPEC = INTEGER_FIELD.integer("test");

  static IndexedField<TestIndexedData, Iterable<Integer>> ITERABLE_INTEGER_FIELD =
      IndexedField.<TestIndexedData>iterableIntegerBuilder("TestField").build(getter(), setter());

  static SearchSpec ITERABLE_INTEGER_FIELD_SPEC = ITERABLE_INTEGER_FIELD.integer("test");

  static IndexedField<TestIndexedData, Iterable<String>> ITERABLE_STRING_FIELD =
      IndexedField.<TestIndexedData>iterableStringBuilder("TestField").build(getter(), setter());

  static IndexedField<TestIndexedData, Long> LONG_FIELD =
      IndexedField.<TestIndexedData>longBuilder("TestField").build(getter(), setter());

  static SearchSpec LONG_FIELD_SPEC = LONG_FIELD.longSearch("test");

  static IndexedField<TestIndexedData, Timestamp> TIMESTAMP_FIELD =
      IndexedField.<TestIndexedData>timestampBuilder("TestField").build(getter(), setter());

  static SearchSpec TIMESTAMP_FIELD_SPEC = TIMESTAMP_FIELD.timestamp("test");

  static SearchSpec ITERABLE_STRING_FIELD_SPEC = ITERABLE_STRING_FIELD.fullText("test");

  static IndexedField<TestIndexedData, String> STRING_FIELD =
      IndexedField.<TestIndexedData>stringBuilder("TestField").build(getter(), setter());

  static SearchSpec STRING_FIELD_SPEC = STRING_FIELD.fullText("test");

  static IndexedField<TestIndexedData, Iterable<byte[]>> ITERABLE_STORED_BYTE_FIELD =
      IndexedField.<TestIndexedData>iterableByteArrayBuilder("TestField")
          .stored()
          .build(getter(), setter());

  static SearchSpec ITERABLE_STORED_BYTE_SPEC = ITERABLE_STORED_BYTE_FIELD.storedOnly("test");

  static IndexedField<TestIndexedData, byte[]> STORED_BYTE_FIELD =
      IndexedField.<TestIndexedData>byteArrayBuilder("TestField")
          .stored()
          .build(getter(), setter());

  static SearchSpec STORED_BYTE_SPEC = STORED_BYTE_FIELD.storedOnly("test");

  static IndexedField<TestIndexedData, Entities.Change> STORED_PROTO_FIELD =
      IndexedField.<TestIndexedData, Entities.Change>builder(
              "TestChange", new TypeToken<Entities.Change>() {})
          .stored()
          .build(getter(), setter(), ChangeProtoConverter.INSTANCE);

  static SearchSpec STORED_PROTO_FIELD_SPEC = STORED_PROTO_FIELD.storedOnly("test_change");

  static IndexedField<TestIndexedData, Iterable<Entities.Change>> ITERABLE_STORED_PROTO_FIELD =
      IndexedField.<TestIndexedData, Iterable<Entities.Change>>builder(
              "TestChange", new TypeToken<Iterable<Entities.Change>>() {})
          .stored()
          .build(getter(), setter(), ChangeProtoConverter.INSTANCE);

  static SearchSpec ITERABLE_PROTO_FIELD_SPEC =
      ITERABLE_STORED_PROTO_FIELD.storedOnly("test_change");

  @DataPoints("nonProtoTypes")
  public static final ImmutableList<Entry<IndexedField.SearchSpec, Serializable>>
      fieldToStoredValue =
          new ImmutableMap.Builder()
              .put(INTEGER_FIELD_SPEC, 123456)
              .put(ITERABLE_INTEGER_FIELD_SPEC, ImmutableList.of(123456, 654321))
              .put(LONG_FIELD_SPEC, 123456L)
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
  public static final ImmutableList<Entry<IndexedField.SearchSpec, Serializable>>
      protoFieldToStoredValue =
          ImmutableMap.of(
                  STORED_PROTO_FIELD_SPEC,
                  createChangeProto(12345),
                  ITERABLE_PROTO_FIELD_SPEC,
                  ImmutableList.of(createChangeProto(12345), createChangeProto(54321)))
              .entrySet()
              .asList();

  @Theory
  public void testSetIfPossible(
      @FromDataPoints("nonProtoTypes") Entry<SearchSpec, Object> fieldToStoredValue) {
    Object docValue = fieldToStoredValue.getValue();
    SchemaField searchSpec = fieldToStoredValue.getKey();
    StoredValue storedValue = new FakeStoredValue(fieldToStoredValue.getValue());
    TestIndexedData testIndexedData = new TestIndexedData();
    searchSpec.setIfPossible(testIndexedData, storedValue);
    assertThat(testIndexedData.getTestField()).isEqualTo(docValue);
  }

  @Test
  public void testSetIfPossible_protoFromBytes() {
    Entities.Change changeProto = createChangeProto(12345);
    StoredValue storedValue = new FakeStoredValue(Protos.toByteArray(changeProto));
    TestIndexedData testIndexedData = new TestIndexedData();
    STORED_PROTO_FIELD_SPEC.setIfPossible(testIndexedData, storedValue);
    assertThat(testIndexedData.getTestField()).isEqualTo(changeProto);
  }

  @Test
  public void testSetIfPossible_iterableProtoFromIterableBytes() {
    List<Entities.Change> changeProtos =
        ImmutableList.of(createChangeProto(12345), createChangeProto(54321));
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
      @FromDataPoints("protoTypes") Entry<SearchSpec, Object> fieldToStoredValue) {
    Object docValue = fieldToStoredValue.getValue();
    SchemaField searchSpec = fieldToStoredValue.getKey();
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

  private static Change createChangeProto(int id) {
    return Entities.Change.newBuilder()
        .setChangeId(Change_Id.newBuilder().setId(id).build())
        .build();
  }
}
