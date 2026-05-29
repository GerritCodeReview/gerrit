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

package com.google.gerrit.index.testing;

import com.google.common.reflect.TypeToken;
import com.google.gerrit.entities.converter.ChangeProtoConverter;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.SchemaFieldDefs.Getter;
import com.google.gerrit.index.SchemaFieldDefs.Setter;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Entities.Change;
import com.google.gerrit.proto.Entities.Change_Id;
import java.io.IOException;
import java.sql.Timestamp;

/**
 * Collection of {@link IndexedField}, used in unit tests.
 *
 * <p>The list of {@link IndexedField} below are field types, that are currently supported and used
 * in different index implementations
 *
 * <p>They are used in unit tests to make sure these types can be extracted to index and assigned
 * back to object.
 */
public final class TestIndexedFields {

  /** Test input object for {@link IndexedField} */
  public static class TestIndexedData {

    /** Key that is used to index to identify indexed object */
    private Object key;

    /** Field value that is extracted from this indexed object to the index document. */
    private Object testFieldValue;

    public Object getTestField() {
      return testFieldValue;
    }

    public void setTestFieldValue(Object testFieldValue) {
      this.testFieldValue = testFieldValue;
    }

    public Object getKey() {
      return key;
    }

    public void setKey(Object key) {
      this.key = key;
    }
  }

  /** Setter for {@link TestIndexedData} */
  private static class TestIndexedDataSetter<T> implements Setter<TestIndexedData, T> {
    @Override
    public void set(TestIndexedData testIndexedData, T value) {
      testIndexedData.setTestFieldValue(value);
    }
  }

  /** Getter for {@link TestIndexedData} */
  @SuppressWarnings("unchecked")
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

  public static final IndexedField<TestIndexedData, Integer> INTEGER_FIELD =
      IndexedField.<TestIndexedData>integerBuilder("IntegerTestField").build(getter(), setter());

  public static final IndexedField<TestIndexedData, Integer>.SearchSpec INTEGER_FIELD_SPEC =
      INTEGER_FIELD.integer("integer_test");

  public static final IndexedField<TestIndexedData, Iterable<Integer>> ITERABLE_INTEGER_FIELD =
      IndexedField.<TestIndexedData>iterableIntegerBuilder("IterableIntegerTestField")
          .build(getter(), setter());

  public static final IndexedField<TestIndexedData, Iterable<Integer>>.SearchSpec
      ITERABLE_INTEGER_FIELD_SPEC = ITERABLE_INTEGER_FIELD.integer("iterable_integer_test");

  public static final IndexedField<TestIndexedData, Integer> INTEGER_RANGE_FIELD =
      IndexedField.<TestIndexedData>integerBuilder("IntegerRangeTestField")
          .build(TestIndexedFields.getter(), TestIndexedFields.setter());
  public static final IndexedField<TestIndexedData, Integer>.SearchSpec INTEGER_RANGE_FIELD_SPEC =
      INTEGER_RANGE_FIELD.range("integer_range_test");

  public static final IndexedField<TestIndexedData, Iterable<Integer>>
      ITERABLE_INTEGER_RANGE_FIELD =
          IndexedField.<TestIndexedData>iterableIntegerBuilder("IterableIntegerRangeTestField")
              .build(TestIndexedFields.getter(), TestIndexedFields.setter());

  public static final IndexedField<TestIndexedData, Iterable<Integer>>.SearchSpec
      ITERABLE_INTEGER_RANGE_FIELD_SPEC =
          ITERABLE_INTEGER_RANGE_FIELD.range("iterable_integer_range_test");

  public static final IndexedField<TestIndexedData, Long> LONG_FIELD =
      IndexedField.<TestIndexedData>longBuilder("LongTestField").build(getter(), setter());

  public static final IndexedField<TestIndexedData, Long>.SearchSpec LONG_FIELD_SPEC =
      LONG_FIELD.longSearch("long_test");

  public static final IndexedField<TestIndexedData, Iterable<Long>> ITERABLE_LONG_FIELD =
      IndexedField.<TestIndexedData, Iterable<Long>>builder(
              "IterableLongTestField", IndexedField.ITERABLE_LONG_TYPE)
          .build(TestIndexedFields.getter(), TestIndexedFields.setter());

  public static final IndexedField<TestIndexedData, Iterable<Long>>.SearchSpec
      ITERABLE_LONG_FIELD_SPEC = ITERABLE_LONG_FIELD.longSearch("iterable_long_test");

  public static final IndexedField<TestIndexedData, Long> LONG_RANGE_FIELD =
      IndexedField.<TestIndexedData>longBuilder("LongRangeTestField")
          .build(TestIndexedFields.getter(), TestIndexedFields.setter());

