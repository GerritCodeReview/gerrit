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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.fixes.FixCalculator;
import com.google.gerrit.server.fixes.FixCalculator.FixResult;
import com.google.gerrit.server.patch.Text;
import org.eclipse.jgit.diff.Edit;
import org.junit.Test;

public class FixCalculatorVariousTest {
  private static final String multilineContentString =
      "First line\nSecond line\nThird line\nFourth line\nFifth line\n";
  private static final Text multilineContent = new Text(multilineContentString.getBytes(UTF_8));

  static FixResult calculateFixSingleReplacement(
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
  public void lineNumberMustExist() {
    assertThrows(
        ResourceConflictException.class,
        () -> calculateFixSingleReplacement("First line\nSecond line", 4, 0, 4, 0, "Abc"));
  }

  @Test
  public void startOffsetMustNotBeNegative() {
    assertThrows(
        ResourceConflictException.class,
        () -> calculateFixSingleReplacement("First line\nSecond line", 0, -1, 0, 0, "Abc"));
  }

  @Test
  public void endOffsetMustNotBeNegative() {
    assertThrows(
        ResourceConflictException.class,
        () -> calculateFixSingleReplacement("First line\nSecond line", 0, 0, 0, -1, "Abc"));
  }

  @Test
  public void insertAtTheEndOfSingleLineContentHasEOLMarkInvalidPosition() {
    assertThrows(
        ResourceConflictException.class,
        () -> calculateFixSingleReplacement("First line\n", 1, 11, 1, 11, "Abc"));
  }

  @Test
  public void startAfterEndOfLineMarkOfIntermediateLineThrowsAnException() {
    assertThrows(
        ResourceConflictException.class,
        () ->
            calculateFixSingleReplacement(
                "First line\nSecond line\nThird line\n", 1, 11, 2, 6, "Abc"));
  }

  @Test
  public void startAfterEndOfLineMarkOfLastLineThrowsAnException() {
    assertThrows(
        ResourceConflictException.class,
        () -> calculateFixSingleReplacement("First line\n", 1, 11, 2, 0, "Abc"));
  }

  @Test
  public void endAfterEndOfLineMarkOfIntermediateLineThrowsAnException() {
    assertThrows(
        ResourceConflictException.class,
        () ->
            calculateFixSingleReplacement(
                "First line\nSecond line\nThird line\n", 2, 0, 2, 12, "Abc"));
  }

  @Test
  public void endAfterEndOfLineMarkOfLastLineThrowsAnException() {
    assertThrows(
        ResourceConflictException.class,
        () -> calculateFixSingleReplacement("First line\nSecond line\n", 2, 0, 2, 12, "Abc"));
  }

  @Test
  public void severalChangesInTheSameLineNonSorted() throws Exception {
    FixReplacement replace = new FixReplacement("path", new Range(2, 1, 2, 3), "ABC");
    FixReplacement insert = new FixReplacement("path", new Range(2, 5, 2, 5), "DEFG");
    FixReplacement delete = new FixReplacement("path", new Range(2, 7, 2, 9), "");
    FixResult result =
        FixCalculator.calculateFix(multilineContent, ImmutableList.of(replace, delete, insert));
    assertThat(result)
        .text()
        .isEqualTo("First line\nSABConDEFGd ne\nThird line\nFourth line\nFifth line\n");
    assertThat(result).edits().hasSize(1);
    Edit edit = result.edits.get(0);
    assertThat(edit).isReplace(1, 1, 1, 1);
    assertThat(edit).internalEdits().hasSize(3);
    assertThat(edit).internalEdits().element(0).isReplace(1, 2, 1, 3);
    assertThat(edit).internalEdits().element(1).isInsert(5, 6, 4);
    assertThat(edit).internalEdits().element(2).isDelete(7, 2, 12);
  }

  @Test
  public void severalChangesInConsecutiveLines() throws Exception {
    FixReplacement replace = new FixReplacement("path", new Range(2, 1, 2, 3), "ABC");
    FixReplacement insert = new FixReplacement("path", new Range(3, 5, 3, 5), "DEFG");
    FixReplacement delete = new FixReplacement("path", new Range(4, 7, 4, 9), "");
    FixResult result =
        FixCalculator.calculateFix(multilineContent, ImmutableList.of(replace, insert, delete));
    assertThat(result)
        .text()
        .isEqualTo("First line\nSABCond line\nThirdDEFG line\nFourth ne\nFifth line\n");
    assertThat(result).edits().hasSize(1);
    Edit edit = result.edits.get(0);
    assertThat(edit).isReplace(1, 3, 1, 3);
    assertThat(edit).internalEdits().hasSize(3);
    assertThat(edit).internalEdits().element(0).isReplace(1, 2, 1, 3);
    assertThat(edit).internalEdits().element(1).isInsert(17, 18, 4);
    assertThat(edit).internalEdits().element(2).isDelete(30, 2, 35);
  }

  @Test
  public void severalChangesInNonConsecutiveLines() throws Exception {
    FixReplacement replace = new FixReplacement("path", new Range(1, 1, 1, 3), "ABC");
    FixReplacement insert = new FixReplacement("path", new Range(3, 5, 3, 5), "DEFG");
    FixReplacement delete = new FixReplacement("path", new Range(5, 9, 6, 0), "");
    FixResult result =
        FixCalculator.calculateFix(multilineContent, ImmutableList.of(replace, insert, delete));
    assertThat(result)
        .text()
        .isEqualTo("FABCst line\nSecond line\nThirdDEFG line\nFourth line\nFifth lin");
    assertThat(result).edits().hasSize(3);
    assertThat(result).edits().element(0).isReplace(0, 1, 0, 1);
    assertThat(result).edits().element(0).internalEdits().onlyElement().isReplace(1, 2, 1, 3);
    assertThat(result).edits().element(1).isReplace(2, 1, 2, 1);
    assertThat(result).edits().element(1).internalEdits().onlyElement().isInsert(5, 5, 4);
    assertThat(result).edits().element(2).isReplace(4, 1, 4, 1);
    assertThat(result).edits().element(2).internalEdits().onlyElement().isDelete(9, 2, 9);
  }

  @Test
  public void multipleChanges() throws Exception {
    String str =
        "First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line"
            + "\nSeventh line\nEighth line\nNinth line\nTenth line\n";
    Text content = new Text(str.getBytes(UTF_8));

    FixReplacement multiLineReplace =
        new FixReplacement("path", new Range(1, 2, 3, 3), "AB\nC\nDEFG\nQ\n");
    FixReplacement multiLineDelete = new FixReplacement("path", new Range(4, 8, 5, 8), "");
    FixReplacement singleLineInsert = new FixReplacement("path", new Range(5, 10, 5, 10), "QWERTY");

    FixReplacement singleLineReplace = new FixReplacement("path", new Range(7, 3, 7, 7), "XY");
    FixReplacement multiLineInsert =
        new FixReplacement("path", new Range(8, 7, 8, 7), "KLMNO\nASDF");

    FixReplacement singleLineDelete = new FixReplacement("path", new Range(10, 3, 10, 7), "");

    FixResult result =
        FixCalculator.calculateFix(
            content,
            ImmutableList.of(
                multiLineReplace,
                multiLineDelete,
                singleLineInsert,
                singleLineReplace,
                multiLineInsert,
                singleLineDelete));
    assertThat(result)
        .text()
        .isEqualTo(
            "FiAB\n"
                + "C\n"
                + "DEFG\n"
                + "Q\n"
                + "rd line\n"
                + "Fourth lneQWERTY\n"
                + "Sixth line\n"
                + "SevXY line\n"
                + "Eighth KLMNO\n"
                + "ASDFline\n"
                + "Ninth line\n"
                + "Tenine\n");
    assertThat(result).edits().hasSize(3);
    assertThat(result).edits().element(0).isReplace(0, 5, 0, 6);
    assertThat(result)
        .edits()
        .element(0)
        .internalEdits()
        .containsExactly(
            new Edit(2, 26, 2, 14), new Edit(42, 54, 30, 30), new Edit(56, 56, 32, 38));

    assertThat(result).edits().element(1).isReplace(6, 2, 7, 3);
    assertThat(result)
        .edits()
        .element(1)
        .internalEdits()
        .containsExactly(new Edit(3, 7, 3, 5), new Edit(20, 20, 18, 28));
    assertThat(result).edits().element(2).isReplace(9, 1, 11, 1);
    assertThat(result).edits().element(2).internalEdits().onlyElement().isDelete(3, 4, 3);
  }

  @Test
  public void changesMayTouch() throws Exception {
    FixReplacement firstReplace = new FixReplacement("path", new Range(1, 6, 2, 7), "modified ");
    FixReplacement consecutiveReplace =
        new FixReplacement("path", new Range(2, 7, 3, 5), "content");
    FixResult result =
        FixCalculator.calculateFix(
            multilineContent, ImmutableList.of(firstReplace, consecutiveReplace));
    assertThat(result).text().isEqualTo("First modified content line\nFourth line\nFifth line\n");
    assertThat(result).edits().hasSize(1);
    Edit edit = result.edits.get(0);
    assertThat(edit).isReplace(0, 3, 0, 1);
    // The current code creates two inline edits even though only one would be necessary. It
    // shouldn't make a visual difference to the user and hence we can ignore this.
    assertThat(edit).internalEdits().hasSize(2);
    assertThat(edit).internalEdits().element(0).isReplace(6, 12, 6, 9);
    assertThat(edit).internalEdits().element(1).isReplace(18, 10, 15, 7);
  }
}
