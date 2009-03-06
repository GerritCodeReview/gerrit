// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.data;

/** A line of a file in a side-by-side view. */
public class SideBySideLine extends LineWithComments {
  public static enum Type {
    DELETE, INSERT, EQUAL;
  }

  protected int lineNumber;
  protected SideBySideLine.Type type;
  protected String text;

  protected SideBySideLine() {
  }

  public SideBySideLine(final int line, final SideBySideLine.Type t,
      final String s) {
    lineNumber = line;
    type = t;
    text = s;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public SideBySideLine.Type getType() {
    return type;
  }

  public String getText() {
    return text;
  }
}
