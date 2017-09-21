// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.fixes;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class StringModifierTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  private final String originalString = "This is the original, unmodified string.";
  private StringModifier stringModifier;

  @Before
  public void setUp() {
    stringModifier = new StringModifier(originalString);
  }

  @Test
  public void singlePartIsReplaced() {
    stringModifier.replace(0, 11, "An");
    String modifiedString = stringModifier.getResult();
    assertThat(modifiedString).isEqualTo("An original, unmodified string.");
  }

  @Test
  public void twoPartsCanBeReplacedWithInsertionFirst() {
    stringModifier.replace(5, 5, "string ");
    stringModifier.replace(8, 39, "a modified version");
    String modifiedString = stringModifier.getResult();
    assertThat(modifiedString).isEqualTo("This string is a modified version.");
  }

  @Test
  public void twoPartsCanBeReplacedWithDeletionFirst() {
    stringModifier.replace(0, 8, "");
    stringModifier.replace(12, 32, "modified");
    String modifiedString = stringModifier.getResult();
    assertThat(modifiedString).isEqualTo("the modified string.");
  }

  @Test
  public void replacedPartsMayTouch() {
    stringModifier.replace(0, 8, "");
    stringModifier.replace(8, 32, "The modified");
    String modifiedString = stringModifier.getResult();
    assertThat(modifiedString).isEqualTo("The modified string.");
  }

  @Test
  public void replacedPartsMustNotOverlap() {
    stringModifier.replace(0, 9, "");
    expectedException.expect(StringIndexOutOfBoundsException.class);
    stringModifier.replace(8, 32, "The modified");
  }

  @Test
  public void startIndexMustNotBeGreaterThanEndIndex() {
    expectedException.expect(StringIndexOutOfBoundsException.class);
    stringModifier.replace(10, 9, "something");
  }

  @Test
  public void startIndexMustNotBeNegative() {
    expectedException.expect(StringIndexOutOfBoundsException.class);
    stringModifier.replace(-1, 9, "something");
  }

  @Test
  public void newContentCanBeInsertedAtEndOfString() {
    stringModifier.replace(
        originalString.length(), originalString.length(), " And this an addition.");
    String modifiedString = stringModifier.getResult();
    assertThat(modifiedString)
        .isEqualTo("This is the original, unmodified string. And this an addition.");
  }

  @Test
  public void startIndexMustNotBeGreaterThanLengthOfString() {
    expectedException.expect(StringIndexOutOfBoundsException.class);
    stringModifier.replace(originalString.length() + 1, originalString.length() + 1, "something");
  }

  @Test
  public void endIndexMustNotBeGreaterThanLengthOfString() {
    expectedException.expect(StringIndexOutOfBoundsException.class);
    stringModifier.replace(8, originalString.length() + 1, "something");
  }
}
