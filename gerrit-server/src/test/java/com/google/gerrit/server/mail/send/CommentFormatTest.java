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

package com.google.gerrit.server.mail.send;

import static com.google.common.truth.Truth.assertThat;

import static com.google.gerrit.server.mail.send.CommentFormat.BlockType.PARAGRAPH;
import static com.google.gerrit.server.mail.send.CommentFormat.BlockType.QUOTE;
import static com.google.gerrit.server.mail.send.CommentFormat.BlockType.LIST;
import static com.google.gerrit.server.mail.send.CommentFormat.BlockType.PRE_FORMATTED;

import org.junit.Test;

import java.util.List;

public class CommentFormatTest {
  private void assertBlock(List<CommentFormat.Block> list, int index,
      CommentFormat.BlockType type, String text) {
    CommentFormat.Block block = list.get(index);
    assertThat(block.type).isEqualTo(type);
    assertThat(block.text).isEqualTo(text);
  }

  private void assertBlock(List<CommentFormat.Block> list, int index,
      CommentFormat.BlockType type, int itemIndex, String text) {
    CommentFormat.Block block = list.get(index);
    assertThat(block.type).isEqualTo(type);
    assertThat(block.items.get(itemIndex)).isEqualTo(text);
  }

  @Test
  public void testParseNullAsEmpty() {
    assertThat(CommentFormat.parse(null)).isEmpty();
  }

  @Test
  public void testParseEmpty() {
    assertThat(CommentFormat.parse("")).isEmpty();
  }

