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
import net.codemirror.lib.Pos;

/** An iterator for intraline edits */
class EditIterator {
  private final JsArrayString lines;
  private final int startLine;
  private int line;
  private int pos;

  EditIterator(JsArrayString lineArray, int start) {
    lines = lineArray;
    startLine = start;
  }

  Pos advance(int numOfChar) {
    numOfChar = adjustForNegativeDelta(numOfChar);

    while (line < lines.length()) {
      int len = lines.get(line).length() - pos + 1; // + 1 for LF
      if (numOfChar < len) {
        Pos at = Pos.create(startLine + line, numOfChar + pos);
        pos += numOfChar;
        return at;
      }

      numOfChar -= len;
      line++;
      pos = 0;

      if (numOfChar == 0) {
        return Pos.create(startLine + line, 0);
      }
    }

    throw new IllegalStateException("EditIterator index out of bounds");
  }

  private int adjustForNegativeDelta(int n) {
    while (n < 0) {
      if (-n <= pos) {
        pos += n;
        return 0;
      }

      n += pos;
      line--;
      if (line < 0) {
        throw new IllegalStateException("EditIterator index out of bounds");
      }
      pos = lines.get(line).length() + 1;
    }
    return n;
  }
}
