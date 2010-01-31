// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.prettify.common;

import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class PrettyFormatter {
  protected List<String> lines = Collections.emptyList();
  protected PrettySettings settings;

  /** @return the line of formatted HTML. */
  public SafeHtml getLine(int lineNo) {
    return SafeHtml.asis(lines.get(lineNo));
  }

  /** @return the number of lines in this formatter. */
  public int size() {
    return lines.size();
  }

  /**
   * Parse and format a complete source code file.
   *
   * @param how the settings to apply to the formatter.
   * @param srcText raw content of the file to format. The string will be HTML
   *        escaped before processing, so it must be the raw text.
   */
  public void format(PrettySettings how, String srcText) {
    settings = how;
    lines = new ArrayList<String>();

    String html = prettify(toHTML(srcText));
    int pos = 0;
    int textChunkStart = 0;
    int col = 0;
    Tag lastTag = Tag.NULL;

    StringBuilder buf = new StringBuilder();
    while (pos <= html.length()) {
      int tagStart = html.indexOf('<', pos);

      if (tagStart < 0) {
        // No more tags remaining. What's left is plain text.
        //
        assert lastTag == Tag.NULL;
        pos = html.length();
        if (textChunkStart < pos) {
          col = htmlText(col, buf, html.substring(textChunkStart, pos));
        }
        if (0 < buf.length()) {
          lines.add(buf.toString());
        }
        break;
      }

      // Assume no attribute contains '>' and that all tags
      // within the HTML will be well-formed.
      //
      int tagEnd = html.indexOf('>', tagStart);
      assert tagStart < tagEnd;
      pos = tagEnd + 1;

      // Handle any text between the end of the last tag,
      // and the start of this tag.
      //
      if (textChunkStart < tagStart) {
        lastTag.open(buf, html);
        col = htmlText(col, buf, html.substring(textChunkStart, tagStart));
      }
      textChunkStart = pos;

      if (isBR(html, tagStart, tagEnd)) {
        lastTag.close(buf, html);
        lines.add(buf.toString());
        buf = new StringBuilder();
        col = 0;

      } else if (html.charAt(tagStart + 1) == '/') {
        lastTag = lastTag.pop(buf, html);

      } else if (html.charAt(tagEnd - 1) != '/') {
        lastTag = new Tag(lastTag, tagStart, tagEnd);
      }
    }
  }

  private int htmlText(int col, StringBuilder buf, String txt) {
    int pos = 0;

    while (pos < txt.length()) {
      int start = txt.indexOf('&', pos);
      if (start < 0) {
        break;
      }

      col = cleanText(col, buf, txt, pos, start);
      pos = txt.indexOf(';', start + 1) + 1;

      if (settings.getLineLength() <= col) {
        buf.append("<br />");
        col = 0;
      }

      buf.append(txt.substring(start, pos));
      col++;
    }

    return cleanText(col, buf, txt, pos, txt.length());
  }

  private int cleanText(int col, StringBuilder buf, String txt, int pos, int end) {
    while (pos < end) {
      int free = settings.getLineLength() - col;
      if (free <= 0) {
        // The current line is full. Throw an explicit line break
        // onto the end, and we'll continue on the next line.
        //
        buf.append("<br />");
        col = 0;
        free = settings.getLineLength();
      }

      int n = Math.min(end - pos, free);
      buf.append(txt.substring(pos, pos + n));
      col += n;
      pos += n;
    }
    return col;
  }

  /** Run the prettify engine over the text and return the result. */
  protected abstract String prettify(String html);

  private static boolean isBR(String html, int tagStart, int tagEnd) {
    return tagEnd - tagStart == 5 //
        && html.charAt(tagStart + 1) == 'b' //
        && html.charAt(tagStart + 2) == 'r' //
        && html.charAt(tagStart + 3) == ' ';
  }

  private static class Tag {
    static final Tag NULL = new Tag(null, 0, 0) {
      @Override
      void open(StringBuilder buf, String html) {
      }

      @Override
      void close(StringBuilder buf, String html) {
      }

      @Override
      Tag pop(StringBuilder buf, String html) {
        return this;
      }
    };

    final Tag parent;
    final int start;
    final int end;
    boolean open;

    Tag(Tag p, int s, int e) {
      parent = p;
      start = s;
      end = e;
    }

    void open(StringBuilder buf, String html) {
      if (!open) {
        parent.open(buf, html);
        buf.append(html.substring(start, end + 1));
        open = true;
      }
    }

    void close(StringBuilder buf, String html) {
      pop(buf, html);
      parent.close(buf, html);
    }

    Tag pop(StringBuilder buf, String html) {
      if (open) {
        int sp = html.indexOf(' ', start + 1);
        if (sp < 0 || end < sp) {
          sp = end;
        }

        buf.append("</");
        buf.append(html.substring(start + 1, sp));
        buf.append('>');
        open = false;
      }
      return parent;
    }
  }

  private String toHTML(String src) {
    SafeHtml html = new SafeHtmlBuilder().append(src);

    // The prettify parsers don't like &#39; as an entity for the
    // single quote character. Replace them all out so we don't
    // confuse the parser.
    //
    html = html.replaceAll("&#39;", "'");

    if (settings.isShowWhiteSpaceErrors()) {
      // We need to do whitespace errors before showing tabs, because
      // these patterns rely on \t as a literal, before it expands.
      //
      html = showTabAfterSpace(html);
      html = showTrailingWhitespace(html);
    }

    if (settings.isShowTabs()) {
      String t = 1 < settings.getTabSize() ? "\t" : "";
      html = html.replaceAll("\t", "<span class=\"vt\">&nbsp;</span>" + t);
    }

    return html.asString();
  }

  private SafeHtml showTabAfterSpace(SafeHtml src) {
    src = src.replaceFirst("^(  *\t)", "<span class=\"wse\">$1</span>");
    src = src.replaceAll("\n(  *\t)", "\n<span class=\"wse\">$1</span>");
    return src;
  }

  private SafeHtml showTrailingWhitespace(SafeHtml src) {
    final String r = "<span class=\"wse\">$1</span>$2";
    src = src.replaceAll("([ \t][ \t]*)(\r?\n)", r);
    src = src.replaceFirst("([ \t][ \t]*)(\r?\n?)$", r);
    return src;
  }
}
