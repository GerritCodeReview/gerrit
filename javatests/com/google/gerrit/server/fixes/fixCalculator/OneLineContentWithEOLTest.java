// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.fixes.fixCalculator;

import static com.google.gerrit.server.fixes.testing.FixResultSubject.assertThat;

import com.google.gerrit.server.fixes.FixCalculator.FixResult;
import com.google.gerrit.server.fixes.FixCalculatorTest;
import org.junit.Test;

public class OneLineContentWithEOLTest {
  @Test
  public void insertSingleLineNoEOLAtStart() throws Exception {
    FixResult fixResult = FixCalculatorTest
        .calculateFixSingleReplacement("First line\n", 1, 0, 1, 0, "Abc");
    assertThat(fixResult).text().isEqualTo("AbcFirst line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 1)
        .hasInternalEdits()
        .onlyElement().isInsert(0, 0, 3);
  }
  @Test
  public void insertSingleLineNoEOLInTheMiddle() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 5, 1, 5, "Abc");
    assertThat(fixResult).text().isEqualTo("FirstAbc line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 1)
        .hasInternalEdits()
        .onlyElement().isInsert(5, 5, 3);
  }
  @Test
  public void insertSingleLineNoEOLAtEnd() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 2, 0, 2, 0, "Abc");
    assertThat(fixResult).text().isEqualTo("First line\nAbc");
    assertThat(fixResult).edits().onlyElement()
        .isInsert(1, 1,   1)
        .hasInternalEdits()
        .onlyElement().isInsert(0, 0, 3);
  }

  @Test
  public void insertSingleLineWithEOLAtStart() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 0, 1, 0, "Abc\n");
    assertThat(fixResult).text().isEqualTo("Abc\nFirst line\n");
    assertThat(fixResult).edits().onlyElement()
        .isInsert(0, 0,  1)
        .hasInternalEdits()
        .onlyElement().isInsert(0, 0, 4);
  }
  @Test
  public void insertSingleLineWithEOLInTheMiddle() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 5, 1, 5, "Abc\n");
    assertThat(fixResult).text().isEqualTo("FirstAbc\n line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 2)
        .hasInternalEdits()
        .onlyElement().isInsert(5, 5, 4);
  }
  @Test
  public void insertSingleLineWithEOLAtEnd() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 2, 0, 2, 0, "Abc\n");
    assertThat(fixResult).text().isEqualTo("First line\nAbc\n");
    assertThat(fixResult).edits().onlyElement()
        .isInsert(1, 1,  1)
        .hasInternalEdits()
        .onlyElement().isInsert(0, 0, 4);
  }

  @Test
  public void insertMultilineLineWithEOLAtStart() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 0, 1, 0, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("Abc\nDefgh\nFirst line\n");
    assertThat(fixResult).edits().onlyElement()
        .isInsert(0, 0,  2)
        .hasInternalEdits()
        .onlyElement().isInsert(0, 0, 10);
  }
  @Test
  public void insertMultilineLineWithEOLInTheMiddle() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 5, 1, 5, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("FirstAbc\nDefgh\n line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 3)
        .hasInternalEdits()
        .onlyElement().isInsert(5, 5, 10);
  }

  @Test
  public void insertMultilineLineWithEOLAtEnd() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 2, 0, 2, 0, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("First line\nAbc\nDefgh\n");
    assertThat(fixResult).edits().onlyElement()
        .isInsert(1, 1, 2)
        .hasInternalEdits()
        .onlyElement().isInsert(0, 0, 10);
  }

  @Test
  public void replaceWithSingleLineNoEOLAtStart() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 0, 1, 2, "Abc");
    assertThat(fixResult).text().isEqualTo("Abcrst line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 1)
        .hasInternalEdits()
        .onlyElement().isReplace(0, 2, 0, 3);
  }

  @Test
  public void replaceWithSingleLineNoEOLInTheMiddle() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 3, 1, 5, "Abc");
    assertThat(fixResult).text().isEqualTo("FirAbc line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 1)
        .hasInternalEdits()
        .onlyElement().isReplace(3, 2, 3, 3);
  }

  @Test
  public void replaceWithSingleLineNoEOLAtEnd() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 9, 2, 0, "Abc");
    assertThat(fixResult).text().isEqualTo("First linAbc");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 1)
        .hasInternalEdits()
        .onlyElement().isReplace(9, 2, 9, 3);
  }

  @Test
  public void replaceWithSingleLineWithEOLAtStart() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 0, 1, 2, "Abc\n");
    assertThat(fixResult).text().isEqualTo("Abc\nrst line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 2)
        .hasInternalEdits()
        .onlyElement().isReplace(0, 2, 0, 4);
  }

  @Test
  public void replaceWithSingleLineWithEOLInTheMiddle() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 3, 1, 5, "Abc\n");
    assertThat(fixResult).text().isEqualTo("FirAbc\n line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 2)
        .hasInternalEdits()
        .onlyElement().isReplace(3, 2, 3, 4);
  }

  @Test
  public void replaceWithSingleLineWithEOLAtEnd() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 8, 2, 0, "Abc\n");
    assertThat(fixResult).text().isEqualTo("First liAbc\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 1)
        .hasInternalEdits()
        .onlyElement().isReplace(8, 3, 8, 4);
  }

  @Test
  public void replaceMultilineLineWithEOLAtStart() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 0, 1, 2, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("Abc\nDefgh\nrst line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 3)
        .hasInternalEdits()
        .onlyElement().isReplace(0, 2,0, 10);
  }

  @Test
  public void replaceMultilineLineWithEOLInTheMiddle() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 3, 1, 5, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("FirAbc\nDefgh\n line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 3)
        .hasInternalEdits()
        .onlyElement().isReplace(3, 2, 3, 10);
  }

  @Test
  public void replaceMultilineLineWithEOLAtEnd() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 8, 2, 0, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("First liAbc\nDefgh\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 2)
        .hasInternalEdits()
        .onlyElement().isReplace(8, 3, 8, 10);
  }

  @Test
  public void replaceMultilineLineNoEOLAtStart() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 0, 1, 2, "Abc\nDefgh");
    assertThat(fixResult).text().isEqualTo("Abc\nDefghrst line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 2)
        .hasInternalEdits()
        .onlyElement().isReplace(0, 2,0, 9);
  }
  @Test
  public void replaceMultilineLineNoEOLInTheMiddle() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 3, 1, 5, "Abc\nDefgh");
    assertThat(fixResult).text().isEqualTo("FirAbc\nDefgh line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 2)
        .hasInternalEdits()
        .onlyElement().isReplace(3, 2, 3, 9);
  }

  @Test
  public void replaceMultilineLineNoEOLAtEnd() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 8, 2, 0, "Abc\nDefgh");
    assertThat(fixResult).text().isEqualTo("First liAbc\nDefgh");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 2)
        .hasInternalEdits()
        .onlyElement().isReplace(8, 3, 8, 9);
  }

  @Test
  public void replaceWholeContent() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 0, 2, 0, "Abc");
    assertThat(fixResult).text().isEqualTo("Abc");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 1)
        .hasInternalEdits()
        .onlyElement().isReplace(0, 11, 0, 3);
  }

  @Test
  public void deleteWholeContent() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 0, 2, 0, "");
    assertThat(fixResult).text().isEqualTo("");
    assertThat(fixResult).edits().onlyElement()
        .isDelete(0, 1, 0)
        .hasInternalEdits()
        .onlyElement().isDelete(0, 11, 0);
  }

  @Test
  public void deleteAtStart() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 0, 1, 4, "");
    assertThat(fixResult).text().isEqualTo("t line\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 1)
        .hasInternalEdits()
        .onlyElement().isDelete(0, 4, 0);
  }

  @Test
  public void deleteInTheMidle() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 5, 1, 8, "");
    assertThat(fixResult).text().isEqualTo("Firstne\n");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 1)
        .hasInternalEdits()
        .onlyElement().isDelete(5, 3, 5);
  }

  @Test
  public void deleteAtEnd() throws Exception {
    FixResult fixResult = FixCalculatorTest.calculateFixSingleReplacement("First line\n", 1, 7, 2, 0, "");
    assertThat(fixResult).text().isEqualTo("First l");
    assertThat(fixResult).edits().onlyElement()
        .isReplace(0, 1,  0, 1)
        .hasInternalEdits()
        .onlyElement().isDelete(7, 4, 7);
  }
}
