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

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.ReplaceEdit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class PrettyFormatter {
  public static abstract class EditFilter {
    protected abstract int getBegin(Edit e);

    protected abstract int getEnd(Edit e);

    protected abstract String getStyleName();

    protected final boolean in(int line, Edit e) {
      return getBegin(e) <= line && line < getEnd(e);
    }

    protected final boolean after(int line, Edit e) {
      return getEnd(e) < line;
    }
  }

  public static final EditFilter A = new EditFilter() {
    @Override
    protected String getStyleName() {
      return "wdd";
    }

    @Override
    protected int getBegin(Edit e) {
      return e.getBeginA();
    }

    @Override
    protected int getEnd(Edit e) {
      return e.getEndA();
    }
  };

  public static final EditFilter B = new EditFilter() {
    @Override
    protected String getStyleName() {
      return "wdi";
    }

    @Override
    protected int getBegin(Edit e) {
      return e.getBeginB();
    }

    @Override
    protected int getEnd(Edit e) {
      return e.getEndB();
    }
  };

  protected List<String> lines = Collections.emptyList();
  protected EditFilter side = A;
  protected List<Edit> lineEdits = Collections.emptyList();
  protected PrettySettings settings;

  private int col;
  private int line;
  private Tag lastTag;
  private StringBuilder buf;

  /** @return the line of formatted HTML. */
  public SafeHtml getLine(int lineNo) {
    return SafeHtml.asis(lines.get(lineNo));
  }

  /** @return the number of lines in this formatter. */
  public int size() {
    return lines.size();
  }

  public void setEditFilter(EditFilter f) {
    side = f;
  }

  public void setEditList(List<Edit> all) {
    lineEdits = all;
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

    String html = toHTML(srcText);
    if (settings.isSyntaxHighlighting()) {
      html = prettify(html);
    } else {
      html = html.replaceAll("\n", "<br />");
    }

    int pos = 0;
    int textChunkStart = 0;

    lastTag = Tag.NULL;
    col = 0;
    line = 0;

    buf = new StringBuilder();
    while (pos <= html.length()) {
      int tagStart = html.indexOf('<', pos);

      if (tagStart < 0) {
        // No more tags remaining. What's left is plain text.
        //
        assert lastTag == Tag.NULL;
        pos = html.length();
        if (textChunkStart < pos) {
          htmlText(html.substring(textChunkStart, pos));
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
        htmlText(html.substring(textChunkStart, tagStart));
      }
      textChunkStart = pos;

      if (isBR(html, tagStart, tagEnd)) {
        lastTag.close(buf, html);
        lines.add(buf.toString());
        buf = new StringBuilder();
        col = 0;
        line++;

      } else if (html.charAt(tagStart + 1) == '/') {
        lastTag = lastTag.pop(buf, html);

      } else if (html.charAt(tagEnd - 1) != '/') {
        lastTag = new Tag(lastTag, tagStart, tagEnd);
      }
    }
    buf = null;
  }

  private void htmlText(String txt) {
    int pos = 0;
    while (pos < txt.length()) {
      int start = txt.indexOf('&', pos);
      if (start < 0) {
        break;
      }

      cleanText(txt, pos, start);
      pos = txt.indexOf(';', start + 1) + 1;

      if (settings.getLineLength() <= col) {
        buf.append("<br />");
        col = 0;
      }

      buf.append(txt.substring(start, pos));
      col++;
    }

    cleanText(txt, pos, txt.length());
  }

  private void cleanText(String txt, int pos, int end) {
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
    SafeHtml html = colorLineEdits(src);

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

  private SafeHtml colorLineEdits(String src) {
    SafeHtmlBuilder buf = new SafeHtmlBuilder();

    int lIdx = 0;
    Edit lCur = lIdx < lineEdits.size() ? lineEdits.get(lIdx) : null;

    int pos = 0;
    int line = 0;
    while (pos < src.length()) {
      if (lCur instanceof ReplaceEdit && side.in(line, lCur)) {
        List<Edit> wordEdits = ((ReplaceEdit) lCur).getInternalEdits();
        if (!wordEdits.isEmpty()) {
          // Copy the result using the word edits to guide us.
          //

          int last = 0;
          for (Edit w : wordEdits) {
            int b = side.getBegin(w);
            int e = side.getEnd(w);

            // If there is text between edits, copy it as-is.
            //
            int cnt = b - last;
            if (0 < cnt) {
              buf.append(src.substring(pos, pos + cnt));
              pos += cnt;
              last = b;
            }

            // If this is an edit, wrap it in a span.
            //
            cnt = e - b;
            if (0 < cnt) {
              buf.openSpan();
              buf.setStyleName(side.getStyleName());
              buf.append(src.substring(pos, pos + cnt));
              buf.closeSpan();
              pos += cnt;
              last = e;
            }
          }

          // We've consumed the entire region, so we are on the end.
          // Fall through, what's left of this edit is only the tail
          // of the final line.
          //
          line = side.getEnd(lCur) - 1;
        }
      }

      int lf = src.indexOf('\n', pos);
      if (lf < 0)
        lf = src.length();
      else
        lf++;

      buf.append(src.substring(pos, lf));
      pos = lf;
      line++;

      if (lCur != null && side.after(line, lCur)) {
        lIdx++;
        lCur = lIdx < lineEdits.size() ? lineEdits.get(lIdx) : null;
      }
    }
    return buf;
  }

  private SafeHtml showTabAfterSpace(SafeHtml src) {
    final String m = "( ( |<span[^>]*>|</span>)*\t)";
    final String r = "<span class=\"wse\">$1</span>";
    src = src.replaceFirst("^" + m, r);
    src = src.replaceAll("\n" + m, "\n" + r);
    return src;
  }

  private SafeHtml showTrailingWhitespace(SafeHtml src) {
    final String r = "<span class=\"wse\">$1</span>$2";
    src = src.replaceAll("([ \t][ \t]*)(\r?(</span>)?\n)", r);
    src = src.replaceFirst("([ \t][ \t]*)(\r?(</span>)?\n?)$", r);
    return src;
  }
}
