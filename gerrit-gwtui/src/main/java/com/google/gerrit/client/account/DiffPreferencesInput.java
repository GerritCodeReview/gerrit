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

public class DiffPreferencesInput extends JavaScriptObject {
  public static DiffPreferencesInput create(AccountDiffPreference original) {
    DiffPreferencesInput input = createObject().cast();
    input.setContext(original.getContext());
    input.setExpandAllComments(original.isExpandAllComments());
    input.setIgnoreWhitespace(original.getIgnoreWhitespace());
    input.setIntralineDifference(original.isIntralineDifference());
    input.setLineLength(original.getLineLength());
    input.setManualReview(original.isManualReview());
    input.setRetainHeader(original.isRetainHeader());
    input.setShowLineEndings(original.isShowLineEndings());
    input.setShowTabs(original.isShowTabs());
    input.setShowWhitespaceErrors(original.isShowWhitespaceErrors());
    input.setSkipDeleted(original.isSkipDeleted());
    input.setSkipUncommented(original.isSkipUncommented());
    input.setSyntaxHighlighting(original.isSyntaxHighlighting());
    input.setTabSize(original.getTabSize());
    return input;
  }

  private final native void setContext(short c) /*-{ this.context = c; }-*/;
  private final native void setExpandAllComments(boolean e) /*-{ this.expand_all_comments = e; }-*/;

  private final void setIgnoreWhitespace(Whitespace i) {
    setIgnoreWhitespaceRaw(i.toString());
  }
  private final native void setIgnoreWhitespaceRaw(String i) /*-{ this.ignore_white_space = i; }-*/;

  private final native void setIntralineDifference(boolean i) /*-{ this.intraline_difference = i; }-*/;
  private final native void setLineLength(int l) /*-{ this.line_length = l; }-*/;
  private final native void setManualReview(boolean m) /*-{ this.manual_review = m; }-*/;
  private final native void setRetainHeader(boolean r) /*-{ this.retain_header = r; }-*/;
  private final native void setShowLineEndings(boolean s) /*-{ this.show_line_endings = s; }-*/;
  private final native void setShowTabs(boolean s) /*-{ this.show_tabs = s; }-*/;
  private final native void setShowWhitespaceErrors(boolean s) /*-{ this.show_whitespace_errors = s; }-*/;
  private final native void setSkipDeleted(boolean s) /*-{ this.skip_deleted = s; }-*/;
  private final native void setSkipUncommented(boolean s) /*-{ this.skip_uncommented = s; }-*/;
  private final native void setSyntaxHighlighting(boolean s) /*-{ this.syntax_highlighting = s; }-*/;
  private final native int setTabSize(int t) /*-{ this.tab_size = t; }-*/;

  protected DiffPreferencesInput() {
  }
}
