// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import com.google.gwt.core.client.JsArrayString;

import net.codemirror.lib.LineCharacter;

/** An iterator for intraline edits */
class EditIterator {
  private final JsArrayString lines;
  private final int startLine;
  private int currLineIndex;
  private int currLineOffset;

  EditIterator(JsArrayString lineArray, int start) {
    lines = lineArray;
    startLine = start;
  }

  LineCharacter advance(int numOfChar) {
    while (currLineIndex < lines.length()) {
      int lengthWithNewline =
          lines.get(currLineIndex).length() - currLineOffset + 1;
      if (numOfChar < lengthWithNewline) {
        LineCharacter at = LineCharacter.create(
            startLine + currLineIndex,
            numOfChar + currLineOffset);
        currLineOffset += numOfChar;
        return at;
      }
      numOfChar -= lengthWithNewline;
      advanceLine();
      if (numOfChar == 0) {
        return LineCharacter.create(startLine + currLineIndex, 0);
      }
    }
    throw new IllegalStateException("EditIterator index out of bound");
  }

  private void advanceLine() {
    currLineIndex++;
    currLineOffset = 0;
  }
}