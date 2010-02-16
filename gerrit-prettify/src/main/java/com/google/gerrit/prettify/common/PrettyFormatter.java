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

import java.util.List;

public abstract class PrettyFormatter implements SparseHtmlFile {
  public static abstract class EditFilter {
    final String get(SparseFileContent src, EditList.Hunk hunk) {
      return src.get(getCur(hunk));
    }

    abstract String getStyleName();

    abstract int getCur(EditList.Hunk hunk);

    abstract int getBegin(Edit edit);

    abstract int getEnd(Edit edit);

    abstract boolean isModified(EditList.Hunk hunk);

    abstract void incSelf(EditList.Hunk hunk);

    abstract void incOther(EditList.Hunk hunk);
  }

  public static final EditFilter A = new EditFilter() {
    @Override
    String getStyleName() {
      return "wdd";
    }

    @Override
    int getCur(EditList.Hunk hunk) {
      return hunk.getCurA();
    }

    @Override
    int getBegin(Edit edit) {
      return edit.getBeginA();
    }

    @Override
    int getEnd(Edit edit) {
      return edit.getEndA();
    }

    @Override
    boolean isModified(EditList.Hunk hunk) {
      return hunk.isDeletedA();
    }

    @Override
    void incSelf(EditList.Hunk hunk) {
      hunk.incA();
    }

    @Override
    void incOther(EditList.Hunk hunk) {
      hunk.incB();
    }
  };

  public static final EditFilter B = new EditFilter() {
    @Override
    String getStyleName() {
      return "wdi";
    }

    @Override
    int getCur(EditList.Hunk hunk) {
      return hunk.getCurB();
    }

    @Override
    int getBegin(Edit edit) {
      return edit.getBeginB();
    }

    @Override
    int getEnd(Edit edit) {
      return edit.getEndB();
    }

    @Override
    boolean isModified(EditList.Hunk hunk) {
      return hunk.isInsertedB();
    }

    @Override
    void incSelf(EditList.Hunk hunk) {
      hunk.incB();
    }

    @Override
    void incOther(EditList.Hunk hunk) {
      hunk.incA();
    }
  };

  protected SparseFileContent content;
  protected EditFilter side;
  protected EditList edits;
  protected PrettySettings settings;

  private int col;
  private int lineIdx;
  private Tag lastTag;
  private StringBuilder buf;

  public SafeHtml getSafeHtmlLine(int lineNo) {
    return SafeHtml.asis(content.get(lineNo));
  }

  public int size() {
    return content.size();
  }

  @Override
  public boolean contains(int idx) {
    return content.contains(idx);
  }

  public void setEditFilter(EditFilter f) {
    side = f;
  }

  public void setEditList(EditList all) {
    edits = all;
  }

  public void setPrettySettings(PrettySettings how) {
    settings = how;
  }

  /**
   * Parse and format a complete source code file.
   *
   * @param src raw content of the file to format. The line strings will be HTML
   *        escaped before processing, so it must be the raw text.
   */
  public void format(SparseFileContent src) {
    content = new SparseFileContent();
    content.setSize(src.size());

    String html = toHTML(src);

    if (settings.isSyntaxHighlighting() && getFileType() != null
        && src.isWholeFile()) {
      // The prettify parsers don't like &#39; as an entity for the
      // single quote character. Replace them all out so we don't
      // confuse the parser.
      //
      html = html.replaceAll("&#39;", "'");
      html = prettify(html, getFileType());

    } else {
      html = expandTabs(html);
      html = html.replaceAll("\n", "<br />");
    }

    int pos = 0;
    int textChunkStart = 0;

    lastTag = Tag.NULL;
    col = 0;
    lineIdx = 0;

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
          content.addLine(src.mapIndexToLine(lineIdx), buf.toString());
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
        content.addLine(src.mapIndexToLine(lineIdx), buf.toString());
        buf = new StringBuilder();
        col = 0;
        lineIdx++;

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
  protected abstract String prettify(String html, String type);

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

  private String toHTML(SparseFileContent src) {
    SafeHtml html = colorLineEdits(src);

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

  private SafeHtml colorLineEdits(SparseFileContent src) {
    SafeHtmlBuilder buf = new SafeHtmlBuilder();

    ReplaceEdit lastReplace = null;
    List<Edit> charEdits = null;
    int lastPos = 0;
    int lastIdx = 0;

    EditList hunkGenerator = edits;
    if (src.isWholeFile()) {
      hunkGenerator = hunkGenerator.getFullContext();
    }

    for (final EditList.Hunk hunk : hunkGenerator.getHunks()) {
      while (hunk.next()) {
        if (hunk.isContextLine()) {
          if (src.contains(side.getCur(hunk))) {
            // If side is B and src isn't the complete file we can't
            // add it to the buffer here. This can happen if the file
            // was really large and we chose not to syntax highlight.
            //
            buf.append(side.get(src, hunk));
            buf.append('\n');
          }
          hunk.incBoth();

        } else if (!side.isModified(hunk)) {
          side.incOther(hunk);

        } else if (hunk.getCurEdit() instanceof ReplaceEdit) {
          if (lastReplace != hunk.getCurEdit()) {
            lastReplace = (ReplaceEdit) hunk.getCurEdit();
            charEdits = lastReplace.getInternalEdits();
            lastPos = 0;
            lastIdx = 0;
          }

          final String line = side.get(src, hunk) + "\n";
          for (int c = 0; c < line.length();) {
            if (charEdits.size() <= lastIdx) {
              buf.append(line.substring(c));
              break;
            }

            final Edit edit = charEdits.get(lastIdx);
            final int b = side.getBegin(edit) - lastPos;
            final int e = side.getEnd(edit) - lastPos;

            if (c < b) {
              // There is text at the start of this line that is common
              // with the other side. Copy it with no style around it.
              //
              final int n = Math.min(b, line.length());
              buf.append(line.substring(c, n));
              c = n;
            }

            if (c < e) {
              final int n = Math.min(e, line.length());
              buf.openSpan();
              buf.setStyleName(side.getStyleName());
              buf.append(line.substring(c, n));
              buf.closeSpan();
              c = n;
            }

            if (e <= c) {
              lastIdx++;
            }
          }
          lastPos += line.length();
          side.incSelf(hunk);

        } else {
          buf.append(side.get(src, hunk));
          buf.append('\n');
          side.incSelf(hunk);
        }
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

  private String expandTabs(String html) {
    StringBuilder tmp = new StringBuilder();
    int i = 0;
    if (settings.isShowTabs()) {
      i = 1;
    }
    for (; i < settings.getTabSize(); i++) {
      tmp.append("&nbsp;");
    }
    return html.replaceAll("\t", tmp.toString());
  }

  private String getFileType() {
    String srcType = settings.getFilename();
    if (srcType == null) {
      return null;
    }

    int dot = srcType.lastIndexOf('.');
    if (0 < dot) {
      srcType = srcType.substring(dot + 1);
    }
    return srcType;
  }
}
