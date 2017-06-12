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

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.Assert;
import org.junit.Test;

public class ColumnFormatterTest {
  /**
   * Holds an in-memory {@link java.io.PrintWriter} object and allows comparisons of its contents to
   * a supplied string via an assert statement.
   */
  static class PrintWriterComparator {
    private PrintWriter printWriter;
    private StringWriter stringWriter;

    PrintWriterComparator() {
      stringWriter = new StringWriter();
      printWriter = new PrintWriter(stringWriter);
    }

    public void assertEquals(String str) {
      printWriter.flush();
      Assert.assertEquals(stringWriter.toString(), str);
    }

    public PrintWriter getPrintWriter() {
      return printWriter;
    }
  }

  /** Test that only lines with at least one column of text emit output. */
  @Test
  public void testEmptyLine() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter = new ColumnFormatter(comparator.getPrintWriter(), '\t');
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

  /** Test that there is no output if no columns are ever added. */
  @Test
  public void testEmptyOutput() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter = new ColumnFormatter(comparator.getPrintWriter(), '\t');
    formatter.nextLine();
    formatter.nextLine();
    formatter.finish();
    comparator.assertEquals("");
  }

  /**
   * Test that there is no output (nor any exceptions) if we finalize the output immediately after
   * the creation of the {@link ColumnFormatter}.
   */
  @Test
  public void testNoNextLine() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter = new ColumnFormatter(comparator.getPrintWriter(), '\t');
    formatter.finish();
    comparator.assertEquals("");
  }

  /**
   * Test that the text in added columns is escaped while the column separator (which of course
   * shouldn't be escaped) is left alone.
   */
  @Test
  public void testEscapingTakesPlace() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter = new ColumnFormatter(comparator.getPrintWriter(), '\t');
    formatter.addColumn("foo");
    formatter.addColumn("\tan indented multi-line\ntext");
    formatter.nextLine();
    formatter.finish();
    comparator.assertEquals("foo\t\\tan indented multi-line\\ntext\n");
  }

  /**
   * Test that we get the correct output with multi-line input where the number of columns in each
   * line varies.
   */
  @Test
  public void testMultiLineDifferentColumnCount() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter = new ColumnFormatter(comparator.getPrintWriter(), '\t');
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

  /** Test that we get the correct output with a single column of input. */
  @Test
  public void testOneColumn() {
    final PrintWriterComparator comparator = new PrintWriterComparator();
    final ColumnFormatter formatter = new ColumnFormatter(comparator.getPrintWriter(), '\t');
    formatter.addColumn("foo");
    formatter.nextLine();
    formatter.finish();
    comparator.assertEquals("foo\n");
  }
}
