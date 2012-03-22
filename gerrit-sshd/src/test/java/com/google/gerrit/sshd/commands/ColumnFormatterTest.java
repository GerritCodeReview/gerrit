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

import junit.framework.TestCase;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ColumnFormatterTest extends TestCase {
  /**
   * Holds an in-memory {@link java.io.PrintWriter} object and allows
   * comparisons of its contents to a supplied string via an assert statement.
   */
  class PrintWriterComparator {

    private PrintWriter printWriter;
    private StringWriter stringWriter;

    public PrintWriterComparator() {
      stringWriter = new StringWriter();
      printWriter = new PrintWriter(stringWriter);
    }

    public void assertEquals(String str) {
      printWriter.flush();
      TestCase.assertEquals(stringWriter.toString(), str);
    }

    public PrintWriter getPrintWriter() {
      return printWriter;
    }

  }

  /**
   * Test that only lines with at least one column of text emit output.
   */
  public void testEmptyLine() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter =
        new ColumnFormatter(comparator.getPrintWriter(), "\t");
    formatter.addColumn("foo");
    formatter.addColumn("bar");
    formatter.nextLine();
    formatter.nextLine();
    formatter.nextLine();
    formatter.addColumn("foo");
    formatter.addColumn("bar");
    formatter.finish();
    comparator.assertEquals("foo\tbar\nfoo\tbar\n");
  }

  /**
   * Test that there is no output if no columns are ever added.
   */
  public void testEmptyOutput() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter =
        new ColumnFormatter(comparator.getPrintWriter(), "\t");
    formatter.nextLine();
    formatter.finish();
    comparator.assertEquals("");
  }

  /**
   * Test that there is no output (nor exceptions) if we finalize the output
   * immediately after the creation of the {@link ColumnFormatter} object.
   */
  public void testNoNextLine() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter =
        new ColumnFormatter(comparator.getPrintWriter(), "\t");
    formatter.finish();
    comparator.assertEquals("");
  }

  /**
   * Test that all non-printable characters in the
   * ColumnFormatter.NON_PRINTABLE_CHARS table are properly escaped.
   */
  public void testEscapeNonPrintables() {
    for (int i = 0; i < ColumnFormatter.NON_PRINTABLE_CHARS.length; i++) {
      assertEquals(
          ColumnFormatter.escapeString(new String(Character.toChars(i))),
          ColumnFormatter.NON_PRINTABLE_CHARS[i]);
    }
  }

  /**
   * Test that various forms of input strings are escaped (or left as-is)
   * in the expected way.
   */
  public void testEscapeString() {
    final String[] testPairs =
      { "", "",
        "plain string", "plain string",
        "string with \"quotes\"", "string with \"quotes\"",
        "C:\\Program Files\\MyProgram", "C:\\\\Program Files\\\\MyProgram",
        "string\nwith\nnewlines", "string\\nwith\\nnewlines",
        "string\twith\ttabs", "string\\twith\\ttabs" };
    for (int i = 0; i < testPairs.length; i += 2) {
      assertEquals(ColumnFormatter.escapeString(testPairs[i]), testPairs[i + 1]);
    }
  }

  /**
   * Test that the text in added columns is escaped while the column separator
   * (which of course shouldn't be escaped) is left alone.
   */
  public void testEscapingTakesPlace() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter =
        new ColumnFormatter(comparator.getPrintWriter(), "\t");
    formatter.addColumn("foo");
    formatter.addColumn(
        "\tan indented multi-line\ntext with a null character: \u0000");
    formatter.nextLine();
    formatter.finish();
    comparator.assertEquals(
        "foo\t\\tan indented multi-line\\ntext with a null character: \\x00\n");
  }

  /**
   * Test that we get the correct output with multi-line input where the number
   * of columns in each lines is different.
   */
  public void testMultiLineDifferentColumnCount() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter =
        new ColumnFormatter(comparator.getPrintWriter(), "\t");
    formatter.addColumn("foo");
    formatter.addColumn("bar");
    formatter.addColumn("baz");
    formatter.nextLine();
    formatter.addColumn("foo");
    formatter.addColumn("bar");
    formatter.nextLine();
    formatter.finish();
    comparator.assertEquals("foo\tbar\tbaz\nfoo\tbar\n");
  }

  /**
   * Test that we get the correct output with a single column of input.
   */
  public void testOneColumn() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter =
        new ColumnFormatter(comparator.getPrintWriter(), "\t");
    formatter.addColumn("foo");
    formatter.nextLine();
    formatter.finish();
    comparator.assertEquals("foo\n");
  }
}
