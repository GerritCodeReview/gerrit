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

package com.google.gwtexpui.safehtml.client;

import java.util.HashMap;
import java.util.Map;

public abstract class PrettyFormatter {
  private static final Map<String, MultiLineStyle> STYLES;
  private static final MultiLineStyle DEFAULT_STYLE = new MultiLineStyle() {};
  static {
    STYLES = new HashMap<String, MultiLineStyle>();

    MultiLineStyle c = new MultiLineStyle.Simple("/*", "*/");
    STYLES.put("h", c);
    STYLES.put("c", c);
    STYLES.put("cc", c);
    STYLES.put("cpp", c);
    STYLES.put("cxx", c);
    STYLES.put("cyc", c);
    STYLES.put("m", c);
    STYLES.put("cs", c);
    STYLES.put("java", c);
    STYLES.put("js", c);
    STYLES.put("css", c);
    STYLES.put("scala", c);

    MultiLineStyle xml = new MultiLineStyle.Simple("<!--", "-->");
    STYLES.put("xml", xml);
    STYLES.put("html", xml);
    STYLES.put("sgml", xml);
  }

  private static MultiLineStyle getCommentStyle(final String lang) {
    MultiLineStyle style = STYLES.get(lang);
    return style != null ? style : DEFAULT_STYLE;
  }

  public static PrettyFormatter newFormatter(String lang) {
    return Pretty.loaded ? new Pretty(lang) : new PassThrough();
  }

  private boolean showWhiteSpaceErrors;
  private int lineLength = 100;

  public void setShowWhiteSpaceErrors(final boolean show) {
    showWhiteSpaceErrors = show;
  }

  public void setLineLength(final int len) {
    lineLength = len;
  }

  public SafeHtml format(String line) {
    SafeHtml html = new SafeHtmlBuilder().append(wrapLines(line));
    if (showWhiteSpaceErrors) {
      html = showTabAfterSpace(html);
      html = showTrailingWhitespace(html);
    }
    html = prettify(html);
    return html;
  }

  public void update(String line) {
  }

  private String wrapLines(final String src) {
    if (lineLength <= 0) {
      // Caller didn't request for line wrapping; use it unmodified.
      //
      return src;
    }
    if (src.length() < lineLength && src.indexOf('\t') < 0) {
      // We're too short and there are no horizontal tabs, line is fine
      // as-is so bypass the longer line wrapping code below.
      return src;
    }

    final StringBuilder r = new StringBuilder();
    int lineLen = 0;
    for (int i = 0; i < src.length(); i++) {
      final char c = src.charAt(i);
      final int cLen = c == '\t' ? 8 : 1;
      if (lineLen >= lineLength) {
        r.append('\n');
        lineLen = 0;
      }
      r.append(c);
      lineLen += cLen;
    }
    return r.toString();
  }


  private static SafeHtml showTabAfterSpace(SafeHtml src) {
    return src.replaceFirst("^(  *\t)",
        "<span class=\"gerrit-whitespaceerror\">$1</span>");
  }

  private static SafeHtml showTrailingWhitespace(SafeHtml src) {
    return src.replaceFirst("([ \t][ \t]*)(\r?\n?)$",
        "<span class=\"gerrit-whitespaceerror\">$1</span>$2");
  }

  protected SafeHtml prettify(SafeHtml line) {
    return line;
  }

  private static class PassThrough extends PrettyFormatter {
  }

  private static class Pretty extends PrettyFormatter {
    static final boolean loaded = isLoaded();

    private static native boolean isLoaded()
    /*-{ return $wnd['prettyPrintOne'] != null }-*/;

    private final String srcType;
    private final MultiLineStyle commentStyle;
    private MultiLineStyle currentStyle;

    Pretty(final String lang) {
      srcType = lang;
      commentStyle = getCommentStyle(lang);
    }

    @Override
    protected SafeHtml prettify(final SafeHtml src) {
      String line = src.asString().replaceAll("&#39;", "'");

      if (currentStyle != null) {
        final boolean isEnd = currentStyle.isEnd(line);

        line = currentStyle.restart(line);
        line = prettifyNative(line, srcType);
        line = currentStyle.unrestart(line);

        if (isEnd) {
          currentStyle = null;
        }
      } else {
        currentStyle = commentStyle.isStart(line);
        line = prettifyNative(line, srcType);
      }

      return SafeHtml.asis(line);
    }

    @Override
    public void update(String line) {
      if (currentStyle != null) {
        if (currentStyle.isEnd(line)) {
          currentStyle = null;
        }
      } else {
        currentStyle = commentStyle.isStart(line);
      }
    }

    private static native String prettifyNative(String srcText, String srcType)
    /*-{ return $wnd.prettyPrintOne(srcText, srcType); }-*/;
  }
}
