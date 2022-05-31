package com.google.gerrit.entities.converter;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/** Test for {@link com.google.gerrit.entities.converter.SafeEnumStringConverter}. */
public class SafeEnumStringConverterTest {
  private enum TestEnum {
    A,
    B,
    C
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
  public void convertUndefinedFields() {
    assertThat(converter.convert("UNKNOWN")).isEqualTo(TestEnum.A);
  }

  @Test
  public void reverseConvert() {
    assertThat(converter.reverseConvert(TestEnum.A)).isEqualTo("A");
  }
}
