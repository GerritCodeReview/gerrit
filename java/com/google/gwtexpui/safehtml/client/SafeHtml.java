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
import com.google.gwt.dom.client.Element;
import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLTable;
import com.google.gwt.user.client.ui.HasHTML;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.Widget;
import java.util.Iterator;
import java.util.List;

/** Immutable string safely placed as HTML without further escaping. */
@SuppressWarnings("serial")
public abstract class SafeHtml implements com.google.gwt.safehtml.shared.SafeHtml {
  public static final SafeHtmlResources RESOURCES;

  static {
    if (GWT.isClient()) {
      RESOURCES = GWT.create(SafeHtmlResources.class);
      RESOURCES.css().ensureInjected();

    } else {
      RESOURCES =
          new SafeHtmlResources() {
            @Override
            public SafeHtmlCss css() {
              return new SafeHtmlCss() {
                @Override
                public String wikiList() {
                  return "wikiList";
                }

                @Override
                public String wikiPreFormat() {
                  return "wikiPreFormat";
                }

                @Override
                public String wikiQuote() {
                  return "wikiQuote";
                }

                @Override
                public boolean ensureInjected() {
                  return false;
                }

                @Override
                public String getName() {
                  return null;
                }

                @Override
                public String getText() {
                  return null;
                }
              };
            }
          };
    }
  }

  /** @return the existing HTML property of a widget. */
  public static SafeHtml get(HasHTML t) {
    return new SafeHtmlString(t.getHTML());
  }

  /** @return the existing HTML text, wrapped in a safe buffer. */
  public static SafeHtml asis(String htmlText) {
    return new SafeHtmlString(htmlText);
  }

  /** Set the HTML property of a widget. */
  public static <T extends HasHTML> T set(T e, SafeHtml str) {
    e.setHTML(str.asString());
    return e;
  }

  /** @return the existing inner HTML of any element. */
  public static SafeHtml get(Element e) {
    return new SafeHtmlString(e.getInnerHTML());
  }

  /** Set the inner HTML of any element. */
  public static Element setInnerHTML(Element e, SafeHtml str) {
    e.setInnerHTML(str.asString());
    return e;
  }

  /** @return the existing inner HTML of a table cell. */
  public static SafeHtml get(HTMLTable t, int row, int col) {
    return new SafeHtmlString(t.getHTML(row, col));
  }

  /** Set the inner HTML of a table cell. */
  public static <T extends HTMLTable> T set(final T t, int row, int col, SafeHtml str) {
    t.setHTML(row, col, str.asString());
    return t;
  }

  /** Parse an HTML block and return the first (typically root) element. */
  public static Element parse(SafeHtml html) {
    Element e = DOM.createDiv();
    setInnerHTML(e, html);
    return DOM.getFirstChild(e);
  }

  /** Convert bare http:// and https:// URLs into &lt;a href&gt; tags. */
  public SafeHtml linkify() {
    final String part = "(?:[a-zA-Z0-9$_+!*'%;:@=?#/~-]|&(?!lt;|gt;)|[.,](?!(?:\\s|$)))";
    return replaceAll(
        "(https?://" + part + "{2,}(?:[(]" + part + "*[)])*" + part + "*)",
        "<a href=\"$1\" target=\"_blank\" rel=\"nofollow\">$1</a>");
  }

  /**
   * Apply {@link #linkify()}, and "\n\n" to &lt;p&gt;.
   *
   * <p>Lines that start with whitespace are assumed to be preformatted, and are formatted by the
   * {@link SafeHtmlCss#wikiPreFormat()} CSS class.
   */
  public SafeHtml wikify() {
    final SafeHtmlBuilder r = new SafeHtmlBuilder();
    for (String p : linkify().asString().split("\n\n")) {
      if (isQuote(p)) {
        wikifyQuote(r, p);

      } else if (isPreFormat(p)) {
        r.openElement("p");
        for (String line : p.split("\n")) {
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

  private void wikifyList(SafeHtmlBuilder r, String p) {
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

  private void wikifyQuote(SafeHtmlBuilder r, String p) {
    r.openElement("blockquote");
    r.setStyleName(RESOURCES.css().wikiQuote());
    if (p.startsWith("&gt; ")) {
      p = p.substring(5);
    } else if (p.startsWith(" &gt; ")) {
      p = p.substring(6);
    }
    p = p.replaceAll("\\n ?&gt; ", "\n");
    for (String e : p.split("\n\n")) {
      if (isQuote(e)) {
        SafeHtmlBuilder b = new SafeHtmlBuilder();
        wikifyQuote(b, e);
        r.append(b);
      } else {
        r.append(asis(e));
      }
    }
    r.closeElement("blockquote");
  }

  private static boolean isQuote(String p) {
    return p.startsWith("&gt; ") || p.startsWith(" &gt; ");
  }

  private static boolean isPreFormat(String p) {
    return p.contains("\n ") || p.contains("\n\t") || p.startsWith(" ") || p.startsWith("\t");
  }

  private static boolean isList(String p) {
    return p.contains("\n- ") || p.contains("\n* ") || p.startsWith("- ") || p.startsWith("* ");
  }

  /**
   * Replace first occurrence of {@code regex} with {@code repl} .
   *
   * <p><b>WARNING:</b> This replacement is being performed against an otherwise safe HTML string.
   * The caller must ensure that the replacement does not introduce cross-site scripting attack
   * entry points.
   *
   * @param regex regular expression pattern to match the substring with.
   * @param repl replacement expression. Capture groups within {@code regex} can be referenced with
   *     {@code $<i>n</i>}.
   * @return a new string, after the replacement has been made.
   */
  public SafeHtml replaceFirst(String regex, String repl) {
    return new SafeHtmlString(asString().replaceFirst(regex, repl));
  }

  /**
   * Replace each occurrence of {@code regex} with {@code repl} .
   *
   * <p><b>WARNING:</b> This replacement is being performed against an otherwise safe HTML string.
   * The caller must ensure that the replacement does not introduce cross-site scripting attack
   * entry points.
   *
   * @param regex regular expression pattern to match substrings with.
   * @param repl replacement expression. Capture groups within {@code regex} can be referenced with
   *     {@code $<i>n</i>}.
   * @return a new string, after the replacements have been made.
   */
  public SafeHtml replaceAll(String regex, String repl) {
    return new SafeHtmlString(asString().replaceAll(regex, repl));
  }

  /**
   * Replace all find/replace pairs in the list in a single pass.
   *
   * @param findReplaceList find/replace pairs to use.
   * @return a new string, after the replacements have been made.
   */
  public <T> SafeHtml replaceAll(List<? extends FindReplace> findReplaceList) {
    if (findReplaceList == null || findReplaceList.isEmpty()) {
      return this;
    }

    StringBuilder pat = new StringBuilder();
    Iterator<? extends FindReplace> it = findReplaceList.iterator();
    while (it.hasNext()) {
      FindReplace fr = it.next();
      pat.append(fr.pattern().getSource());
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
      for (FindReplace fr : findReplaceList) {
        if (fr.pattern().test(g)) {
          try {
            String repl = fr.replace(g);
            result.append(orig.substring(index, mat.getIndex()));
            result.append(repl);
          } catch (IllegalArgumentException e) {
            continue;
          }
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
  @Override
  public abstract String asString();
}
