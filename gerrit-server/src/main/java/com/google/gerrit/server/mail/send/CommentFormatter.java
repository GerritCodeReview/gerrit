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

import com.google.gerrit.common.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class CommentFormatter {
  public enum BlockType {
    LIST,
    PARAGRAPH,
    PRE_FORMATTED,
    QUOTE
  }

  public static class Block {
    public BlockType type;
    public String text;
    public List<String> items;
  }

  /**
   * Take a strimg of comment text that was written using the Wiki-Like format
   * and emit a list of blocks that can be rendered to block-level HTML. This
   * method does not escape HTML.
   *
   * Adapted from the {@code wikify} method found in:
   *   com.google.gwtexpui.safehtml.client.SafeHtml
   *
   * @param source The raw, unescaped comment in the Gerrit wiki-like format.
   * @return List of block objects, each with unescaped comment content.
   */
  public static List<Block> parse(@Nullable String source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }

    List<Block> result = new ArrayList<>();
    for (final String p : source.split("\n\n")) {
      if (isQuote(p)) {
        result.add(makeQuote(p));
      } else if (isPreFormat(p)) {
        result.add(makePre(p));
      } else if (isList(p)) {
        makeList(p, result);
      } else if (!p.isEmpty()) {
        result.add(makeParagraph(p));
      }
    }
    return result;
  }

  /**
   * Take a block of comment text that contains a list and potentially
   * paragraphs (but does not contain blank lines), generate appropriate block
   * elements and append them to the output list.
   *
   * In simple cases, this will generate a single list block. For example, on
   * the following input.
   *
   *    * Item one.
   *    * Item two.
   *    * item three.
   *
   * However, if the list is adjacent to a paragraph, it will need to also
   * generate that paragraph. Consider the following input.
   *
   *    A bit of text describing the context of the list:
   *    * List item one.
   *    * List item two.
   *    * Et cetera.
   *
   * In this case, {@code makeList} generates a paragraph block object
   * containing the non-bullet-prefixed text, followed by a list block.
   *
   * Adapted from the {@code wikifyList} method found in:
   *   com.google.gwtexpui.safehtml.client.SafeHtml
   *
   * @param p The block containing the list (as well as potential paragraphs).
   * @param out The list of blocks to append to.
   */
  private static void makeList(String p, List<Block> out) {
    Block block = null;
    StringBuilder textBuilder = null;
    boolean inList = false;
    boolean inParagraph = false;

    for (String line : p.split("\n")) {
      if (line.startsWith("-") || line.startsWith("*")) {
        if (!inList) {
          if (inParagraph) {
            inParagraph = false;
            block.text = textBuilder.toString();
            out.add(block);
          }

          inList = true;
          block = new Block();
          block.type = BlockType.LIST;
          block.items = new ArrayList<>();
        }
        line = line.substring(1).trim();

      } else if (!inList) {
        if (!inParagraph) {
          inParagraph = true;
          block = new Block();
          block.type = BlockType.PARAGRAPH;
          textBuilder = new StringBuilder();
        } else {
          textBuilder.append(" ");
        }
        textBuilder.append(line);
        continue;
      }

      block.items.add(line);
    }

    if (block != null) {
      out.add(block);
    }
  }

  private static Block makeQuote(String p) {
    Block block = new Block();
    block.type = BlockType.QUOTE;
    block.text = p.substring(2).replaceAll("\n\\s?>\\s", "\n").trim();
    return block;
  }

  private static Block makePre(String p) {
    Block block = new Block();
    block.type = BlockType.PRE_FORMATTED;
    block.text = p;
    return block;
  }

  private static Block makeParagraph(String p) {
    Block block = new Block();
    block.type = BlockType.PARAGRAPH;
    block.text = p;
    return block;
  }

  private static boolean isQuote(final String p) {
    return p.startsWith("> ") || p.startsWith(" > ");
  }

  private static boolean isPreFormat(final String p) {
    return p.contains("\n ") || p.contains("\n\t") || p.startsWith(" ")
        || p.startsWith("\t");
  }

  private static boolean isList(final String p) {
    return p.contains("\n- ") || p.contains("\n* ") || p.startsWith("- ")
        || p.startsWith("* ");
  }
}
