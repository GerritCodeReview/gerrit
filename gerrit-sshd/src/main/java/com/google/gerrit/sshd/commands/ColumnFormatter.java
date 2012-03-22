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

package com.google.gerrit.sshd.commands;

import org.apache.commons.lang.StringUtils;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple output formatter for column-oriented data, writing its output to
 * a {@link java.io.PrintWriter} object. Handles escaping of the column
 * data so that the resulting output is reasonably safe and machine readable.
 */
public class ColumnFormatter {
  /**
   * An array of the string representations that should be used in place
   * of the non-printable characters in the beginning of the ASCII table.
   * The index of each element in the array corresponds to its ASCII value,
   * i.e. the string representation of ASCII 0 is found in the first element
   * of this array.
   */
  public static String[] NON_PRINTABLE_CHARS =
    { "\\x00", "\\x01", "\\x02", "\\x03", "\\x04", "\\x05", "\\x06", "\\a",
      "\\b",   "\\t",   "\\n",   "\\v",   "\\f",   "\\r",   "\\x0e", "\\x0f",
      "\\x10", "\\x11", "\\x12", "\\x13", "\\x14", "\\x15", "\\x16", "\\x17",
      "\\x18", "\\x19", "\\x1a", "\\x1b", "\\x1c", "\\x1d", "\\x1e", "\\x1f" };

  private List<String> columns;
  private String columnSeparator;
  private final PrintWriter out;

  public ColumnFormatter(final PrintWriter out, String columnSeparator) {
    this.out = out;
    this.columnSeparator = columnSeparator;
    this.columns = new LinkedList<String>();
  }

  /**
   * Adds a text string as a new column in the current line of output,
   * taking care of escaping as necessary.
   *
   * @param content the string to add.
   */
  public void addColumn(String content) {
    columns.add(escapeString(content));
  }

  /*
   * Escapes the input string so that all non-printable characters (0x00-0x1f)
   * are represented as a hex escape (\x00, \x01, ...) or as a C-style escape
   * sequence (\a, \b, \t, \n, \v, \f, or \r). Backslashes in the input string
   * are doubled.
   */
  public static String escapeString(String str) {
    // Allocate a buffer big enough to cover the case with a string needed
    // very excessive escaping without having to reallocate the buffer.
    StringBuffer result = new StringBuffer(3 * str.length());

    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) < NON_PRINTABLE_CHARS.length) {
        result.append(NON_PRINTABLE_CHARS[str.charAt(i)]);
      } else if (str.charAt(i) == '\\') {
          result.append("\\\\");
      } else {
        result.append(str.charAt(i));
      }
    }
    return result.toString();
  }

  /*
   * Finishes the output by flushing the current line, writing any
   * footer text etc.
   */
  public void finish() {
    nextLine();
  }

  public String getColumnSeparator() {
    return columnSeparator;
  }

  /*
   * Flushes the current line of output and makes the formatter ready to
   * start receiving new column data for a new line (or end-of-file).
   * If the current line is empty nothing is done, i.e. consecutive calls
   * to this method without intervening calls to {@link addColumn} will
   * be squashed.
   */
  public void nextLine() {
    if (!columns.isEmpty()) {
      out.print(StringUtils.join(columns, columnSeparator));

      // Ensure consistent behavior regardless of the default line separator
      // for the platform where Gerrit is run.
      out.print('\n');
      columns.clear();
    }
  }
}
