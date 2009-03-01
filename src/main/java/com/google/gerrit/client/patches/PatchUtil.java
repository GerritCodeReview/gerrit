// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.patches;

import com.google.gwt.core.client.GWT;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtjsonrpc.client.JsonUtil;

public class PatchUtil {
  public static final PatchConstants C = GWT.create(PatchConstants.class);
  public static final PatchMessages M = GWT.create(PatchMessages.class);
  public static final PatchDetailService DETAIL_SVC;
  public static final int DEFAULT_LINE_LENGTH = 100;

  static {
    DETAIL_SVC = GWT.create(PatchDetailService.class);
    JsonUtil.bind(DETAIL_SVC, "rpc/PatchDetailService");
  }

  public static SafeHtml lineToSafeHtml(final String src, final int lineLength,
      final boolean showWhiteSpaceErrors) {
    final boolean hasTab = src.indexOf('\t') >= 0;
    String brokenSrc = wrapLines(src, hasTab, lineLength);
    SafeHtml html = new SafeHtmlBuilder().append(brokenSrc);
    if (showWhiteSpaceErrors) {
      html = showTabAfterSpace(html);
      html = showTrailingWhitespace(html);
    }
    if (brokenSrc != src) {
      // If we had line breaks inserted into the source text we need
      // to expand the line breaks into <br> tags in HTML, so the
      // line will wrap around.
      //
      html = expandLFs(html);
    }
    if (hasTab) {
      // We had at least one horizontal tab, so we should expand it out.
      //
      html = expandTabs(html);
    }
    return html;
  }

  private static String wrapLines(final String src, final boolean hasTabs,
      final int lineLength) {
    if (lineLength <= 0) {
      // Caller didn't request for line wrapping; use it unmodified.
      //
      return src;
    }
    if (src.length() < lineLength && !hasTabs) {
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

  private static SafeHtml expandTabs(SafeHtml src) {
    return src
        .replaceAll("\t",
            "<span title=\"Visual Tab\" class=\"gerrit-visualtab\">&raquo;</span>\t");
  }

  private static SafeHtml expandLFs(SafeHtml src) {
    return src.replaceAll("\n", "<br />");
  }

  private static SafeHtml showTabAfterSpace(SafeHtml src) {
    return src.replaceFirst("^(  *\t)",
        "<span class=\"gerrit-whitespaceerror\">$1</span>");
  }

  private static SafeHtml showTrailingWhitespace(SafeHtml src) {
    return src.replaceFirst("([ \t][ \t]*)(\r?\n?)$",
        "<span class=\"gerrit-whitespaceerror\">$1</span>$2");
  }
}
