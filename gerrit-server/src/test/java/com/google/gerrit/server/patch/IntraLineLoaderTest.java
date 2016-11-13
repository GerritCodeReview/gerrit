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
    assertThat(intraline(a, b)).isEqualTo(ref().replace("abc", "def").common("1\n").edits);
  }

  @Test
  public void rewriteAtEndOfLineIsRecognized() throws Exception {
    String a = "abc1\n";
    String b = "abc2\n";
    assertThat(intraline(a, b)).isEqualTo(ref().common("abc").replace("1", "2").common("\n").edits);
  }

  @Test
  public void completeRewriteIncludesNewline() throws Exception {
    String a = "abc1\n";
    String b = "def2\n";
    assertThat(intraline(a, b)).isEqualTo(ref().replace("abc1\n", "def2\n").edits);
  }

  @Test
  public void closeEditsAreCombined() throws Exception {
    String a = "ab1cdef2gh\n";
    String b = "ab2cdef3gh\n";
    assertThat(intraline(a, b))
        .isEqualTo(ref().common("ab").replace("1cdef2", "2cdef3").common("gh\n").edits);
  }

  @Test
  public void preferInsertAfterCommonPart1() throws Exception {
    String a = "start middle end\n";
    String b = "start middlemiddle end\n";
    assertThat(intraline(a, b))
        .isEqualTo(ref().common("start middle").insert("middle").common(" end\n").edits);
  }

  @Test
  public void preferInsertAfterCommonPart2() throws Exception {
    String a = "abc def\n";
    String b = "abc  def\n";
    assertThat(intraline(a, b)).isEqualTo(ref().common("abc ").insert(" ").common("def\n").edits);
  }

  @Test
  public void preferInsertAtLineBreak1() throws Exception {
    String a = "multi\nline\n";
    String b = "multi\nlinemulti\nline\n";
    assertThat(intraline(a, b)).isEqualTo(wordEdit(10, 10, 6, 16));
    // better would be:
    //assertThat(intraline(a, b)).isEqualTo(wordEdit(6, 6, 6, 16));
    // or the equivalent:
    //assertThat(intraline(a, b)).isEqualTo(ref()
    //    .common("multi\n").insert("linemulti\n").common("line\n").edits
    //);
  }

  //TODO: expected failure
  // the current code does not work on the first line
  // and the insert marker is in the wrong location
  @Test(expected = AssertionError.class)
  public void preferInsertAtLineBreak2() throws Exception {
    String a = "  abc\n    def\n";
    String b = "    abc\n      def\n";
    assertThat(intraline(a, b))
        .isEqualTo(ref().insert("  ").common("  abc\n").insert("  ").common("  def\n").edits);
  }

  //TODO: expected failure
  // the current code does not work on the first line
  @Test(expected = AssertionError.class)
  public void preferDeleteAtLineBreak() throws Exception {
    String a = "    abc\n      def\n";
    String b = "  abc\n    def\n";
    assertThat(intraline(a, b))
        .isEqualTo(ref().remove("  ").common("  abc\n").remove("  ").common("  def\n").edits);
  }

  @Test
  public void insertedWhitespaceIsRecognized() throws Exception {
    String a = " int *foobar\n";
    String b = " int * foobar\n";
    assertThat(intraline(a, b))
        .isEqualTo(ref().common(" int *").insert(" ").common("foobar\n").edits);
  }

  @Test
  public void insertedWhitespaceIsRecognizedInMultipleLines() throws Exception {
    //         |0    5   10  |  5   20    5   30
    String a = " int *foobar\n int *foobar\n";
    String b = " int * foobar\n int * foobar\n";
    assertThat(intraline(a, b))
        .isEqualTo(
            ref()
                .common(" int *")
                .insert(" ")
                .common("foobar\n")
                .common(" int *")
                .insert(" ")
                .common("foobar\n")
                .edits);
  }

  // helper functions to call IntraLineLoader.compute

  private static int countLines(String s) {
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '\n') {
        count++;
      }
    }
    return count;
  }

  private static List<Edit> intraline(String a, String b) throws Exception {
    return intraline(a, b, new Edit(0, countLines(a), 0, countLines(b)));
  }

  private static List<Edit> intraline(String a, String b, Edit lines) throws Exception {
    Text aText = new Text(a.getBytes(UTF_8));
    Text bText = new Text(b.getBytes(UTF_8));

    IntraLineDiff diff;
    diff = IntraLineLoader.compute(aText, bText, EditList.singleton(lines));

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

  // helpers to compute reference values

  private static List<Edit> wordEdit(int as, int ae, int bs, int be) {
    return EditList.singleton(new Edit(as, ae, bs, be));
  }

  private static Reference ref() {
    return new Reference();
  }

  private static class Reference {
    List<Edit> edits;
    private int posA;
    private int posB;

    Reference() {
      edits = new EditList();
      posA = posB = 0;
    }

    Reference common(String s) {
      int len = s.length();
      posA += len;
      posB += len;
      return this;
    }

    Reference insert(String s) {
      return replace("", s);
    }

    Reference remove(String s) {
      return replace(s, "");
    }

    Reference replace(String a, String b) {
      int lenA = a.length();
      int lenB = b.length();
      edits.add(new Edit(posA, posA + lenA, posB, posB + lenB));
      posA += lenA;
      posB += lenB;
      return this;
    }
  }
}
