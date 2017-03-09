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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class LineIdentifierTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void lineNumberMustBePositive() {
    LineIdentifier lineIdentifier = new LineIdentifier("First line\nSecond line");
    expectedException.expect(StringIndexOutOfBoundsException.class);
    expectedException.expectMessage("positive");
    lineIdentifier.getStartIndexOfLine(0);
  }

  @Test
  public void lineNumberMustIndicateAnAvailableLine() {
    LineIdentifier lineIdentifier = new LineIdentifier("First line\nSecond line");
    expectedException.expect(StringIndexOutOfBoundsException.class);
    expectedException.expectMessage("Line 3 isn't available");
    lineIdentifier.getStartIndexOfLine(3);
  }

  @Test
  public void startIndexOfFirstLineIsRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n1234567");
    int startIndex = lineIdentifier.getStartIndexOfLine(1);
    assertThat(startIndex).isEqualTo(0);
  }

  @Test
  public void lengthOfFirstLineIsCorrect() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n1234567");
    int lineLength = lineIdentifier.getLengthOfLine(1);
    assertThat(lineLength).isEqualTo(8);
  }

  @Test
  public void startIndexOfSecondLineIsRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n1234567");
    int startIndex = lineIdentifier.getStartIndexOfLine(2);
    assertThat(startIndex).isEqualTo(9);
  }

  @Test
  public void lengthOfSecondLineIsCorrect() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n1234567");
    int lineLength = lineIdentifier.getLengthOfLine(2);
    assertThat(lineLength).isEqualTo(3);
  }

  @Test
  public void startIndexOfLastLineIsRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n1234567");
    int startIndex = lineIdentifier.getStartIndexOfLine(3);
    assertThat(startIndex).isEqualTo(13);
  }

  @Test
  public void lengthOfLastLineIsCorrect() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n1234567");
    int lineLength = lineIdentifier.getLengthOfLine(3);
    assertThat(lineLength).isEqualTo(7);
  }

  @Test
  public void emptyFirstLineIsRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("\n123\n1234567");
    int startIndex = lineIdentifier.getStartIndexOfLine(1);
    assertThat(startIndex).isEqualTo(0);
  }

  @Test
  public void lengthOfEmptyFirstLineIsCorrect() {
    LineIdentifier lineIdentifier = new LineIdentifier("\n123\n1234567");
    int lineLength = lineIdentifier.getLengthOfLine(1);
    assertThat(lineLength).isEqualTo(0);
  }

  @Test
  public void emptyIntermediaryLineIsRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n\n1234567");
    int startIndex = lineIdentifier.getStartIndexOfLine(2);
    assertThat(startIndex).isEqualTo(9);
  }

  @Test
  public void lengthOfEmptyIntermediaryLineIsCorrect() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n\n1234567");
    int lineLength = lineIdentifier.getLengthOfLine(2);
    assertThat(lineLength).isEqualTo(0);
  }

  @Test
  public void lineAfterIntermediaryLineIsRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n\n1234567");
    int startIndex = lineIdentifier.getStartIndexOfLine(3);
    assertThat(startIndex).isEqualTo(10);
  }

  @Test
  public void emptyLastLineIsRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n");
    int startIndex = lineIdentifier.getStartIndexOfLine(3);
    assertThat(startIndex).isEqualTo(13);
  }

  @Test
  public void lengthOfEmptyLastLineIsCorrect() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n");
    int lineLength = lineIdentifier.getLengthOfLine(3);
    assertThat(lineLength).isEqualTo(0);
  }

  @Test
  public void startIndexOfSingleLineIsRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678");
    int startIndex = lineIdentifier.getStartIndexOfLine(1);
    assertThat(startIndex).isEqualTo(0);
  }

  @Test
  public void lengthOfSingleLineIsCorrect() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678");
    int lineLength = lineIdentifier.getLengthOfLine(1);
    assertThat(lineLength).isEqualTo(8);
  }

  @Test
  public void startIndexOfSingleEmptyLineIsRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("");
    int startIndex = lineIdentifier.getStartIndexOfLine(1);
    assertThat(startIndex).isEqualTo(0);
  }

  @Test
  public void lengthOfSingleEmptyLineIsCorrect() {
    LineIdentifier lineIdentifier = new LineIdentifier("");
    int lineLength = lineIdentifier.getLengthOfLine(1);
    assertThat(lineLength).isEqualTo(0);
  }

  @Test
  public void lookingUpSubsequentLinesIsPossible() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n1234567\n12");

    int firstLineStartIndex = lineIdentifier.getStartIndexOfLine(1);
    assertThat(firstLineStartIndex).isEqualTo(0);

    int secondLineStartIndex = lineIdentifier.getStartIndexOfLine(2);
    assertThat(secondLineStartIndex).isEqualTo(9);
  }

  @Test
  public void lookingUpNotSubsequentLinesInAscendingOrderIsPossible() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n1234567\n12");

    int firstLineStartIndex = lineIdentifier.getStartIndexOfLine(1);
    assertThat(firstLineStartIndex).isEqualTo(0);

    int fourthLineStartIndex = lineIdentifier.getStartIndexOfLine(4);
    assertThat(fourthLineStartIndex).isEqualTo(21);
  }

  @Test
  public void lookingUpNotSubsequentLinesInDescendingOrderIsPossible() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\n123\n1234567\n12");

    int fourthLineStartIndex = lineIdentifier.getStartIndexOfLine(4);
    assertThat(fourthLineStartIndex).isEqualTo(21);

    int secondLineStartIndex = lineIdentifier.getStartIndexOfLine(2);
    assertThat(secondLineStartIndex).isEqualTo(9);
  }

  @Test
  public void linesSeparatedByOnlyCarriageReturnAreRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\r123\r12");
    int startIndex = lineIdentifier.getStartIndexOfLine(2);
    assertThat(startIndex).isEqualTo(9);
  }

  @Test
  public void lengthOfLinesSeparatedByOnlyCarriageReturnIsCorrect() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\r123\r12");
    int lineLength = lineIdentifier.getLengthOfLine(2);
    assertThat(lineLength).isEqualTo(3);
  }

  @Test
  public void linesSeparatedByLineFeedAndCarriageReturnAreRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\r\n123\r\n12");
    int startIndex = lineIdentifier.getStartIndexOfLine(2);
    assertThat(startIndex).isEqualTo(10);
  }

  @Test
  public void lengthOfLinesSeparatedByLineFeedAndCarriageReturnIsCorrect() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\r\n123\r\n12");
    int lineLength = lineIdentifier.getLengthOfLine(2);
    assertThat(lineLength).isEqualTo(3);
  }

  @Test
  public void linesSeparatedByMixtureOfCarriageReturnAndLineFeedAreRecognized() {
    LineIdentifier lineIdentifier = new LineIdentifier("12345678\r123\r\n12\n123456\r\n1234");
    int startIndex = lineIdentifier.getStartIndexOfLine(5);
    assertThat(startIndex).isEqualTo(25);
  }

  @Test
  public void blanksAreNotInterpretedAsLineSeparators() {
    LineIdentifier lineIdentifier = new LineIdentifier("1 2345678\n123\n12");
    int startIndex = lineIdentifier.getStartIndexOfLine(2);
    assertThat(startIndex).isEqualTo(10);
  }

  @Test
  public void tabsAreNotInterpretedAsLineSeparators() {
    LineIdentifier lineIdentifier = new LineIdentifier("123\t45678\n123\n12");
    int startIndex = lineIdentifier.getStartIndexOfLine(2);
    assertThat(startIndex).isEqualTo(10);
  }
}
