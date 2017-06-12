// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StringUtilTest {
  /** Test the boundary condition that the first character of a string should be escaped. */
  @Test
  public void testEscapeFirstChar() {
    assertEquals(StringUtil.escapeString("\tLeading tab"), "\\tLeading tab");
  }

  /** Test the boundary condition that the last character of a string should be escaped. */
  @Test
  public void testEscapeLastChar() {
    assertEquals(StringUtil.escapeString("Trailing tab\t"), "Trailing tab\\t");
  }

  /** Test that various forms of input strings are escaped (or left as-is) in the expected way. */
  @Test
  public void testEscapeString() {
    final String[] testPairs = {
      "", "",
      "plain string", "plain string",
      "string with \"quotes\"", "string with \"quotes\"",
      "string with 'quotes'", "string with 'quotes'",
      "string with 'quotes'", "string with 'quotes'",
      "C:\\Program Files\\MyProgram", "C:\\\\Program Files\\\\MyProgram",
      "string\nwith\nnewlines", "string\\nwith\\nnewlines",
      "string\twith\ttabs", "string\\twith\\ttabs",
    };
    for (int i = 0; i < testPairs.length; i += 2) {
      assertEquals(StringUtil.escapeString(testPairs[i]), testPairs[i + 1]);
    }
  }
}
