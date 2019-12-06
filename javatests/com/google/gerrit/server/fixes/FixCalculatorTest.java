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

package com.google.gerrit.server.fixes;

import static com.google.gerrit.server.fixes.testing.FixResultSubject.assertThat;
import static com.google.gerrit.server.fixes.testing.GitEditSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.fixes.FixCalculator.FixResult;
import com.google.gerrit.server.patch.Text;
import org.eclipse.jgit.diff.Edit;
import org.junit.Test;

public class FixCalculatorTest {

  public static FixResult calculateFixSingleReplacement(
      String content, int startLine, int startChar, int endLine, int endChar, String replacement)
      throws ResourceConflictException {
    FixReplacement fixReplacement =
        new FixReplacement(
            "AnyPath", new Range(startLine, startChar, endLine, endChar), replacement);
    return FixCalculator.calculateFix(
        new Text(content.getBytes(UTF_8)), ImmutableList.of(fixReplacement));
  }

  @Test
  public void lineNumberMustBePositive() {
    assertThrows(
        ResourceConflictException.class,
        () -> calculateFixSingleReplacement("First line\nSecond line", 0, 0, 0, 0, "Abc"));
  }

  @Test
  public void insertInTheEmptyContent() throws Exception {
    FixResult fixResult = calculateFixSingleReplacement("", 1, 0, 1, 0, "Abc");
    assertThat(fixResult).text().isEqualTo("Abc");
    assertThat(fixResult).edits().onlyElement();
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isInsert(0, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 3);
  }

  @Test
  public void insertAtTheStartOfSingleLineContentNoEOLMark() throws Exception {
    FixResult fixResult = calculateFixSingleReplacement("First line", 1, 0, 1, 0, "Abc");
    assertThat(fixResult).text().isEqualTo("AbcFirst line");
    assertThat(fixResult).edits().onlyElement();
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 3);
  }

  @Test
  public void insertAtTheStartOfSingleLineContentHasEOLMark() throws Exception {
    FixResult fixResult = calculateFixSingleReplacement("First line\n", 1, 0, 1, 0, "Abc");
    assertThat(fixResult).text().isEqualTo("AbcFirst line\n");
    assertThat(fixResult).edits().onlyElement();
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 3);
  }

  @Test
  public void insertInTheMiddleOfSingleLineContentNoEOLMark() throws Exception {
    FixResult fixResult = calculateFixSingleReplacement("First line", 1, 5, 1, 5, "Abc");
    assertThat(fixResult).text().isEqualTo("FirstAbc line");
    assertThat(fixResult).edits().onlyElement();
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(5, 5, 3);
  }

  @Test
  public void insertInTheMiddleOfSingleLineContentHasEOLMark() throws Exception {
    FixResult fixResult = calculateFixSingleReplacement("First line\n", 1, 5, 1, 5, "Abc");
    assertThat(fixResult).text().isEqualTo("FirstAbc line\n");
    assertThat(fixResult).edits().onlyElement();
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(5, 5, 3);
  }

  @Test
  public void insertAtTheEndOfSingleLineContentNoEOLMark() throws Exception {
    FixResult fixResult = calculateFixSingleReplacement("First line", 1, 10, 1, 10, "Abc");
    assertThat(fixResult).text().isEqualTo("First lineAbc");
    assertThat(fixResult).edits().onlyElement();
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isReplace(0, 1, 0, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(10, 10, 3);
  }

  @Test
  public void insertAtTheEndOfSingleLineContentHasEOLMarkInvalidPosition() throws Exception {
    assertThrows(
        ResourceConflictException.class,
        () -> calculateFixSingleReplacement("First line\n", 1, 11, 1, 11, "Abc"));
  }

  @Test
  public void insertAtTheEndOfSingleLineContentHasEOLMark() throws Exception {
    FixResult fixResult = calculateFixSingleReplacement("First line\n", 2, 0, 2, 0, "Abc");
    assertThat(fixResult).text().isEqualTo("First line\nAbc");
    assertThat(fixResult).edits().onlyElement();
    Edit edit = fixResult.edits.get(0);
    assertThat(edit).isInsert(1, 1, 1);
    assertThat(edit).internalEdits().onlyElement().isInsert(0, 0, 3);
  }
}
