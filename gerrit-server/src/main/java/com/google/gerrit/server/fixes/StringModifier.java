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

/**
 * A modifier of a string. It allows to replace multiple parts of a string by indicating those parts
 * with indices based on the unmodified string. There is one limitation though: replacements which
 * affect lower indices of the string must be specified before replacements for higher indices.
 */
class StringModifier {

  private final StringBuilder stringBuilder;

  private int characterShift = 0;
  private int previousEndOffset = Integer.MIN_VALUE;

  StringModifier(String string) {
    checkNotNull(string, "string must not be null");
    stringBuilder = new StringBuilder(string);
  }

  /**
   * Replaces part of the string with another content. When called multiple times, the calls must be
   * ordered according to increasing start indices. Overlapping replacement regions aren't
   * supported.
   *
   * @param startIndex the beginning index in the unmodified string (inclusive)
   * @param endIndex the ending index in the unmodified string (exclusive)
   * @param replacement the string which should be used instead of the original content
   * @throws StringIndexOutOfBoundsException if the start index is smaller than the end index of a
   *     previous call of this method
   */
  public void replace(int startIndex, int endIndex, String replacement) {
    checkNotNull(replacement, "replacement string must not be null");
    if (previousEndOffset > startIndex) {
      throw new StringIndexOutOfBoundsException(
          String.format(
              "Not supported to replace the content starting at index %s after previous "
                  + "replacement which ended at index %s",
              startIndex, previousEndOffset));
    }
    int shiftedStartIndex = startIndex + characterShift;
    int shiftedEndIndex = endIndex + characterShift;
    if (shiftedEndIndex > stringBuilder.length()) {
      throw new StringIndexOutOfBoundsException(
          String.format("end %s > length %s", shiftedEndIndex, stringBuilder.length()));
    }
    stringBuilder.replace(shiftedStartIndex, shiftedEndIndex, replacement);

    int replacedContentLength = endIndex - startIndex;
    characterShift += replacement.length() - replacedContentLength;
    previousEndOffset = endIndex;
  }

  /**
   * Returns the modified string including all specified replacements.
   *
   * @return the modified string
   */
  public String getResult() {
    return stringBuilder.toString();
  }
}