  @Test
  public void testParseSimple() {
    String comment = "Para1";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PARAGRAPH, comment);
  }

  @Test
  public void testParseMultilinePara() {
    String comment = "Para 1\nStill para 1";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PARAGRAPH, comment);
  }

  @Test
  public void testParseParaBreak() {
    String comment = "Para 1\n\nPara 2\n\nPara 3";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "Para 1");
    assertBlock(result, 1, PARAGRAPH, "Para 2");
    assertBlock(result, 2, PARAGRAPH, "Para 3");
  }

  @Test
  public void testParseQuote() {
    String comment = "> Quote text";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, QUOTE, "Quote text");
  }

  @Test
  public void testParseExcludesEmpty() {
    String comment = "Para 1\n\n\n\nPara 2";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "Para 1");
    assertBlock(result, 1, PARAGRAPH, "Para 2");
  }

  @Test
  public void testParseQuoteLeadSpace() {
    String comment = " > Quote text";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, QUOTE, "Quote text");
  }

  @Test
  public void testParseMultiLineQuote() {
    String comment = "> Quote line 1\n> Quote line 2\n > Quote line 3\n";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, QUOTE, "Quote line 1\nQuote line 2\nQuote line 3");
  }

  @Test
  public void testParsePre() {
    String comment = "    Four space indent.";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PRE_FORMATTED, comment);
  }

  @Test
  public void testParseOneSpacePre() {
    String comment = " One space indent.\n Another line.";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PRE_FORMATTED, comment);
  }

  @Test
  public void testParseTabPre() {
    String comment = "\tOne tab indent.\n\tAnother line.\n  Yet another!";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PRE_FORMATTED, comment);
  }

  @Test
  public void testParseIntermediateLeadingWhitespacePre() {
    String comment = "No indent.\n\tNonzero indent.\nNo indent again.";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PRE_FORMATTED, comment);
  }

  @Test
  public void testParseStarList() {
    String comment = "* Item 1\n* Item 2\n* Item 3";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, LIST, 0, "Item 1");
    assertBlock(result, 0, LIST, 1, "Item 2");
    assertBlock(result, 0, LIST, 2, "Item 3");
  }

  @Test
  public void testParseDashList() {
    String comment = "- Item 1\n- Item 2\n- Item 3";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, LIST, 0, "Item 1");
    assertBlock(result, 0, LIST, 1, "Item 2");
    assertBlock(result, 0, LIST, 2, "Item 3");
  }

  @Test
  public void testParseMixedList() {
    String comment = "- Item 1\n* Item 2\n- Item 3\n* Item 4";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, LIST, 0, "Item 1");
    assertBlock(result, 0, LIST, 1, "Item 2");
    assertBlock(result, 0, LIST, 2, "Item 3");
    assertBlock(result, 0, LIST, 3, "Item 4");
  }

  @Test
  public void testParseMixedBlockTypes() {
    String comment = "Paragraph\nacross\na\nfew\nlines."
        + "\n\n"
        + "> Quote\n> across\n> not many lines."
        + "\n\n"
        + "Another paragraph"
        + "\n\n"
        + "* Series\n* of\n* list\n* items"
        + "\n\n"
        + "Yet another paragraph"
        + "\n\n"
        + "\tPreformatted text."
        + "\n\n"
        + "Parting words.";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(7);
    assertBlock(result, 0, PARAGRAPH, "Paragraph\nacross\na\nfew\nlines.");
    assertBlock(result, 1, QUOTE, "Quote\nacross\nnot many lines.");
    assertBlock(result, 2, PARAGRAPH, "Another paragraph");
    assertBlock(result, 3, LIST, 0, "Series");
    assertBlock(result, 3, LIST, 1, "of");
    assertBlock(result, 3, LIST, 2, "list");
    assertBlock(result, 3, LIST, 3, "items");
    assertBlock(result, 4, PARAGRAPH, "Yet another paragraph");
    assertBlock(result, 5, PRE_FORMATTED, "\tPreformatted text.");
    assertBlock(result, 6, PARAGRAPH, "Parting words.");
  }

  // BEGIN: Test cases adapted from:
  //    com.google.gwtexpui.safehtml.client.SafeHtml_WikifyListTest

  @Test
  public void testBulletList1() {
    String comment = "A\n\n* line 1\n* 2nd line";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertBlock(result, 1, LIST, 0, "line 1");
    assertBlock(result, 1, LIST, 1, "2nd line");
  }

  @Test
  public void testBulletList2() {
    String comment = "A\n\n* line 1\n* 2nd line\n\nB";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertBlock(result, 1, LIST, 0, "line 1");
    assertBlock(result, 1, LIST, 1, "2nd line");
    assertBlock(result, 2, PARAGRAPH, "B");
  }

  @Test
  public void testBulletList3() {
    String comment = "* line 1\n* 2nd line\n\nB";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, LIST, 0, "line 1");
    assertBlock(result, 0, LIST, 1, "2nd line");
    assertBlock(result, 1, PARAGRAPH, "B");
  }

  @Test
  public void testBulletList4() {
    String comment = "To see this bug, you have to:\n" //
        + "* Be on IMAP or EAS (not on POP)\n"//
        + "* Be very unlucky\n";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "To see this bug, you have to:");
    assertBlock(result, 1, LIST, 0, "Be on IMAP or EAS (not on POP)");
    assertBlock(result, 1, LIST, 1, "Be very unlucky");
  }

  @Test
  public void testBulletList5() {
    String comment = "To see this bug,\n" //
        + "you have to:\n" //
        + "* Be on IMAP or EAS (not on POP)\n"//
        + "* Be very unlucky\n";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "To see this bug, you have to:");
    assertBlock(result, 1, LIST, 0, "Be on IMAP or EAS (not on POP)");
    assertBlock(result, 1, LIST, 1, "Be very unlucky");
  }

  @Test
  public void testDashList1() {
    String comment = "A\n\n- line 1\n- 2nd line";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertBlock(result, 1, LIST, 0, "line 1");
    assertBlock(result, 1, LIST, 1, "2nd line");
  }

  @Test
  public void testDashList2() {
    String comment = "A\n\n- line 1\n- 2nd line\n\nB";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertBlock(result, 1, LIST, 0, "line 1");
    assertBlock(result, 1, LIST, 1, "2nd line");
    assertBlock(result, 2, PARAGRAPH, "B");
  }

  @Test
  public void testDashList3() {
    String comment = "- line 1\n- 2nd line\n\nB";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, LIST, 0, "line 1");
    assertBlock(result, 0, LIST, 1, "2nd line");
    assertBlock(result, 1, PARAGRAPH, "B");
  }

  // END: Test cases adapted from:
  //    com.google.gwtexpui.safehtml.client.SafeHtml_WikifyListTest

  // BEGIN: Test cases adapted from:
  //    com.google.gwtexpui.safehtml.client.SafeHtml_WikifyPreformatTest

  @Test
  public void testPreformat1() {
    String comment = "A\n\n  This is pre\n  formatted";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertBlock(result, 1, PRE_FORMATTED, "  This is pre\n  formatted");
  }

  @Test
  public void testPreformat2() {
    String comment = "A\n\n  This is pre\n  formatted\n\nbut this is not";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertBlock(result, 1, PRE_FORMATTED, "  This is pre\n  formatted");
    assertBlock(result, 2, PARAGRAPH, "but this is not");
  }

  @Test
  public void testPreformat3() {
    String comment = "A\n\n  Q\n    <R>\n  S\n\nB";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertBlock(result, 1, PRE_FORMATTED, "  Q\n    <R>\n  S");
    assertBlock(result, 2, PARAGRAPH, "B");
  }

  @Test
  public void testPreformat4() {
    String comment = "  Q\n    <R>\n  S\n\nB";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PRE_FORMATTED, "  Q\n    <R>\n  S");
    assertBlock(result, 1, PARAGRAPH, "B");
  }

  // END: Test cases adapted from:
  //    com.google.gwtexpui.safehtml.client.SafeHtml_WikifyPreformatTest

  // BEGIN: Test cases adapted from:
  //    com.google.gwtexpui.safehtml.client.SafeHtml_WikifyQuoteTest

  @Test
  public void testQuote1() {
    String comment = "> I'm happy\n > with quotes!\n\nSee above.";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, QUOTE, "I'm happy\nwith quotes!");
    assertBlock(result, 1, PARAGRAPH, "See above.");
  }

  @Test
  public void testQuote2() {
    String comment = "See this said:\n\n > a quoted\n > string block\n\nOK?";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "See this said:");
    assertBlock(result, 1, QUOTE, "a quoted\nstring block");
    assertBlock(result, 2, PARAGRAPH, "OK?");
  }

  @Test
  public void testNestedQuotes1() {
    String comment = " > > prior\n > \n > next\n";
    List<CommentFormat.Block> result = CommentFormat.parse(comment);

    assertThat(result).hasSize(1);

    // Note: block does not encode nesting.
    assertBlock(result, 0, QUOTE, "> prior\n\nnext");
  }

  // END: Test cases adapted from:
  //    com.google.gwtexpui.safehtml.client.SafeHtml_WikifyQuoteTest
}
