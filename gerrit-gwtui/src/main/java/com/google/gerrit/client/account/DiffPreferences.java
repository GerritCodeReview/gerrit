// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.account;

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.client.Theme;
import com.google.gwt.core.client.JavaScriptObject;

public class DiffPreferences extends JavaScriptObject {
  public static DiffPreferences create(DiffPreferencesInfo in) {
    if (in == null) {
      in = DiffPreferencesInfo.defaults();
    }
    DiffPreferences p = createObject().cast();
    p.ignoreWhitespace(in.ignoreWhitespace);
    p.tabSize(in.tabSize);
    p.lineLength(in.lineLength);
    p.cursorBlinkRate(in.cursorBlinkRate);
    p.context(in.context);
    p.intralineDifference(in.intralineDifference);
    p.showLineEndings(in.showLineEndings);
    p.showTabs(in.showTabs);
    p.showWhitespaceErrors(in.showWhitespaceErrors);
    p.syntaxHighlighting(in.syntaxHighlighting);
    p.hideTopMenu(in.hideTopMenu);
    p.autoHideDiffTableHeader(in.autoHideDiffTableHeader);
    p.hideLineNumbers(in.hideLineNumbers);
    p.expandAllComments(in.expandAllComments);
    p.manualReview(in.manualReview);
    p.renderEntireFile(in.renderEntireFile);
    p.theme(in.theme);
    p.hideEmptyPane(in.hideEmptyPane);
    p.retainHeader(in.retainHeader);
    p.skipUnchanged(in.skipUnchanged);
    p.skipUncommented(in.skipUncommented);
    p.skipDeleted(in.skipDeleted);
    p.matchBrackets(in.matchBrackets);
    p.lineWrapping(in.lineWrapping);
    return p;
  }

  public final void copyTo(DiffPreferencesInfo p) {
    p.context = context();
    p.tabSize = tabSize();
    p.lineLength = lineLength();
    p.cursorBlinkRate = cursorBlinkRate();
    p.expandAllComments = expandAllComments();
    p.intralineDifference = intralineDifference();
    p.manualReview = manualReview();
    p.retainHeader = retainHeader();
    p.showLineEndings = showLineEndings();
    p.showTabs = showTabs();
    p.showWhitespaceErrors = showWhitespaceErrors();
    p.skipDeleted = skipDeleted();
    p.skipUnchanged = skipUnchanged();
    p.skipUncommented = skipUncommented();
    p.syntaxHighlighting = syntaxHighlighting();
    p.hideTopMenu = hideTopMenu();
    p.autoHideDiffTableHeader = autoHideDiffTableHeader();
    p.hideLineNumbers = hideLineNumbers();
    p.renderEntireFile = renderEntireFile();
    p.hideEmptyPane = hideEmptyPane();
    p.matchBrackets = matchBrackets();
    p.lineWrapping = lineWrapping();
    p.theme = theme();
    p.ignoreWhitespace = ignoreWhitespace();
  }

  public final void ignoreWhitespace(Whitespace i) {
    setIgnoreWhitespaceRaw(i.toString());
  }

  public final void theme(Theme i) {
    setThemeRaw(i != null ? i.toString() : Theme.DEFAULT.toString());
  }

  public final void showLineNumbers(boolean s) {
    hideLineNumbers(!s);
  }

  public final Whitespace ignoreWhitespace() {
    String s = ignoreWhitespaceRaw();
    return s != null ? Whitespace.valueOf(s) : Whitespace.IGNORE_NONE;
  }

  public final Theme theme() {
    String s = themeRaw();
    return s != null ? Theme.valueOf(s) : Theme.DEFAULT;
  }

  public final int tabSize() {
    return get("tab_size", 8);
  }

  public final int context() {
    return get("context", 10);
  }

  public final int lineLength() {
    return get("line_length", 100);
  }

  public final int cursorBlinkRate() {
    return get("cursor_blink_rate", 0);
  }

  public final boolean showLineNumbers() {
    return !hideLineNumbers();
  }

  public final boolean autoReview() {
    return !manualReview();
  }

  public final native void tabSize(int t) /*-{ this.tab_size = t }-*/;

  public final native void lineLength(int c) /*-{ this.line_length = c }-*/;

