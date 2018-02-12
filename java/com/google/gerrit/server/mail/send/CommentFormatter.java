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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Splitter;
import com.google.gerrit.common.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public List<String> items; // For the items of list blocks.
    public List<Block> quotedBlocks; // For the contents of quote blocks.
  }

  /**
   * Take a string of comment text that was written using the wiki-Like format and emit a list of
   * blocks that can be rendered to block-level HTML. This method does not escape HTML.
   *
   * <p>Adapted from the {@code wikify} method found in:
   * com.google.gwtexpui.safehtml.client.SafeHtml
   *
   * @param source The raw, unescaped comment in the Gerrit wiki-like format.
   * @return List of block objects, each with unescaped comment content.
   */
  public static List<Block> parse(@Nullable String source) {
    if (isNullOrEmpty(source)) {
      return Collections.emptyList();
    }

    List<Block> result = new ArrayList<>();
    for (String p : Splitter.on("\n\n").split(source)) {
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
   * Take a block of comment text that contains a list and potentially paragraphs (but does not
   * contain blank lines), generate appropriate block elements and append them to the output list.
   *
   * <p>In simple cases, this will generate a single list block. For example, on the following
   * input.
   *
   * <p>* Item one. * Item two. * item three.
   *
   * <p>However, if the list is adjacent to a paragraph, it will need to also generate that
   * paragraph. Consider the following input.
   *
   * <p>A bit of text describing the context of the list: * List item one. * List item two. * Et
   * cetera.
   *
   * <p>In this case, {@code makeList} generates a paragraph block object containing the
   * non-bullet-prefixed text, followed by a list block.
   *
   * <p>Adapted from the {@code wikifyList} method found in:
   * com.google.gwtexpui.safehtml.client.SafeHtml
   *
   * @param p The block containing the list (as well as potential paragraphs).
   * @param out The list of blocks to append to.
   */
  private static void makeList(String p, List<Block> out) {
    Block block = null;
    StringBuilder textBuilder = null;
    boolean inList = false;
    boolean inParagraph = false;

    for (String line : Splitter.on('\n').split(p)) {
      if (line.startsWith("-") || line.startsWith("*")) {
        // The next line looks like a list item. If not building a list already,
        // then create one. Remove the list item marker (* or -) from the line.
        if (!inList) {
          if (inParagraph) {
            // Add the finished paragraph block to the result.
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
        // Otherwise, if a list has not yet been started, but the next line does
        // not look like a list item, then add the line to a paragraph block. If
        // a paragraph block has not yet been started, then create one.
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
    String quote = p.replaceAll("\n\\s?>\\s?", "\n");
    if (quote.startsWith("> ")) {
      quote = quote.substring(2);
    } else if (quote.startsWith(" > ")) {
      quote = quote.substring(3);
    }

    Block block = new Block();
    block.type = BlockType.QUOTE;
    block.quotedBlocks = CommentFormatter.parse(quote);
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

  private static boolean isQuote(String p) {
    return p.startsWith("> ") || p.startsWith(" > ");
  }

  private static boolean isPreFormat(String p) {
    return p.startsWith(" ") || p.startsWith("\t") || p.contains("\n ") || p.contains("\n\t");
  }

  private static boolean isList(String p) {
    return p.startsWith("- ") || p.startsWith("* ") || p.contains("\n- ") || p.contains("\n* ");
  }
}
