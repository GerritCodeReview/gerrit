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

import static com.google.gerrit.server.mail.send.CommentFormatter.BlockType.LIST;
import static com.google.gerrit.server.mail.send.CommentFormatter.BlockType.PARAGRAPH;
import static com.google.gerrit.server.mail.send.CommentFormatter.BlockType.PRE_FORMATTED;
import static com.google.gerrit.server.mail.send.CommentFormatter.BlockType.QUOTE;

import org.junit.Test;

import java.util.List;

public class CommentFormatterTest {
  private void assertBlock(List<CommentFormatter.Block> list, int index,
      CommentFormatter.BlockType type, String text) {
    CommentFormatter.Block block = list.get(index);
    assertThat(block.type).isEqualTo(type);
    assertThat(block.text).isEqualTo(text);
    assertThat(block.items).isNull();
  }

  private void assertListBlock(List<CommentFormatter.Block> list, int index,
      int itemIndex, String text) {
    CommentFormatter.Block block = list.get(index);
    assertThat(block.type).isEqualTo(LIST);
    assertThat(block.items.get(itemIndex)).isEqualTo(text);
    assertThat(block.text).isNull();
  }

  @Test
  public void testParseNullAsEmpty() {
    assertThat(CommentFormatter.parse(null)).isEmpty();
  }

  @Test
  public void testParseEmpty() {
    assertThat(CommentFormatter.parse("")).isEmpty();
  }

