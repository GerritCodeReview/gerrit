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

public class StringUtil {
  /**
   * An array of the string representations that should be used in place of the non-printable
   * characters in the beginning of the ASCII table when escaping a string. The index of each
   * element in the array corresponds to its ASCII value, i.e. the string representation of ASCII 0
   * is found in the first element of this array.
   */
  private static final String[] NON_PRINTABLE_CHARS = {
    "\\x00", "\\x01", "\\x02", "\\x03", "\\x04", "\\x05", "\\x06", "\\a",
    "\\b", "\\t", "\\n", "\\v", "\\f", "\\r", "\\x0e", "\\x0f",
    "\\x10", "\\x11", "\\x12", "\\x13", "\\x14", "\\x15", "\\x16", "\\x17",
    "\\x18", "\\x19", "\\x1a", "\\x1b", "\\x1c", "\\x1d", "\\x1e", "\\x1f",
  };

  /**
   * Escapes the input string so that all non-printable characters (0x00-0x1f) are represented as a
   * hex escape (\x00, \x01, ...) or as a C-style escape sequence (\a, \b, \t, \n, \v, \f, or \r).
   * Backslashes in the input string are doubled (\\).
   */
  public static String escapeString(String str) {
    // Allocate a buffer big enough to cover the case with a string needed
    // very excessive escaping without having to reallocate the buffer.
    final StringBuilder result = new StringBuilder(3 * str.length());

    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c < NON_PRINTABLE_CHARS.length) {
        result.append(NON_PRINTABLE_CHARS[c]);
      } else if (c == '\\') {
        result.append("\\\\");
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }
}
