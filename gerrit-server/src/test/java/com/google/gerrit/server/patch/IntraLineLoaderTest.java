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

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.ReplaceEdit;

import static java.nio.charset.StandardCharsets.UTF_8;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.Ignore;

import java.util.List;

public class IntraLineLoaderTest {

  static List<Edit> intraline(String a, String b) throws Exception {
    return intraline(a, b, new Edit(0, 1, 0, 1));
  }

  static List<Edit> intraline(String a, String b, Edit lines) throws Exception {
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

  static List<Edit> wordEdit(int as, int ae, int bs, int be) {
    return EditList.singleton(new Edit(as, ae, bs, be));
  }

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

  @Test(expected = AssertionError.class)
  public void additionalIndentationIsRecognizedAtStart() throws Exception {
    String a = " abc\n";
    String b = "  abc\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(0, 0, 0, 1));
  }

  @Test(expected = AssertionError.class)
  public void lessIndentationIsRecognizedAtStart() throws Exception {
    String a = "  abc\n";
    String b = " abc\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(0, 1, 0, 2));
  }

  @Test
  public void insertedWhitespaceIsRecognized() throws Exception {
    String a = " int *foobar\n";
    String b = " int * foobar\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(6, 6, 6, 7));
  }

  @Test(expected = AssertionError.class) // issue #3423
  public void insertedWhitespaceIsRecognizedInIdenticalLines() throws Exception {
    //         |0    5   10  |  5   20    5   30
    String a = " int *foobar\n int *foobar\n";
    String b = " int * foobar\n int * foobar\n";
    List<Edit> expected = new EditList();
    expected.add(new Edit(6, 6, 6, 7));
    expected.add(new Edit(19, 19, 20, 21));
    List<Edit> actual = intraline(a, b, new Edit(0, 2, 0, 2));
    assertThat(actual).isEqualTo(expected);
  }
}
