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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.IndexedField.SearchSpec;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.StoredValue;
import com.google.gerrit.index.testing.FakeStoredValue;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@link com.google.gerrit.index.IndexedField} */
@RunWith(Parameterized.class)
public class IndexedFieldTest {

  static class TestIndexedData {

    private Object testField;

    public Object getTestField() {
      return testField;
    }

    public void setTestField(Object testField) {
      this.testField = testField;
    }
  }

  static IndexedField<TestIndexedData, Integer> INTEGER_FIELD =
      IndexedField.<TestIndexedData>integerBuilder("TestField")
          .build(
              testData -> (Integer) testData.getTestField(),
              (testIndexedData, value) -> testIndexedData.setTestField(value));

  static SearchSpec INTEGER_FIELD_SPEC = INTEGER_FIELD.integer("test");

  static IndexedField<TestIndexedData, Iterable<String>> ITERABLE_STRING_FIELD =
      IndexedField.<TestIndexedData>iterableStringBuilder("TestField")
          .build(
              testData -> (Iterable<String>) testData.getTestField(),
              (testIndexedData, value) -> testIndexedData.setTestField(value));

  static SearchSpec ITERABLE_STRING_FIELD_SPEC = ITERABLE_STRING_FIELD.fullText("test");

  static IndexedField<TestIndexedData, Iterable<byte[]>> ITERABLE_STORED_BYTE_FIELD =
      IndexedField.<TestIndexedData>iterableByteArrayBuilder("TestField")
          .stored()
          .build(
              testData -> (Iterable<byte[]>) testData.getTestField(),
              (testIndexedData, value) -> testIndexedData.setTestField(value));

  static SearchSpec ITERABLE_STORED_BYTE_SPEC = ITERABLE_STORED_BYTE_FIELD.storedOnly("test");

  static IndexedField<TestIndexedData, byte[]> STORED_BYTE_FIELD =
      IndexedField.<TestIndexedData>byteArrayBuilder("TestField")
          .stored()
          .build(
              testData -> (byte[]) testData.getTestField(),
              (testIndexedData, value) -> testIndexedData.setTestField(value));

  static SearchSpec STORED_BYTE_SPEC = STORED_BYTE_FIELD.storedOnly("test");

  @Parameter(0)
  public SchemaField schemaField;

  @Parameter(1)
  public Object docValue;

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {INTEGER_FIELD_SPEC, 123456},
          {ITERABLE_STRING_FIELD_SPEC, ImmutableList.of("123456")},
          {ITERABLE_STORED_BYTE_SPEC, ImmutableList.of("123456".getBytes(StandardCharsets.UTF_8))},
          {STORED_BYTE_SPEC, "123456".getBytes(StandardCharsets.UTF_8)},
        });
  }

  @Test
  public void testSetIfPossible() {
    StoredValue storedValue = new FakeStoredValue(docValue);
    TestIndexedData testIndexedData = new TestIndexedData();
    schemaField.setIfPossible(testIndexedData, storedValue);
    assertThat(testIndexedData.getTestField()).isEqualTo(docValue);
  }
}