  public static final IndexedField<TestIndexedData, Long>.SearchSpec LONG_RANGE_FIELD_SPEC =
      LONG_RANGE_FIELD.range("long_range_test");

  public static final IndexedField<TestIndexedData, Iterable<Long>> ITERABLE_LONG_RANGE_FIELD =
      IndexedField.<TestIndexedData, Iterable<Long>>builder(
              "IterableLongRangeTestField", IndexedField.ITERABLE_LONG_TYPE)
          .build(TestIndexedFields.getter(), TestIndexedFields.setter());

  public static final IndexedField<TestIndexedData, Iterable<Long>>.SearchSpec
      ITERABLE_LONG_RANGE_FIELD_SPEC = ITERABLE_LONG_RANGE_FIELD.range("iterable_long_range_test");

  public static final IndexedField<TestIndexedData, Timestamp> TIMESTAMP_FIELD =
      IndexedField.<TestIndexedData>timestampBuilder("TimestampTestField")
          .build(getter(), setter());

  public static final IndexedField<TestIndexedData, Timestamp>.SearchSpec TIMESTAMP_FIELD_SPEC =
      TIMESTAMP_FIELD.timestamp("timestamp_test");

  public static final IndexedField<TestIndexedData, Iterable<String>> ITERABLE_STRING_FIELD =
      IndexedField.<TestIndexedData>iterableStringBuilder("IterableStringTestField")
          .build(getter(), setter());

  public static final IndexedField<TestIndexedData, Iterable<String>>.SearchSpec
      ITERABLE_STRING_FIELD_SPEC = ITERABLE_STRING_FIELD.fullText("iterable_test_string");

  public static final IndexedField<TestIndexedData, String> STRING_FIELD =
      IndexedField.<TestIndexedData>stringBuilder("StringTestField").build(getter(), setter());

  public static final IndexedField<TestIndexedData, String>.SearchSpec STRING_FIELD_SPEC =
      STRING_FIELD.fullText("string_test");

  public static final IndexedField<TestIndexedData, String>.SearchSpec PREFIX_STRING_FIELD_SPEC =
      STRING_FIELD.prefix("prefix_string_test");

  public static final IndexedField<TestIndexedData, String>.SearchSpec EXACT_STRING_FIELD_SPEC =
      STRING_FIELD.exact("exact_string_test");

  public static final IndexedField<TestIndexedData, Iterable<byte[]>> ITERABLE_STORED_BYTE_FIELD =
      IndexedField.<TestIndexedData>iterableByteArrayBuilder("IterableByteTestField")
          .stored()
          .build(getter(), setter());

  public static final IndexedField<TestIndexedData, Iterable<byte[]>>.SearchSpec
      ITERABLE_STORED_BYTE_SPEC = ITERABLE_STORED_BYTE_FIELD.storedOnly("iterable_byte_test");

  public static final IndexedField<TestIndexedData, byte[]> STORED_BYTE_FIELD =
      IndexedField.<TestIndexedData>byteArrayBuilder("ByteTestField")
          .stored()
          .build(getter(), setter());

  public static final IndexedField<TestIndexedData, byte[]>.SearchSpec STORED_BYTE_SPEC =
      STORED_BYTE_FIELD.storedOnly("byte_test");

  public static final IndexedField<TestIndexedData, Entities.Change> STORED_PROTO_FIELD =
      IndexedField.<TestIndexedData, Entities.Change>builder(
              "TestChange",
              new TypeToken<Entities.Change>() {
                private static final long serialVersionUID = 1L;
              })
          .stored()
          .build(getter(), setter(), ChangeProtoConverter.INSTANCE);

  public static final IndexedField<TestIndexedData, Entities.Change>.SearchSpec
      STORED_PROTO_FIELD_SPEC = STORED_PROTO_FIELD.storedOnly("test_change");

  public static final IndexedField<TestIndexedData, Iterable<Entities.Change>>
      ITERABLE_STORED_PROTO_FIELD =
          IndexedField.<TestIndexedData, Iterable<Entities.Change>>builder(
                  "IterableTestChange",
                  new TypeToken<Iterable<Entities.Change>>() {
                    private static final long serialVersionUID = 1L;
                  })
              .stored()
              .build(getter(), setter(), ChangeProtoConverter.INSTANCE);

  public static final IndexedField<TestIndexedData, Iterable<Entities.Change>>.SearchSpec
      ITERABLE_PROTO_FIELD_SPEC = ITERABLE_STORED_PROTO_FIELD.storedOnly("iterable_test_change");

  public static Change createChangeProto(int id) {
    return Entities.Change.newBuilder()
        .setChangeId(Change_Id.newBuilder().setId(id).build())
        .build();
  }

  private TestIndexedFields() {}
}
