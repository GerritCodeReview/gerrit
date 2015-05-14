// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.KeyMapType;
import com.google.gerrit.extensions.client.Theme;
import com.google.gwt.core.client.JavaScriptObject;

public class EditPreferences extends JavaScriptObject {
  public static EditPreferences create(EditPreferencesInfo in) {
    EditPreferences p = createObject().cast();
    p.tabSize(in.tabSize);
    p.lineLength(in.lineLength);
    p.cursorBlinkRate(in.cursorBlinkRate);
    p.hideTopMenu(in.hideTopMenu);
    p.showTabs(in.showTabs);
    p.showWhitespaceErrors(in.showWhitespaceErrors);
    p.syntaxHighlighting(in.syntaxHighlighting);
    p.hideLineNumbers(in.hideLineNumbers);
    p.matchBrackets(in.matchBrackets);
    p.theme(in.theme);
    p.keyMapType(in.keyMapType);
    return p;
  }

  public final void copyTo(EditPreferencesInfo p) {
    p.tabSize = tabSize();
    p.lineLength = lineLength();
    p.cursorBlinkRate = cursorBlinkRate();
    p.hideTopMenu = hideTopMenu();
    p.showTabs = showTabs();
    p.showWhitespaceErrors = showWhitespaceErrors();
    p.syntaxHighlighting = syntaxHighlighting();
    p.hideLineNumbers = hideLineNumbers();
    p.matchBrackets = matchBrackets();
    p.theme = theme();
    p.keyMapType = keyMapType();
  }

  public final void theme(Theme i) {
    setThemeRaw(i != null ? i.toString() : Theme.DEFAULT.toString());
  }
  private final native void setThemeRaw(String i) /*-{ this.theme = i }-*/;

  public final void keyMapType(KeyMapType i) {
    setkeyMapTypeRaw(i != null ? i.toString() : KeyMapType.DEFAULT.toString());
  }
  private final native void setkeyMapTypeRaw(String i) /*-{ this.key_map_type = i }-*/;

  public final native void tabSize(int t) /*-{ this.tab_size = t }-*/;
  public final native void lineLength(int c) /*-{ this.line_length = c }-*/;
  public final native void cursorBlinkRate(int r) /*-{ this.cursor_blink_rate = r }-*/;
  public final native void hideTopMenu(boolean s) /*-{ this.hide_top_menu = s }-*/;
  public final native void showTabs(boolean s) /*-{ this.show_tabs = s }-*/;
  public final native void showWhitespaceErrors(boolean s) /*-{ this.show_whitespace_errors = s }-*/;
  public final native void syntaxHighlighting(boolean s) /*-{ this.syntax_highlighting = s }-*/;
  public final native void hideLineNumbers(boolean s) /*-{ this.hide_line_numbers = s }-*/;
  public final native void matchBrackets(boolean s) /*-{ this.match_brackets = s }-*/;

  public final Theme theme() {
    String s = themeRaw();
    return s != null ? Theme.valueOf(s) : Theme.DEFAULT;
  }
  private final native String themeRaw() /*-{ return this.theme }-*/;

  public final KeyMapType keyMapType() {
    String s = keyMapTypeRaw();
    return s != null ? KeyMapType.valueOf(s) : KeyMapType.DEFAULT;
  }
  private final native String keyMapTypeRaw() /*-{ return this.key_map_type }-*/;

  public final int tabSize() {return get("tab_size", 8); }
  public final int lineLength() {return get("line_length", 100); }
  public final int cursorBlinkRate() {return get("cursor_blink_rate", 0); }
  public final native boolean hideTopMenu() /*-{ return this.hide_top_menu || false }-*/;
  public final native boolean showTabs() /*-{ return this.show_tabs || false }-*/;
  public final native boolean showWhitespaceErrors() /*-{ return this.show_whitespace_errors || false }-*/;
  public final native boolean syntaxHighlighting() /*-{ return this.syntax_highlighting || false }-*/;
  public final native boolean hideLineNumbers() /*-{ return this.hide_line_numbers || false }-*/;
  public final native boolean matchBrackets() /*-{ return this.match_brackets || false }-*/;
  private final native int get(String n, int d) /*-{ return this.hasOwnProperty(n) ? this[n] : d }-*/;

  protected EditPreferences() {
  }
}