  @Test
  public void testParseSimple() {
    String comment = "Para1";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PARAGRAPH, comment);
  }

  @Test
  public void testParseMultilinePara() {
    String comment = "Para 1\nStill para 1";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PARAGRAPH, comment);
  }

  @Test
  public void testParseParaBreak() {
    String comment = "Para 1\n\nPara 2\n\nPara 3";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "Para 1");
    assertBlock(result, 1, PARAGRAPH, "Para 2");
    assertBlock(result, 2, PARAGRAPH, "Para 3");
  }

  @Test
  public void testParseQuote() {
    String comment = "> Quote text";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, QUOTE, "Quote text");
  }

  @Test
  public void testParseExcludesEmpty() {
    String comment = "Para 1\n\n\n\nPara 2";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "Para 1");
    assertBlock(result, 1, PARAGRAPH, "Para 2");
  }

  @Test
  public void testParseQuoteLeadSpace() {
    String comment = " > Quote text";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, QUOTE, "Quote text");
  }

  @Test
  public void testParseMultiLineQuote() {
    String comment = "> Quote line 1\n> Quote line 2\n > Quote line 3\n";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, QUOTE, "Quote line 1\nQuote line 2\nQuote line 3");
  }

  @Test
  public void testParsePre() {
    String comment = "    Four space indent.";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PRE_FORMATTED, comment);
  }

  @Test
  public void testParseOneSpacePre() {
    String comment = " One space indent.\n Another line.";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PRE_FORMATTED, comment);
  }

  @Test
  public void testParseTabPre() {
    String comment = "\tOne tab indent.\n\tAnother line.\n  Yet another!";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PRE_FORMATTED, comment);
  }

  @Test
  public void testParseIntermediateLeadingWhitespacePre() {
    String comment = "No indent.\n\tNonzero indent.\nNo indent again.";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertBlock(result, 0, PRE_FORMATTED, comment);
  }

  @Test
  public void testParseStarList() {
    String comment = "* Item 1\n* Item 2\n* Item 3";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertListBlock(result, 0, 0, "Item 1");
    assertListBlock(result, 0, 1, "Item 2");
    assertListBlock(result, 0, 2, "Item 3");
  }

  @Test
  public void testParseDashList() {
    String comment = "- Item 1\n- Item 2\n- Item 3";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertListBlock(result, 0, 0, "Item 1");
    assertListBlock(result, 0, 1, "Item 2");
    assertListBlock(result, 0, 2, "Item 3");
  }

  @Test
  public void testParseMixedList() {
    String comment = "- Item 1\n* Item 2\n- Item 3\n* Item 4";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);
    assertListBlock(result, 0, 0, "Item 1");
    assertListBlock(result, 0, 1, "Item 2");
    assertListBlock(result, 0, 2, "Item 3");
    assertListBlock(result, 0, 3, "Item 4");
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
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(7);
    assertBlock(result, 0, PARAGRAPH, "Paragraph\nacross\na\nfew\nlines.");
    assertBlock(result, 1, QUOTE, "Quote\nacross\nnot many lines.");
    assertBlock(result, 2, PARAGRAPH, "Another paragraph");
    assertListBlock(result, 3, 0, "Series");
    assertListBlock(result, 3, 1, "of");
    assertListBlock(result, 3, 2, "list");
    assertListBlock(result, 3, 3, "items");
    assertBlock(result, 4, PARAGRAPH, "Yet another paragraph");
    assertBlock(result, 5, PRE_FORMATTED, "\tPreformatted text.");
    assertBlock(result, 6, PARAGRAPH, "Parting words.");
  }

  @Test
  public void testBulletList1() {
    String comment = "A\n\n* line 1\n* 2nd line";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertListBlock(result, 1, 0, "line 1");
    assertListBlock(result, 1, 1, "2nd line");
  }

  @Test
  public void testBulletList2() {
    String comment = "A\n\n* line 1\n* 2nd line\n\nB";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertListBlock(result, 1, 0, "line 1");
    assertListBlock(result, 1, 1, "2nd line");
    assertBlock(result, 2, PARAGRAPH, "B");
  }

  @Test
  public void testBulletList3() {
    String comment = "* line 1\n* 2nd line\n\nB";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(2);
    assertListBlock(result, 0, 0, "line 1");
    assertListBlock(result, 0, 1, "2nd line");
    assertBlock(result, 1, PARAGRAPH, "B");
  }

  @Test
  public void testBulletList4() {
    String comment = "To see this bug, you have to:\n" //
        + "* Be on IMAP or EAS (not on POP)\n"//
        + "* Be very unlucky\n";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "To see this bug, you have to:");
    assertListBlock(result, 1, 0, "Be on IMAP or EAS (not on POP)");
    assertListBlock(result, 1, 1, "Be very unlucky");
  }

  @Test
  public void testBulletList5() {
    String comment = "To see this bug,\n" //
        + "you have to:\n" //
        + "* Be on IMAP or EAS (not on POP)\n"//
        + "* Be very unlucky\n";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "To see this bug, you have to:");
    assertListBlock(result, 1, 0, "Be on IMAP or EAS (not on POP)");
    assertListBlock(result, 1, 1, "Be very unlucky");
  }

  @Test
  public void testDashList1() {
    String comment = "A\n\n- line 1\n- 2nd line";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertListBlock(result, 1, 0, "line 1");
    assertListBlock(result, 1, 1, "2nd line");
  }

  @Test
  public void testDashList2() {
    String comment = "A\n\n- line 1\n- 2nd line\n\nB";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertListBlock(result, 1, 0, "line 1");
    assertListBlock(result, 1, 1, "2nd line");
    assertBlock(result, 2, PARAGRAPH, "B");
  }

  @Test
  public void testDashList3() {
    String comment = "- line 1\n- 2nd line\n\nB";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(2);
    assertListBlock(result, 0, 0, "line 1");
    assertListBlock(result, 0, 1, "2nd line");
    assertBlock(result, 1, PARAGRAPH, "B");
  }

  @Test
  public void testPreformat1() {
    String comment = "A\n\n  This is pre\n  formatted";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertBlock(result, 1, PRE_FORMATTED, "  This is pre\n  formatted");
  }

  @Test
  public void testPreformat2() {
    String comment = "A\n\n  This is pre\n  formatted\n\nbut this is not";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertBlock(result, 1, PRE_FORMATTED, "  This is pre\n  formatted");
    assertBlock(result, 2, PARAGRAPH, "but this is not");
  }

  @Test
  public void testPreformat3() {
    String comment = "A\n\n  Q\n    <R>\n  S\n\nB";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "A");
    assertBlock(result, 1, PRE_FORMATTED, "  Q\n    <R>\n  S");
    assertBlock(result, 2, PARAGRAPH, "B");
  }

  @Test
  public void testPreformat4() {
    String comment = "  Q\n    <R>\n  S\n\nB";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, PRE_FORMATTED, "  Q\n    <R>\n  S");
    assertBlock(result, 1, PARAGRAPH, "B");
  }

  @Test
  public void testQuote1() {
    String comment = "> I'm happy\n > with quotes!\n\nSee above.";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(2);
    assertBlock(result, 0, QUOTE, "I'm happy\nwith quotes!");
    assertBlock(result, 1, PARAGRAPH, "See above.");
  }

  @Test
  public void testQuote2() {
    String comment = "See this said:\n\n > a quoted\n > string block\n\nOK?";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(3);
    assertBlock(result, 0, PARAGRAPH, "See this said:");
    assertBlock(result, 1, QUOTE, "a quoted\nstring block");
    assertBlock(result, 2, PARAGRAPH, "OK?");
  }

  @Test
  public void testNestedQuotes1() {
    String comment = " > > prior\n > \n > next\n";
    List<CommentFormatter.Block> result = CommentFormatter.parse(comment);

    assertThat(result).hasSize(1);

    // Note: block does not encode nesting.
    assertBlock(result, 0, QUOTE, "> prior\n\nnext");
  }
}
