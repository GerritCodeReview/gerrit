// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

import static com.google.gerrit.extensions.common.testing.RangeSubject.assertThat;

import org.junit.Test;

public class RangeTest {

  @Test
  public void rangeOverMultipleLinesWithSmallerEndCharacterIsValid() {
    Comment.Range range = createRange(13, 31, 19, 10);
    assertThat(range).isValid();
  }

  @Test
  public void rangeInOneLineIsValid() {
    Comment.Range range = createRange(13, 2, 13, 10);
    assertThat(range).isValid();
  }

  @Test
  public void startPositionEqualToEndPositionIsValidRange() {
    Comment.Range range = createRange(13, 11, 13, 11);
    assertThat(range).isValid();
  }

  @Test
  public void negativeStartLineResultsInInvalidRange() {
    Comment.Range range = createRange(-1, 2, 19, 10);
    assertThat(range).isInvalid();
  }

  @Test
  public void negativeEndLineResultsInInvalidRange() {
    Comment.Range range = createRange(13, 2, -1, 10);
    assertThat(range).isInvalid();
  }

  @Test
  public void negativeStartCharacterResultsInInvalidRange() {
    Comment.Range range = createRange(13, -1, 19, 10);
    assertThat(range).isInvalid();
  }

  @Test
  public void negativeEndCharacterResultsInInvalidRange() {
    Comment.Range range = createRange(13, 2, 19, -1);
    assertThat(range).isInvalid();
  }

  @Test
  public void zeroStartLineResultsInInvalidRange() {
    Comment.Range range = createRange(0, 2, 19, 10);
    assertThat(range).isInvalid();
  }

  @Test
  public void zeroEndLineResultsInInvalidRange() {
    Comment.Range range = createRange(13, 2, 0, 10);
    assertThat(range).isInvalid();
  }

  @Test
  public void zeroStartCharacterResultsInValidRange() {
    Comment.Range range = createRange(13, 0, 19, 10);
    assertThat(range).isValid();
  }

  @Test
  public void zeroEndCharacterResultsInValidRange() {
    Comment.Range range = createRange(13, 31, 19, 0);
    assertThat(range).isValid();
  }

  @Test
  public void startLineGreaterThanEndLineResultsInInvalidRange() {
    Comment.Range range = createRange(20, 2, 19, 10);
    assertThat(range).isInvalid();
  }

  @Test
  public void startCharGreaterThanEndCharForSameLineResultsInInvalidRange() {
    Comment.Range range = createRange(13, 11, 13, 10);
    assertThat(range).isInvalid();
  }

  private Comment.Range createRange(
      int startLine, int startCharacter, int endLine, int endCharacter) {
    Comment.Range range = new Comment.Range();
    range.startLine = startLine;
    range.startCharacter = startCharacter;
    range.endLine = endLine;
    range.endCharacter = endCharacter;
    return range;
  }
}
