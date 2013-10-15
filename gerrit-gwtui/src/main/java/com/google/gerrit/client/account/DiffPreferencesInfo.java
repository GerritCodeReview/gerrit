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

public class DiffPreferencesInfo extends JavaScriptObject {
  public static DiffPreferencesInfo create(DiffPreferencesInfo original) {
    DiffPreferencesInfo info = createObject().cast();
    info.setContext(original.context());
    info.setExpandAllComments(original.expand_all_comments());
    info.setIgnoreWhitespaceRaw(original.ignorewhitespaceRaw());
    info.setIntralineDifference(original.intraline_difference());
    info.setLineLength(original.line_length());
    info.setManualReview(original.manual_review());
    info.setRetainHeader(original.retain_header());
    info.setShowLineEndings(original.show_line_endings());
    info.setShowTabs(original.show_tabs());
    info.setShowWhitespaceErrors(original.show_whitespace_errors());
    info.setSkipDeleted(original.skip_deleted());
    info.setSkipUncommented(original.skip_uncommented());
    info.setSyntaxHighlighting(original.syntax_highlighting());
    info.setTabSize(original.tab_size());
    return info;
  }

  public static DiffPreferencesInfo create(AccountDiffPreference original) {
    DiffPreferencesInfo info = createObject().cast();
    info.setContext(original.getContext());
    info.setExpandAllComments(original.isExpandAllComments());
    info.setIgnoreWhitespace(original.getIgnoreWhitespace());
    info.setIntralineDifference(original.isIntralineDifference());
    info.setLineLength(original.getLineLength());
    info.setManualReview(original.isManualReview());
    info.setRetainHeader(original.isRetainHeader());
    info.setShowLineEndings(original.isShowLineEndings());
    info.setShowTabs(original.isShowTabs());
    info.setShowWhitespaceErrors(original.isShowWhitespaceErrors());
    info.setSkipDeleted(original.isSkipDeleted());
    info.setSkipUncommented(original.isSkipUncommented());
    info.setSyntaxHighlighting(original.isSyntaxHighlighting());
    info.setTabSize(original.getTabSize());
    return info;
  }

  public static DiffPreferencesInfo createDefault() {
    DiffPreferencesInfo info = createObject().cast();
    info.setIgnoreWhitespace(Whitespace.IGNORE_NONE);
    info.setTabSize(8);
    info.setLineLength(100);
    info.setSyntaxHighlighting(true);
    info.setShowWhitespaceErrors(true);
    info.setShowLineEndings(true);
    info.setIntralineDifference(true);
    info.setShowTabs(true);
    info.setContext(AccountDiffPreference.DEFAULT_CONTEXT);
    info.setManualReview(false);
    return info;
  }

  public final native short context() /*-{ return this.context; }-*/;
  public final native boolean expand_all_comments() /*-{ return this['expand_all_comments'] ? true : false; }-*/;

  public final Whitespace ignore_whitespace() {
    return Whitespace.valueOf(ignorewhitespaceRaw());
  }
  private final native String ignorewhitespaceRaw() /*-{ return this.ignore_whitespace; }-*/;

  public final native boolean intraline_difference() /*-{ return this['intraline_difference'] ? true : false; }-*/;
  public final native int line_length() /*-{ return this.line_length; }-*/;
  public final native boolean manual_review() /*-{ return this['manual_review'] ? true : false; }-*/;
  public final native boolean retain_header() /*-{ return this['retain_header'] ? true : false; }-*/;
  public final native boolean show_line_endings() /*-{ return this['show_line_endings'] ? true : false; }-*/;
  public final native boolean show_tabs() /*-{ return this['show_tabs'] ? true : false; }-*/;
  public final native boolean show_whitespace_errors() /*-{ return this['show_whitespace_errors'] ? true : false; }-*/;
  public final native boolean skip_deleted() /*-{ return this['skip_deleted'] ? true : false; }-*/;
  public final native boolean skip_uncommented() /*-{ return this['skip_uncommented'] ? true : false; }-*/;
  public final native boolean syntax_highlighting() /*-{ return this['syntax_highlighting'] ? true : false; }-*/;
  public final native int tab_size() /*-{ return this.tab_size; }-*/;

  public final native void setContext(short c) /*-{ this.context = c; }-*/;
  public final native void setExpandAllComments(boolean e) /*-{ this.expand_all_comments = e; }-*/;

  public final void setIgnoreWhitespace(Whitespace i) {
    setIgnoreWhitespaceRaw(i.toString());
  }
  private final native void setIgnoreWhitespaceRaw(String i) /*-{ this.ignore_white_space = i; }-*/;

  public final native void setIntralineDifference(boolean i) /*-{ this.intraline_difference = i; }-*/;
  public final native void setLineLength(int l) /*-{ this.line_length = l; }-*/;
  public final native void setManualReview(boolean m) /*-{ this.manual_review = m; }-*/;
  public final native void setRetainHeader(boolean r) /*-{ this.retain_header = r; }-*/;
  public final native void setShowLineEndings(boolean s) /*-{ this.show_line_endings = s; }-*/;
  public final native void setShowTabs(boolean s) /*-{ this.show_tabs = s; }-*/;
  public final native void setShowWhitespaceErrors(boolean s) /*-{ this.show_whitespace_errors = s; }-*/;
  public final native void setSkipDeleted(boolean s) /*-{ this.skip_deleted = s; }-*/;
  public final native void setSkipUncommented(boolean s) /*-{ this.skip_uncommented = s; }-*/;
  public final native void setSyntaxHighlighting(boolean s) /*-{ this.syntax_highlighting = s; }-*/;
  public final native int setTabSize(int t) /*-{ this.tab_size = t; }-*/;

  protected DiffPreferencesInfo() {
  }
}
