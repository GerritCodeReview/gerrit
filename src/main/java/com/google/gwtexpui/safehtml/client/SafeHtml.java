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

import com.google.gwt.core.client.GWT;
import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLTable;
import com.google.gwt.user.client.ui.HasHTML;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Immutable string safely placed as HTML without further escaping. */
public abstract class SafeHtml {
  public static final SafeHtmlResources RESOURCES;

  static {
    if (GWT.isClient()) {
      RESOURCES = GWT.create(SafeHtmlResources.class);
      RESOURCES.css().ensureInjected();

    } else {
      RESOURCES = new SafeHtmlResources() {
        @Override
        public SafeHtmlCss css() {
          return new SafeHtmlCss() {
            public String wikiList() {
              return "wikiList";
            }

            public String wikiPreFormat() {
              return "wikiPreFormat";
            }

            public boolean ensureInjected() {
              return false;
            }

            public String getName() {
              return null;
            }

            public String getText() {
              return null;
            }
          };
        }
      };
    }
  }

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
    final String part = "(?:" +
		"[a-zA-Z0-9$_.+!*',%;:@=?#/~-]" +
		"|&(?!lt;|gt;)" +
		")";
    return replaceAll(
        "(https?://" +
          part + "{2,}" +
          "(?:[(]" + part + "*" + "[)])*" +
          part + "*" +
        ")",
        "<a href=\"$1\" target=\"_blank\">$1</a>");
  }

  /**
   * Apply {@link #linkify()}, and "\n\n" to &lt;p&gt;.
   * <p>
   * Lines that start with whitespace are assumed to be preformatted, and are
   * formatted by the {@link SafeHtmlCss#wikiPreFormat()} CSS class.
   */
  public SafeHtml wikify() {
    final SafeHtmlBuilder r = new SafeHtmlBuilder();
    for (final String p : linkify().asString().split("\n\n")) {
      if (isPreFormat(p)) {
        r.openElement("p");
        for (final String line : p.split("\n")) {
          r.openSpan();
          r.setStyleName(RESOURCES.css().wikiPreFormat());
          r.append(asis(line));
          r.closeSpan();
          r.br();
        }
        r.closeElement("p");

      } else if (isList(p)) {
        wikifyList(r, p);

      } else {
        r.openElement("p");
        r.append(asis(p));
        r.closeElement("p");
      }
    }
    return r.toSafeHtml();
  }

  private void wikifyList(final SafeHtmlBuilder r, final String p) {
    boolean in_ul = false;
    boolean in_p = false;
    for (String line : p.split("\n")) {
      if (line.startsWith("-") || line.startsWith("*")) {
        if (!in_ul) {
          if (in_p) {
            in_p = false;
            r.closeElement("p");
          }

          in_ul = true;
          r.openElement("ul");
          r.setStyleName(RESOURCES.css().wikiList());
        }
        line = line.substring(1).trim();

      } else if (!in_ul) {
        if (!in_p) {
          in_p = true;
          r.openElement("p");
        } else {
          r.append(' ');
        }
        r.append(asis(line));
        continue;
      }

      r.openElement("li");
      r.append(asis(line));
      r.closeElement("li");
    }

    if (in_ul) {
      r.closeElement("ul");
    } else if (in_p) {
      r.closeElement("p");
    }
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

  private static class RegExpReplacement {
    private final RegExp re;
    private final String repl;

    private RegExpReplacement(String pat, String repl) {
      this.re = RegExp.compile(pat);
      this.repl = repl;
    }
  }

  /**
   * Replace all find/replace pairs in the list in a single pass.
   *
   * @param findReplaceList find/replace pairs to use.
   * @return a new string, after the replacements have been made.
   */
  public SafeHtml replaceAll(final List<RegexFindReplace> findReplaceList) {
    if (findReplaceList == null) {
      return this;
    }
    List<RegExpReplacement> repls =
        new ArrayList<RegExpReplacement>(findReplaceList.size());

    StringBuilder pat = new StringBuilder();
    Iterator<RegexFindReplace> it = findReplaceList.iterator();
    while (it.hasNext()) {
      RegexFindReplace fr = it.next();
      repls.add(new RegExpReplacement(fr.find(), fr.replace()));
      pat.append(fr.find());
      if (it.hasNext()) {
        pat.append('|');
      }
    }

    StringBuilder result = new StringBuilder();
    RegExp re = RegExp.compile(pat.toString(), "g");
    String orig = asString();
    int index = 0;
    MatchResult mat;
    while ((mat = re.exec(orig)) != null) {
      String g = mat.getGroup(0);
      // Re-run each candidate to find which one matched.
      for (RegExpReplacement repl : repls) {
        if (repl.re.exec(g) != null) {
          result.append(orig.substring(index, mat.getIndex()));
          result.append(repl.re.replace(g, repl.repl));
          index = mat.getIndex() + g.length();
          break;
        }
      }
    }
    result.append(orig.substring(index, orig.length()));
    return asis(result.toString());
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
  public abstract String asString();
}
