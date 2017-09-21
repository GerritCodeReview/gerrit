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

package com.google.gerrit.server.ioutil;

import com.google.gerrit.server.StringUtil;
import java.io.PrintWriter;

/**
 * Simple output formatter for column-oriented data, writing its output to a {@link
 * java.io.PrintWriter} object. Handles escaping of the column data so that the resulting output is
 * unambiguous and reasonably safe and machine parsable.
 */
public class ColumnFormatter {
  private char columnSeparator;
  private boolean firstColumn;
  private final PrintWriter out;

  /**
   * @param out The writer to which output should be sent.
   * @param columnSeparator A character that should serve as the separator token between columns of
   *     output. As only non-printable characters in the column text are ever escaped, the column
   *     separator must be a non-printable character if the output needs to be unambiguously parsed.
   */
  public ColumnFormatter(PrintWriter out, char columnSeparator) {
    this.out = out;
    this.columnSeparator = columnSeparator;
    this.firstColumn = true;
  }

  /**
   * Adds a text string as a new column in the current line of output, taking care of escaping as
   * necessary.
   *
   * @param content the string to add.
   */
  public void addColumn(String content) {
    if (!firstColumn) {
      out.print(columnSeparator);
    }
    out.print(StringUtil.escapeString(content));
    firstColumn = false;
  }

  /**
   * Finishes the output by flushing the current line and takes care of any other cleanup action.
   */
  public void finish() {
    nextLine();
    out.flush();
  }

  /**
   * Flushes the current line of output and makes the formatter ready to start receiving new column
   * data for a new line (or end-of-file). If the current line is empty nothing is done, i.e.
   * consecutive calls to this method without intervening calls to {@link #addColumn} will be
   * squashed.
   */
  public void nextLine() {
    if (!firstColumn) {
      out.print('\n');
      firstColumn = true;
    }
  }
}
