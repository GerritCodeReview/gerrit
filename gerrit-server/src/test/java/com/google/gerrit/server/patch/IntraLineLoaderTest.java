// Copyright (C) 2009 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class IntraLineLoaderTest {

  static List<Edit> intraline(String a, String b) throws Exception {
    return intraline(a, b, new Edit(0, 1, 0, 1));
  }

  static List<Edit> intraline(String a, String b, Edit lines) throws Exception {
    Text aText = new Text(a.getBytes("UTF-8"));
    Text bText = new Text(b.getBytes("UTF-8"));
    IntraLineDiff diff = IntraLineLoader.compute(aText, bText, EditList.singleton(lines));
    assertEquals(diff.getStatus(), IntraLineDiff.Status.EDIT_LIST);
    List<Edit> actualEdits = diff.getEdits();
    assertEquals(actualEdits.size(), 1);
    Edit actualEdit = actualEdits.get(0);
    assertEquals(lines.getBeginA(), actualEdit.getBeginA());
    assertEquals(lines.getEndA(), actualEdit.getEndA());
    assertEquals(lines.getBeginB(), actualEdit.getBeginB());
    assertEquals(lines.getEndB(), actualEdit.getEndB());
    assertTrue(actualEdit instanceof ReplaceEdit);
    return ((ReplaceEdit) actualEdit).getInternalEdits();
  }

  static List<Edit> wordEdit(int as, int ae, int bs, int be) {
    return EditList.singleton(new Edit(as, ae, bs, be));
  }


  @Test
  public void testCompleteLineIncludesNewline() throws Exception {
    String a = "abc\n";
    String b = "def\n";
    assertEquals(wordEdit(0, 4, 0, 4), intraline(a, b));
  }

  @Test
  public void testHead() throws Exception {
    String a = "abc1\n";
    String b = "def1\n";
    assertEquals(wordEdit(0, 3, 0, 3), intraline(a, b));
  }

  @Test
  public void testAddedSame() throws Exception {
    String a = "abc\n";
    String b = "abcabc\n";
    assertEquals(wordEdit(3, 3, 3, 6), intraline(a, b));
  }

//  @Test
  public void testMoreIndentation() throws Exception {
    String a = "  abc\n";
    String b = "   abc\n";
    assertEquals(wordEdit(0, 0, 0, 1), intraline(a, b));
  }

//  @Test
  public void testLessIndentation() throws Exception {
    String a = "  abc\n";
    String b = " abc\n";
    assertEquals(wordEdit(0, 1, 0, 2), intraline(a, b));
  }

  @Test
  public void testWhitespace1() throws Exception {
    String a = "   uint *foobar\n";
    String b = "   uint * foobar\n";
    assertEquals(wordEdit(9, 9, 9, 10), intraline(a, b));
  }

  @Test
  public void testWhitespace2() throws Exception {
    //         |0    5   10    |5   20    5   30|    5   40    5   50
    String a = "   uint *foobar\n   uint *foobar\n   uint *foobar\n";
    String b = "   uint * foobar\n   uint * foobar\n   uint * foobar\n";
    List<Edit> expected = new EditList();
    expected.add(new Edit(9, 9, 9, 10));
    expected.add(new Edit(26, 26, 27, 28));
    expected.add(new Edit(43, 43, 45, 47));
    List<Edit> actual = intraline(a, b, new Edit(0, 3, 0, 3));
    assertEquals(expected, actual);
  }
}
