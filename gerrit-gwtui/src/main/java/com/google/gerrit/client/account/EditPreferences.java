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

import com.google.gerrit.extensions.common.EditPreferencesInfo;
import com.google.gerrit.extensions.common.KeyMap;
import com.google.gerrit.extensions.common.Theme;
import com.google.gwt.core.client.JavaScriptObject;

public class EditPreferences extends JavaScriptObject {
  public static EditPreferences create(EditPreferencesInfo in) {
    EditPreferences p = createObject().cast();
    p.tabSize(in.tabSize);
    p.lineLength(in.lineLength);
    p.lineWrapping(in.lineWrapping);
    p.showTabs(in.showTabs);
    p.showTrailingSpace(in.showTrailingSpace);
    p.syntaxHighlighting(in.syntaxHighlighting);
    p.hideLineNumbers(in.hideLineNumbers);
    p.keyMap(in.keyMap);
    p.theme(in.theme);
    return p;
  }

  public final void copyTo(EditPreferencesInfo p) {
    p.tabSize = tabSize();
    p.lineLength = lineLength();
    p.lineWrapping = lineWrapping();
    p.showTabs = showTabs();
    p.showTrailingSpace = showTrailingSpace();
    p.syntaxHighlighting = syntaxHighlighting();
    p.hideLineNumbers = hideLineNumbers();
    p.keyMap = keyMap();
    p.theme = theme();
  }

  public final void keyMap(KeyMap i) {
    setKeyMapRaw(i != null ? i.toString() : KeyMap.DEFAULT.toString());
  }
  private final native void setKeyMapRaw(String i) /*-{ this.key_map = i }-*/;

  public final void theme(Theme i) {
    setThemeRaw(i != null ? i.toString() : Theme.DEFAULT.toString());
  }
  private final native void setThemeRaw(String i) /*-{ this.theme = i }-*/;

  public final native void tabSize(int t) /*-{ this.tab_size = t }-*/;
  public final native void lineLength(int c) /*-{ this.line_length = c }-*/;
  public final native void lineWrapping(boolean w) /*-{ this.line_wrapping = w }-*/;
  public final native void showTabs(boolean s) /*-{ this.show_tabs = s }-*/;
  public final native void showTrailingSpace(boolean s) /*-{ this.show_trailing_space = s }-*/;
  public final native void syntaxHighlighting(boolean s) /*-{ this.syntax_highlighting = s }-*/;
  public final native void hideLineNumbers(boolean s) /*-{ this.hide_line_numbers = s }-*/;
  public final void showLineNumbers(boolean s) { hideLineNumbers(!s); }

  public final KeyMap keyMap() {
    String s = keyMapRaw();
    return s != null ? KeyMap.valueOf(s) : KeyMap.DEFAULT;
  }
  private final native String keyMapRaw() /*-{ return this.key_map }-*/;

  public final Theme theme() {
    String s = themeRaw();
    return s != null ? Theme.valueOf(s) : Theme.DEFAULT;
  }
  private final native String themeRaw() /*-{ return this.theme }-*/;

  public final int tabSize() {return get("tab_size", 8); }
  public final int lineLength() {return get("line_length", 100); }
  public final native boolean lineWrapping() /*-{ return this.line_wrapping || false }-*/;
  public final native boolean showTabs() /*-{ return this.show_tabs || false }-*/;
  public final native boolean showTrailingSpace() /*-{ return this.show_trailing_space || false }-*/;
  public final native boolean syntaxHighlighting() /*-{ return this.syntax_highlighting || false }-*/;
  public final native boolean hideLineNumbers() /*-{ return this.hide_line_numbers || false }-*/;
  public final boolean showLineNumbers() { return !hideLineNumbers(); }
  private final native int get(String n, int d)
  /*-{ return this.hasOwnProperty(n) ? this[n] : d }-*/;

  protected EditPreferences() {
  }
}
