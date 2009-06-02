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

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLTable;
import com.google.gwt.user.client.ui.HasHTML;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.Widget;

/** Immutable string safely placed as HTML without further escaping. */
public abstract class SafeHtml {
  /** @return the existing HTML property of a widget. */
  public static SafeHtml get(final HasHTML t) {
    return new SafeHtmlString(t.getHTML());
  }

  /** @return the existing HTML text, wrapped in a safe buffer. */
  public static SafeHtml asis(final String htmlText) {
    return new SafeHtmlString(htmlText);
  }

  /** Set the HTML property of a widget. */
  public static <T extends HasHTML> T set(final T e, final SafeHtml str) {
    e.setHTML(str.asString());
    return e;
  }

  /** @return the existing inner HTML of any element. */
  public static SafeHtml get(final Element e) {
    return new SafeHtmlString(DOM.getInnerHTML(e));
  }

  /** Set the inner HTML of any element. */
  public static Element set(final Element e, final SafeHtml str) {
    DOM.setInnerHTML(e, str.asString());
    return e;
  }

  /** @return the existing inner HTML of a table cell. */
  public static SafeHtml get(final HTMLTable t, final int row, final int col) {
    return new SafeHtmlString(t.getHTML(row, col));
  }

  /** Set the inner HTML of a table cell. */
  public static <T extends HTMLTable> T set(final T t, final int row,
      final int col, final SafeHtml str) {
    t.setHTML(row, col, str.asString());
    return t;
  }

  /** Parse an HTML block and return the first (typically root) element. */
  public static Element parse(final SafeHtml str) {
    return DOM.getFirstChild(set(DOM.createDiv(), str));
  }

  /** Convert bare http:// and https:// URLs into &lt;a href&gt; tags. */
  public SafeHtml linkify() {
    return replaceAll(
        "(https?://" +
          "[a-zA-Z0-9$_.+!*',%;:@&=?#/-]{2,}" +
          "([(]" +
          "[a-zA-Z0-9$_.+!*',%;:@&=?#/-]*" +
          "[)])*" +
          "[a-zA-Z0-9$_.+!*',%;:@&=?#/-]*" +
        ")",
        "<a href=\"$1\">$1</a>");
  }

  /**
   * Apply {@link #linkify()}, and "\n\n" to &lt;p&gt;.
   * <p>
   * Lines that start with whitespace are assumed to be preformatted, and are
   * formatted by the <code>gwtexpui-SafeHtml-WikiPreFormat</code> CSS class. By
   * default this class is:
   * 
   * <pre>
   *   white-space: pre;
   *   font-family: monospace;
   * </pre>
   */
  public SafeHtml wikify() {
    final SafeHtmlBuilder r = new SafeHtmlBuilder();
    for (final String p : linkify().asString().split("\n\n")) {
      if (isPreFormat(p)) {
        r.openElement("p");
        for (final String line : p.split("\n")) {
          r.openSpan();
          r.setStyleName("gwtexpui-SafeHtml-WikiPreFormat");
          r.append(new SafeHtmlString(line));
          r.closeSpan();
          r.br();
        }
        r.closeElement("p");

      } else if (isList(p)) {
        r.openElement("ul");
        r.setStyleName("gwtexpui-SafeHtml-WikiList");
        for (String line : p.split("\n")) {
          if (line.startsWith("-") || line.startsWith("*")) {
            line = line.substring(1).trim();
          }
          r.openElement("li");
          r.append(new SafeHtmlString(line));
          r.closeElement("li");
        }
        r.closeElement("ul");

      } else {
        r.openElement("p");
        r.append(new SafeHtmlString(p));
        r.closeElement("p");
      }
    }
    return r.toSafeHtml();
  }

  private static boolean isPreFormat(final String p) {
    return p.contains("\n ") || p.contains("\n\t") || p.startsWith(" ")
        || p.startsWith("\t");
  }

  private static boolean isList(final String p) {
    return p.contains("\n- ") || p.contains("\n* ") || p.startsWith("- ")
        || p.startsWith("* ");
  }

  /**
   * Replace first occurrence of <code>regex</code> with <code>repl</code> .
   * <p>
   * <b>WARNING:</b> This replacement is being performed against an otherwise
   * safe HTML string. The caller must ensure that the replacement does not
   * introduce cross-site scripting attack entry points.
   * 
   * @param regex regular expression pattern to match the substring with.
   * @param repl replacement expression. Capture groups within
   *        <code>regex</code> can be referenced with <code>$<i>n</i></code>.
   * @return a new string, after the replacement has been made.
   */
  public SafeHtml replaceFirst(final String regex, final String repl) {
    return new SafeHtmlString(asString().replaceFirst(regex, repl));
  }

  /**
   * Replace each occurrence of <code>regex</code> with <code>repl</code> .
   * <p>
   * <b>WARNING:</b> This replacement is being performed against an otherwise
   * safe HTML string. The caller must ensure that the replacement does not
   * introduce cross-site scripting attack entry points.
   * 
   * @param regex regular expression pattern to match substrings with.
   * @param repl replacement expression. Capture groups within
   *        <code>regex</code> can be referenced with <code>$<i>n</i></code>.
   * @return a new string, after the replacements have been made.
   */
  public SafeHtml replaceAll(final String regex, final String repl) {
    return new SafeHtmlString(asString().replaceAll(regex, repl));
  }

  /** @return a GWT block display widget displaying this HTML. */
  public Widget toBlockWidget() {
    return new HTML(asString());
  }

  /** @return a GWT inline display widget displaying this HTML. */
  public Widget toInlineWidget() {
    return new InlineHTML(asString());
  }

  /** @return a clean HTML string safe for inclusion in any context. */
  abstract String asString();
}