  public final native void context(int c) /*-{ this.context = c }-*/;

  public final native void cursorBlinkRate(int r) /*-{ this.cursor_blink_rate = r }-*/;

  public final native void intralineDifference(Boolean i) /*-{ this.intraline_difference = i }-*/;

  public final native void showLineEndings(Boolean s) /*-{ this.show_line_endings = s }-*/;

  public final native void showTabs(Boolean s) /*-{ this.show_tabs = s }-*/;

  public final native void showWhitespaceErrors(
      Boolean s) /*-{ this.show_whitespace_errors = s }-*/;

  public final native void syntaxHighlighting(Boolean s) /*-{ this.syntax_highlighting = s }-*/;

  public final native void hideTopMenu(Boolean s) /*-{ this.hide_top_menu = s }-*/;

  public final native void autoHideDiffTableHeader(
      Boolean s) /*-{ this.auto_hide_diff_table_header = s }-*/;

  public final native void hideLineNumbers(Boolean s) /*-{ this.hide_line_numbers = s }-*/;

  public final native void expandAllComments(Boolean e) /*-{ this.expand_all_comments = e }-*/;

  public final native void manualReview(Boolean r) /*-{ this.manual_review = r }-*/;

  public final native void renderEntireFile(Boolean r) /*-{ this.render_entire_file = r }-*/;

  public final native void retainHeader(Boolean r) /*-{ this.retain_header = r }-*/;

  public final native void hideEmptyPane(Boolean s) /*-{ this.hide_empty_pane = s }-*/;

  public final native void skipUnchanged(Boolean s) /*-{ this.skip_unchanged = s }-*/;

  public final native void skipUncommented(Boolean s) /*-{ this.skip_uncommented = s }-*/;

  public final native void skipDeleted(Boolean s) /*-{ this.skip_deleted = s }-*/;

  public final native void matchBrackets(Boolean m) /*-{ this.match_brackets = m }-*/;

  public final native void lineWrapping(Boolean w) /*-{ this.line_wrapping = w }-*/;

  public final native boolean
      intralineDifference() /*-{ return this.intraline_difference || false }-*/;

  public final native boolean showLineEndings() /*-{ return this.show_line_endings || false }-*/;

  public final native boolean showTabs() /*-{ return this.show_tabs || false }-*/;

  public final native boolean
      showWhitespaceErrors() /*-{ return this.show_whitespace_errors || false }-*/;

  public final native boolean
      syntaxHighlighting() /*-{ return this.syntax_highlighting || false }-*/;

  public final native boolean hideTopMenu() /*-{ return this.hide_top_menu || false }-*/;

  public final native boolean
      autoHideDiffTableHeader() /*-{ return this.auto_hide_diff_table_header || false }-*/;

  public final native boolean hideLineNumbers() /*-{ return this.hide_line_numbers || false }-*/;

  public final native boolean
      expandAllComments() /*-{ return this.expand_all_comments || false }-*/;

  public final native boolean manualReview() /*-{ return this.manual_review || false }-*/;

  public final native boolean renderEntireFile() /*-{ return this.render_entire_file || false }-*/;

  public final native boolean hideEmptyPane() /*-{ return this.hide_empty_pane || false }-*/;

  public final native boolean retainHeader() /*-{ return this.retain_header || false }-*/;

  public final native boolean skipUnchanged() /*-{ return this.skip_unchanged || false }-*/;

  public final native boolean skipUncommented() /*-{ return this.skip_uncommented || false }-*/;

  public final native boolean skipDeleted() /*-{ return this.skip_deleted || false }-*/;

  public final native boolean matchBrackets() /*-{ return this.match_brackets || false }-*/;

  public final native boolean lineWrapping() /*-{ return this.line_wrapping || false }-*/;

  private native void setThemeRaw(String i) /*-{ this.theme = i }-*/;

  private native void setIgnoreWhitespaceRaw(String i) /*-{ this.ignore_whitespace = i }-*/;

  private native String ignoreWhitespaceRaw() /*-{ return this.ignore_whitespace }-*/;

  private native String themeRaw() /*-{ return this.theme }-*/;

  private native int get(String n, int d) /*-{ return this.hasOwnProperty(n) ? this[n] : d }-*/;

  protected DiffPreferences() {}
}
