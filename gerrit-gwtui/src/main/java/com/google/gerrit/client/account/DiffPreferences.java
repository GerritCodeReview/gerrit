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
    p.context(in.getContext());
    p.intralineDifference(in.isIntralineDifference());
    p.showLineEndings(in.isShowLineEndings());
    p.showTabs(in.isShowTabs());
    p.showWhitespaceErrors(in.isShowWhitespaceErrors());
    p.syntaxHighlighting(in.isSyntaxHighlighting());
    p.hideTopMenu(in.isHideTopMenu());
    p.expandAllComments(in.isExpandAllComments());
    return p;
  }

  public final void copyTo(AccountDiffPreference p) {
    p.setIgnoreWhitespace(ignoreWhitespace());
    p.setTabSize(tabSize());
    p.setContext((short)context());
    p.setIntralineDifference(intralineDifference());
    p.setShowLineEndings(showLineEndings());
    p.setShowTabs(showTabs());
    p.setShowWhitespaceErrors(showWhitespaceErrors());
    p.setSyntaxHighlighting(syntaxHighlighting());
    p.setHideTopMenu(hideTopMenu());
    p.setExpandAllComments(expandAllComments());
  }

  public final void ignoreWhitespace(Whitespace i) {
    setIgnoreWhitespaceRaw(i.toString());
  }
  private final native void setIgnoreWhitespaceRaw(String i) /*-{ this.ignore_whitespace = i }-*/;

  public final native void tabSize(int t) /*-{ this.tab_size = t }-*/;
  public final native void context(int c) /*-{ this.context = c }-*/;
  public final native void intralineDifference(boolean i) /*-{ this.intraline_difference = i }-*/;
  public final native void showLineEndings(boolean s) /*-{ this.show_line_endings = s }-*/;
  public final native void showTabs(boolean s) /*-{ this.show_tabs = s }-*/;
  public final native void showWhitespaceErrors(boolean s) /*-{ this.show_whitespace_errors = s }-*/;
  public final native void syntaxHighlighting(boolean s) /*-{ this.syntax_highlighting = s }-*/;
  public final native void hideTopMenu(boolean s) /*-{ this.hide_top_menu = s }-*/;
  public final native void expandAllComments(boolean e) /*-{ this.expand_all_comments = e }-*/;

  public final Whitespace ignoreWhitespace() {
    String s = ignoreWhitespaceRaw();
    return s != null ? Whitespace.valueOf(s) : Whitespace.IGNORE_NONE;
  }
  private final native String ignoreWhitespaceRaw() /*-{ return this.ignore_whitespace }-*/;

  public final native int tabSize() /*-{ return this.tab_size }-*/;
  public final native int context() /*-{ return this.context }-*/;
  public final native boolean intralineDifference() /*-{ return this.intraline_difference }-*/;
  public final native boolean showLineEndings() /*-{ return this.show_line_endings }-*/;
  public final native boolean showTabs() /*-{ return this.show_tabs }-*/;
  public final native boolean showWhitespaceErrors() /*-{ return this.show_whitespace_errors }-*/;
  public final native boolean syntaxHighlighting() /*-{ return this.syntax_highlighting }-*/;
  public final native boolean hideTopMenu() /*-{ return this.hide_top_menu }-*/;
  public final native boolean expandAllComments() /*-{ return this.expand_all_comments }-*/;

  protected DiffPreferences() {
  }
}
