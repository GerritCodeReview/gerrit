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

package net.codemirror.lib;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Glue around the Vim emulation for {@link CodeMirror}.
 *
 * <p>As an instance {@code this} is actually the {@link CodeMirror} object. Class Vim is providing
 * a new namespace for Vim related methods that are associated with an editor.
 */
public class Vim extends JavaScriptObject {
  static void initKeyMap() {
    KeyMap km = KeyMap.create();
    for (String key : new String[] {"A", "C", "I", "O", "R", "U"}) {
      km.propagate(key);
      km.propagate("'" + key.toLowerCase() + "'");
    }
    for (String key :
        new String[] {
          "Ctrl-C", "Ctrl-O", "Ctrl-P", "Ctrl-S", "Ctrl-F", "Ctrl-B", "Ctrl-R",
        }) {
      km.propagate(key);
    }
    for (int i = 0; i <= 9; i++) {
      km.propagate("Ctrl-" + i);
    }
    km.fallthrough(CodeMirror.getKeyMap("vim"));
    CodeMirror.addKeyMap("vim_ro", km);

    mapKey("j", "gj");
    mapKey("k", "gk");
    mapKey("Down", "gj");
    mapKey("Up", "gk");
    mapKey("<PageUp>", "<C-u>");
    mapKey("<PageDown>", "<C-d>");
  }

  public static final native void mapKey(String alias, String actual) /*-{
    $wnd.CodeMirror.Vim.map(alias, actual)
  }-*/;

  public final native void handleKey(String key) /*-{
    $wnd.CodeMirror.Vim.handleKey(this, key)
  }-*/;

  public final native void handleEx(String exCommand) /*-{
    $wnd.CodeMirror.Vim.handleEx(this, exCommand);
  }-*/;

  public final native boolean hasSearchHighlight() /*-{
    var v = this.state.vim;
    return v && v.searchState_ && !!v.searchState_.getOverlay();
  }-*/;

  protected Vim() {}
}
