// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.json;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.junit.Test;

public class JsonEnumMappingTest {

  // Use the regular, pre-configured Gson object we use throughout the Gerrit server to ensure that
  // the EnumTypeAdapterFactory is properly set up.
  private final Gson gson = OutputFormat.JSON.newGson();

  @Test
  public void nullCanBeWrittenAndParsedBack() {
    String resultingJson = gson.toJson(null, TestEnum.class);
    TestEnum value = gson.fromJson(resultingJson, TestEnum.class);
    assertThat(value).isNull();
  }

  @Test
  public void enumValueCanBeWrittenAndParsedBack() {
    String resultingJson = gson.toJson(TestEnum.ONE, TestEnum.class);
    TestEnum value = gson.fromJson(resultingJson, TestEnum.class);
    assertThat(value).isEqualTo(TestEnum.ONE);
  }

  @Test
  public void enumValueCanBeParsed() {
    TestData data = gson.fromJson("{\"value\":\"ONE\"}", TestData.class);
    assertThat(data.value).isEqualTo(TestEnum.ONE);
  }

  @Test
  public void mixedCaseEnumValueIsRejectedOnParse() {
    assertThrows(
        JsonSyntaxException.class, () -> gson.fromJson("{\"value\":\"oNe\"}", TestData.class));
  }

  @Test
  public void lowerCaseEnumValueIsRejectedOnParse() {
    assertThrows(
        JsonSyntaxException.class, () -> gson.fromJson("{\"value\":\"one\"}", TestData.class));
  }

  @Test
  public void notExistingEnumValueIsRejectedOnParse() {
    assertThrows(
        JsonSyntaxException.class, () -> gson.fromJson("{\"value\":\"FOUR\"}", TestData.class));
  }

  @Test
  public void emptyEnumValueIsRejectedOnParse() {
    assertThrows(
        JsonSyntaxException.class, () -> gson.fromJson("{\"value\":\"\"}", TestData.class));
  }

  private static class TestData {
    TestEnum value;
  }

  private enum TestEnum {
    ONE,
    TWO
  }
}
