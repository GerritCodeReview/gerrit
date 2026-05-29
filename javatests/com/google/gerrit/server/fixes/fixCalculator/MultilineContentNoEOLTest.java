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
import static com.google.gerrit.server.fixes.testing.GitEditSubject.assertThat;

import com.google.gerrit.server.fixes.FixCalculator.FixResult;
import org.eclipse.jgit.diff.Edit;
import org.junit.Test;

public class MultilineContentNoEOLTest {

  @Test
  public void insertSingleLineNoEOLAtStart() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 0, 1, 0, "Abc");
    assertThat(fixResult).text().isEqualTo("AbcFirst line\nSecond line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 3);
  }

  @Test
  public void insertSingleLineNoEOLInTheMiddle() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 2, 5, 2, 5, "Abc");
    assertThat(fixResult).text().isEqualTo("First line\nSeconAbcd line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(1, 1, 1, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(5, 5, 3);
  }

  @Test
  public void insertSingleLineNoEOLAtEnd() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 3, 10, 3, 10, "Abc");
    assertThat(fixResult).text().isEqualTo("First line\nSecond line\nThird lineAbc");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(2, 1, 2, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(10, 10, 3);
  }

  @Test
  public void insertSingleLineWithEOLAtStart() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 0, 1, 0, "Abc\n");
    assertThat(fixResult).text().isEqualTo("Abc\nFirst line\nSecond line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isInsert(0, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 4);
  }

  @Test
  public void insertSingleLineWithEOLInTheMiddle() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 2, 5, 2, 5, "Abc\n");
    assertThat(fixResult).text().isEqualTo("First line\nSeconAbc\nd line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(1, 1, 1, 2);
    assertThat(edit).internalEdits().onlyElement().isInsert(5, 5, 4);
  }

  @Test
  public void insertSingleLineWithEOLAtEnd() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 3, 10, 3, 10, "Abc\n");
    assertThat(fixResult).text().isEqualTo("First line\nSecond line\nThird lineAbc\n");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(2, 1, 2, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(10, 10, 4);
  }

  @Test
  public void insertMultilineLineWithEOLAtStart() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 0, 1, 0, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("Abc\nDefgh\nFirst line\nSecond line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isInsert(0, 0, 2);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 10);
  }

  @Test
  public void insertMultilineLineWithEOLInTheMiddle() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 2, 5, 2, 5, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("First line\nSeconAbc\nDefgh\nd line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(1, 1, 1, 3);
    assertThat(edit).internalEdits().onlyElement().isInsert(5, 5, 10);
  }

  @Test
  public void insertMultilineLineWithEOLAtEnd() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 3, 10, 3, 10, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("First line\nSecond line\nThird lineAbc\nDefgh\n");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(2, 1, 2, 2);
    assertThat(edit).internalEdits().onlyElement().isInsert(10, 10, 10);
  }

  @Test
  public void replaceWithSingleLineNoEOLAtStart() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 0, 1, 2, "Abc");
    assertThat(fixResult).text().isEqualTo("Abcrst line\nSecond line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isReplace(0, 2, 0, 3);
  }

  @Test
  public void replaceWithSingleLineNoEOLInTheMiddle() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 2, 3, 2, 5, "Abc");
    assertThat(fixResult).text().isEqualTo("First line\nSecAbcd line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(1, 1, 1, 1);
    assertThat(edit).internalEdits().onlyElement().isReplace(3, 2, 3, 3);
  }

  @Test
  public void replaceWithSingleLineNoEOLAtEnd() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 3, 8, 3, 10, "Abc");
    assertThat(fixResult).text().isEqualTo("First line\nSecond line\nThird liAbc");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(2, 1, 2, 1);
    assertThat(edit).internalEdits().onlyElement().isReplace(8, 2, 8, 3);
  }

  @Test
  public void replaceWithSingleLineWithEOLAtStart() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 0, 1, 2, "Abc\n");
    assertThat(fixResult).text().isEqualTo("Abc\nrst line\nSecond line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 2);
    assertThat(edit).internalEdits().onlyElement().isReplace(0, 2, 0, 4);
  }

  @Test
  public void replaceWithSingleLineWithEOLInTheMiddle() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 2, 3, 2, 5, "Abc\n");
    assertThat(fixResult).text().isEqualTo("First line\nSecAbc\nd line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(1, 1, 1, 2);
    assertThat(edit).internalEdits().onlyElement().isReplace(3, 2, 3, 4);
  }

  @Test
  public void replaceWithSingleLineWithEOLAtEnd() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 3, 8, 3, 10, "Abc\n");
    assertThat(fixResult).text().isEqualTo("First line\nSecond line\nThird liAbc\n");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(2, 1, 2, 1);
    assertThat(edit).internalEdits().onlyElement().isReplace(8, 2, 8, 4);
  }

  @Test
  public void replaceMultilineLineWithEOLAtStart() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 0, 1, 2, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("Abc\nDefgh\nrst line\nSecond line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 3);
    assertThat(edit).internalEdits().onlyElement().isReplace(0, 2, 0, 10);
  }

  @Test
  public void replaceMultilineLineWithEOLInTheMiddle() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 2, 3, 2, 5, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("First line\nSecAbc\nDefgh\nd line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(1, 1, 1, 3);
    assertThat(edit).internalEdits().onlyElement().isReplace(3, 2, 3, 10);
  }

  @Test
  public void replaceMultilineLineWithEOLAtEnd() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 3, 8, 3, 10, "Abc\nDefgh\n");
    assertThat(fixResult).text().isEqualTo("First line\nSecond line\nThird liAbc\nDefgh\n");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(2, 1, 2, 2);
    assertThat(edit).internalEdits().onlyElement().isReplace(8, 2, 8, 10);
  }

  @Test
  public void replaceMultilineLineNoEOLAtStart() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 0, 1, 2, "Abc\nDefgh");
    assertThat(fixResult).text().isEqualTo("Abc\nDefghrst line\nSecond line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 2);
    assertThat(edit).internalEdits().onlyElement().isReplace(0, 2, 0, 9);
  }

  @Test
  public void replaceMultilineLineNoEOLInTheMiddle() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 2, 3, 2, 5, "Abc\nDefgh");
    assertThat(fixResult).text().isEqualTo("First line\nSecAbc\nDefghd line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(1, 1, 1, 2);
    assertThat(edit).internalEdits().onlyElement().isReplace(3, 2, 3, 9);
  }

  @Test
  public void replaceMultilineLineNoEOLAtEnd() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 3, 8, 3, 10, "Abc\nDefgh");
    assertThat(fixResult).text().isEqualTo("First line\nSecond line\nThird liAbc\nDefgh");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(2, 1, 2, 2);
    assertThat(edit).internalEdits().onlyElement().isReplace(8, 2, 8, 9);
  }

  @Test
  public void replaceLastLine() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 3, 0, 3, 10, "Abc\ndef");
    assertThat(fixResult).text().isEqualTo("First line\nSecond line\nAbc\ndef");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(2, 1, 2, 2);
    assertThat(edit).internalEdits().onlyElement().isReplace(0, 10, 0, 7);
  }

  @Test
  public void replaceLastLineEndLineNotExists() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 3, 0, 4, 0, "Abc\ndef");
    assertThat(fixResult).text().isEqualTo("First line\nSecond line\nAbc\ndef");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(2, 1, 2, 2);
    assertThat(edit).internalEdits().onlyElement().isReplace(0, 10, 0, 7);
  }

  @Test
  public void replaceWholeContent() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 0, 3, 10, "Abc");
    assertThat(fixResult).text().isEqualTo("Abc");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 3, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isReplace(0, 33, 0, 3);
  }

  @Test
  public void deleteWholeContent() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 0, 3, 10, "");
    assertThat(fixResult).text().isEqualTo("");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isDelete(0, 3, 0);
    assertThat(edit).internalEdits().onlyElement().isDelete(0, 33, 0);
  }

  @Test
  public void deleteAtStart() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 0, 1, 4, "");
    assertThat(fixResult).text().isEqualTo("t line\nSecond line\nThird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isDelete(0, 4, 0);
  }

  @Test
  public void deleteInTheMiddle() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 1, 5, 3, 1, "");
    assertThat(fixResult).text().isEqualTo("Firsthird line");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 3, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isDelete(5, 19, 5);
  }

  @Test
  public void deleteAtEnd() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement(
            "First line\nSecond line\nThird line", 3, 7, 3, 10, "");
    assertThat(fixResult).text().isEqualTo("First line\nSecond line\nThird l");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(2, 1, 2, 1);
    assertThat(edit).internalEdits().onlyElement().isDelete(7, 3, 7);
  }
}
