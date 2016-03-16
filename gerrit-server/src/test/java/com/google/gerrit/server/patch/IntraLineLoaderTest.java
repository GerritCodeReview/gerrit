// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.ReplaceEdit;

import org.junit.Test;

public class IntraLineLoaderTest {

  @Test
  public void rewriteAtStartOfLineIsRecognized() throws Exception {
    String a = "abc1\n";
    String b = "def1\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(0, 3, 0, 3));
  }

  @Test
  public void rewriteAtEndOfLineIsRecognized() throws Exception {
    String a = "abc1\n";
    String b = "abc2\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(3, 4, 3, 4));
  }

  @Test
  public void completeRewriteIncludesNewline() throws Exception {
    String a = "abc1\n";
    String b = "def2\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(0, 5, 0, 5));
  }

  @Test
  public void duplicateTextIsRecognizedAtEnd() throws Exception {
    String a = "abc\n";
    String b = "abcabc\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(3, 3, 3, 6));
  }

  @Test
  public void closeEditsAreCombined() throws Exception {
    String a = "ab1cdef2gh\n";
    String b = "ab2cdef3gh\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(2, 8, 2, 8));
  }

  @Test
  public void preferSecondDuplicateWhenInserting() throws Exception {
    String a = "start middle end\n";
    String b = "start middlemiddle end\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(12, 12, 12, 18));
  }

  @Test
  public void preferInsertAtLineBreak() throws Exception {
    String a = "multi\nline\n";
    String b = "multi\nlinemulti\nline\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(10, 10, 6, 16));
    // better would be:
    //assertThat(intraline(a, b)).isEqualTo(wordEdit(6, 6, 6, 16));
  }

  //TODO: expected failure
  // the current code does not work on the first line
  // and the insert marker is in the wrong location
  @Test(expected = AssertionError.class)
  public void additionalIndentationIsRecognizedAtStart() throws Exception {
    //         |0 2  |5 7
    String a = " abc\n  def\n";
    String b = "  abc\n   def\n";
    List<Edit> expected = new EditList();
    expected.add(new Edit(0, 0, 0, 1));
    expected.add(new Edit(5, 5, 6, 7));
    assertThat(intraline(a,b)).isEqualTo(expected);
  }

  //TODO: expected failure
  // the current code does not work on the first line
  // and the delete marker is in the wrong location
  @Test(expected = AssertionError.class)
  public void lessIndentationIsRecognizedAtStart() throws Exception {
    //         |0 2  |5 7
    String a = "  abc\n   def\n";
    String b = " abc\n  def\n";
    List<Edit> expected = new EditList();
    expected.add(new Edit(0, 1, 0, 0));
    expected.add(new Edit(6, 7, 5, 5));
    assertThat(intraline(a,b)).isEqualTo(expected);
  }

  @Test
  public void insertedWhitespaceIsRecognized() throws Exception {
    String a = " int *foobar\n";
    String b = " int * foobar\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(6, 6, 6, 7));
  }

  //TODO: expected failure, issue #3423
  // The current code wrongly marks the first space of the second line,
  // instead of the one inserted after the '*'.
  @Test(expected = AssertionError.class)
  public void insertedWhitespaceIsRecognizedInIdenticalLines() throws Exception {
    //         |0    5   10  |  5   20    5   30
    String a = " int *foobar\n int *foobar\n";
    String b = " int * foobar\n int * foobar\n";
    List<Edit> expected = new EditList();
    expected.add(new Edit(6, 6, 6, 7));
    expected.add(new Edit(19, 19, 20, 21));
    assertThat(intraline(a,b)).isEqualTo(expected);
  }

  // helper functions for the tests

  static private int countLines(String s) {
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '\n') { count++; }
    }
    return count;
  }

  static private List<Edit> intraline(String a, String b) throws Exception {
    return intraline(a, b, new Edit(0, countLines(a), 0, countLines(b)));
  }

  static private List<Edit> intraline(String a, String b, Edit lines) throws Exception {
    Text aText = new Text(a.getBytes(UTF_8));
    Text bText = new Text(b.getBytes(UTF_8));
    IntraLineDiff diff = IntraLineLoader.compute(aText, bText, EditList.singleton(lines));
    assertThat(diff.getStatus()).isEqualTo(IntraLineDiff.Status.EDIT_LIST);
    List<Edit> actualEdits = diff.getEdits();
    assertThat(actualEdits).hasSize(1);
    Edit actualEdit = actualEdits.get(0);
    assertThat(actualEdit.getBeginA()).isEqualTo(lines.getBeginA());
    assertThat(actualEdit.getEndA()).isEqualTo(lines.getEndA());
    assertThat(actualEdit.getBeginB()).isEqualTo(lines.getBeginB());
    assertThat(actualEdit.getEndB()).isEqualTo(lines.getEndB());
    assertThat(actualEdit).isInstanceOf(ReplaceEdit.class);
    return ((ReplaceEdit) actualEdit).getInternalEdits();
  }

  static private List<Edit> wordEdit(int as, int ae, int bs, int be) {
    return EditList.singleton(new Edit(as, ae, bs, be));
  }
}
