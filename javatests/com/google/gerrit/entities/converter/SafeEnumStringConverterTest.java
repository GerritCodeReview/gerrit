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

package com.google.gerrit.entities.converter;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.client.DefaultEnum;
import org.junit.Test;

/** Test for {@link com.google.gerrit.entities.converter.SafeEnumStringConverter}. */
public class SafeEnumStringConverterTest {
  private enum TestEnum implements DefaultEnum<TestEnum> {
    A,
    B,
    C;

    @Override
    public TestEnum getDefaultValue() {
      return B;
    }
  }

  private static final SafeEnumStringConverter<TestEnum> converter =
      new SafeEnumStringConverter<>(TestEnum.class);

  @Test
  public void convertDefinedFields() {
    assertThat(converter.convert("A")).isEqualTo(TestEnum.A);
    assertThat(converter.convert("B")).isEqualTo(TestEnum.B);
    assertThat(converter.convert("C")).isEqualTo(TestEnum.C);
  }

  @Test
  public void convertUndefinedFieldsFallbacksToDefault() {
    assertThat(converter.convert("UNKNOWN")).isEqualTo(TestEnum.B);
  }

  @Test
  public void reverseConvert() {
    assertThat(converter.reverseConvert(TestEnum.A)).isEqualTo("A");
  }
}
