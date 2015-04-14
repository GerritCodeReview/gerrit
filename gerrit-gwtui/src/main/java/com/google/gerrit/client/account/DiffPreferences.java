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

import com.google.gerrit.extensions.client.Theme;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gwt.core.client.JavaScriptObject;

public class DiffPreferences extends JavaScriptObject {
  public static DiffPreferences create(AccountDiffPreference in) {
    DiffPreferences p = createObject().cast();
    if (in == null) {
      in = AccountDiffPreference.createDefault(null);
    }
    p.ignoreWhitespace(in.getIgnoreWhitespace());
    p.tabSize(in.getTabSize());
    p.lineLength(in.getLineLength());
    p.context(in.getContext());
    p.intralineDifference(in.isIntralineDifference());
    p.showLineEndings(in.isShowLineEndings());
    p.showTabs(in.isShowTabs());
    p.showWhitespaceErrors(in.isShowWhitespaceErrors());
    p.syntaxHighlighting(in.isSyntaxHighlighting());
    p.hideTopMenu(in.isHideTopMenu());
    p.autoHideDiffTableHeader(in.isAutoHideDiffTableHeader());
    p.hideLineNumbers(in.isHideLineNumbers());
    p.expandAllComments(in.isExpandAllComments());
    p.manualReview(in.isManualReview());
    p.renderEntireFile(in.isRenderEntireFile());
    p.theme(in.getTheme());
    p.hideEmptyPane(in.isHideEmptyPane());
    return p;
  }

  public final void copyTo(AccountDiffPreference p) {
    p.setIgnoreWhitespace(ignoreWhitespace());
    p.setTabSize(tabSize());
    p.setLineLength(lineLength());
    p.setContext((short)context());
    p.setIntralineDifference(intralineDifference());
    p.setShowLineEndings(showLineEndings());
    p.setShowTabs(showTabs());
    p.setShowWhitespaceErrors(showWhitespaceErrors());
    p.setSyntaxHighlighting(syntaxHighlighting());
    p.setHideTopMenu(hideTopMenu());
    p.setAutoHideDiffTableHeader(autoHideDiffTableHeader());
    p.setHideLineNumbers(hideLineNumbers());
    p.setExpandAllComments(expandAllComments());
    p.setManualReview(manualReview());
    p.setRenderEntireFile(renderEntireFile());
    p.setTheme(theme());
    p.setHideEmptyPane(hideEmptyPane());
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

  public final boolean showLineNumbers() {
    return !hideLineNumbers();
  }

  public final boolean autoReview() {
    return !manualReview();
  }

  public final native void tabSize(int t) /*-{ this.tab_size = t }-*/;
  public final native void lineLength(int c) /*-{ this.line_length = c }-*/;
  public final native void context(int c) /*-{ this.context = c }-*/;
  public final native void intralineDifference(boolean i) /*-{ this.intraline_difference = i }-*/;
  public final native void showLineEndings(boolean s) /*-{ this.show_line_endings = s }-*/;
  public final native void showTabs(boolean s) /*-{ this.show_tabs = s }-*/;
  public final native void showWhitespaceErrors(boolean s) /*-{ this.show_whitespace_errors = s }-*/;
  public final native void syntaxHighlighting(boolean s) /*-{ this.syntax_highlighting = s }-*/;
  public final native void hideTopMenu(boolean s) /*-{ this.hide_top_menu = s }-*/;
  public final native void autoHideDiffTableHeader(boolean s) /*-{ this.auto_hide_diff_table_header = s }-*/;
  public final native void hideLineNumbers(boolean s) /*-{ this.hide_line_numbers = s }-*/;
  public final native void expandAllComments(boolean e) /*-{ this.expand_all_comments = e }-*/;
  public final native void manualReview(boolean r) /*-{ this.manual_review = r }-*/;
  public final native void renderEntireFile(boolean r) /*-{ this.render_entire_file = r }-*/;
  public final native void hideEmptyPane(boolean s) /*-{ this.hide_empty_pane = s }-*/;
  public final native boolean intralineDifference() /*-{ return this.intraline_difference || false }-*/;
  public final native boolean showLineEndings() /*-{ return this.show_line_endings || false }-*/;
  public final native boolean showTabs() /*-{ return this.show_tabs || false }-*/;
  public final native boolean showWhitespaceErrors() /*-{ return this.show_whitespace_errors || false }-*/;
  public final native boolean syntaxHighlighting() /*-{ return this.syntax_highlighting || false }-*/;
  public final native boolean hideTopMenu() /*-{ return this.hide_top_menu || false }-*/;
  public final native boolean autoHideDiffTableHeader() /*-{ return this.auto_hide_diff_table_header || false }-*/;
  public final native boolean hideLineNumbers() /*-{ return this.hide_line_numbers || false }-*/;
  public final native boolean expandAllComments() /*-{ return this.expand_all_comments || false }-*/;
  public final native boolean manualReview() /*-{ return this.manual_review || false }-*/;
  public final native boolean renderEntireFile() /*-{ return this.render_entire_file || false }-*/;
  public final native boolean hideEmptyPane() /*-{ return this.hide_empty_pane || false }-*/;

  private final native void setThemeRaw(String i) /*-{ this.theme = i }-*/;
  private final native void setIgnoreWhitespaceRaw(String i) /*-{ this.ignore_whitespace = i }-*/;
  private final native String ignoreWhitespaceRaw() /*-{ return this.ignore_whitespace }-*/;
  private final native String themeRaw() /*-{ return this.theme }-*/;
  private final native int get(String n, int d) /*-{ return this.hasOwnProperty(n) ? this[n] : d }-*/;

  protected DiffPreferences() {
  }
}
