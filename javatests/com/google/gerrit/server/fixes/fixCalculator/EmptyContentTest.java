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

public class EmptyContentTest {
  @Test
  public void insertSingleLineNoEOL() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement("", 1, 0, 1, 0, "Abc");
    assertThat(fixResult).text().isEqualTo("Abc");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isInsert(0, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 3);
  }

  @Test
  public void insertSingleLineWithEOL() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement("", 1, 0, 1, 0, "Abc\n");
    assertThat(fixResult).text().isEqualTo("Abc\n");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isInsert(0, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 4);
  }

  @Test
  public void insertMultilineNoEOL() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement("", 1, 0, 1, 0, "Abc\nDEFGH");
    assertThat(fixResult).text().isEqualTo("Abc\nDEFGH");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isInsert(0, 0, 2);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 9);
  }

  @Test
  public void insertMultilineWithEOL() throws Exception {
    FixResult fixResult =
        FixCalculatorVariousTest.calculateFixSingleReplacement("", 1, 0, 1, 0, "Abc\nDEFGH\n");
    assertThat(fixResult).text().isEqualTo("Abc\nDEFGH\n");
    assertThat(fixResult).edits().hasSize(1);
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isInsert(0, 0, 2);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 10);
  }
}
