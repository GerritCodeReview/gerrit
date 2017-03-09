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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An identifier of lines in a string. Lines are sequences of characters which are separated by the
 * line feed character, the carriage return character, or the carriage return followed by the line
 * feed character. If data for several lines is requested, calls which are ordered according to
 * ascending line numbers are the most efficient.
 */
class LineIdentifier {

  private static final Pattern LINE_SEPARATOR_PATTERN = Pattern.compile("(\r\n)|(\n)|(\r)");
  private final Matcher lineSeparatorMatcher;

  private int nextLineNumber;
  private int nextLineStartIndex;
  private int currentLineStartIndex;
  private int currentLineEndIndex;

  LineIdentifier(String string) {
    checkNotNull(string);
    lineSeparatorMatcher = LINE_SEPARATOR_PATTERN.matcher(string);
    reset();
  }

  /**
   * Returns the start index of the indicated line within the given string. Start indices are
   * zero-based while line numbers are one-based.
   *
   * <p><b>Note:</b> Requesting data for several lines is more efficient if those calls occur with
   * increasing line number.
   *
   * @param lineNumber the line whose start index should be determined
   * @return the start index of the line
   * @throws StringIndexOutOfBoundsException if the line number is negative, zero or greater than
   *     the identified number of lines
   */
  public int getStartIndexOfLine(int lineNumber) {
    findLine(lineNumber);
    return currentLineStartIndex;
  }

  /**
   * Returns the length of the indicated line in the given string. The character(s) used to separate
   * lines aren't included in the count. Line numbers are one-based.
   *
   * <p><b>Note:</b> Requesting data for several lines is more efficient if those calls occur with
   * increasing line number.
   *
   * @param lineNumber the line whose length should be determined
   * @return the length of the line
   * @throws StringIndexOutOfBoundsException if the line number is negative, zero or greater than
   *     the identified number of lines
   */
  public int getLengthOfLine(int lineNumber) {
    findLine(lineNumber);
    return currentLineEndIndex - currentLineStartIndex;
  }

  private void findLine(int targetLineNumber) {
    if (targetLineNumber <= 0) {
      throw new StringIndexOutOfBoundsException("Line number must be positive");
    }
    if (targetLineNumber < nextLineNumber) {
      reset();
    }
    while (nextLineNumber < targetLineNumber + 1 && lineSeparatorMatcher.find()) {
      currentLineStartIndex = nextLineStartIndex;
      currentLineEndIndex = lineSeparatorMatcher.start();
      nextLineStartIndex = lineSeparatorMatcher.end();
      nextLineNumber++;
    }

    // End of string
    if (nextLineNumber == targetLineNumber) {
      currentLineStartIndex = nextLineStartIndex;
      currentLineEndIndex = lineSeparatorMatcher.regionEnd();
    }
    if (nextLineNumber < targetLineNumber) {
      throw new StringIndexOutOfBoundsException(
          String.format("Line %d isn't available", targetLineNumber));
    }
  }

  private void reset() {
    nextLineNumber = 1;
    nextLineStartIndex = 0;
    currentLineStartIndex = 0;
    currentLineEndIndex = 0;
    lineSeparatorMatcher.reset();
  }
}
