// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.plugin.client;

import com.google.gerrit.plugin.client.screen.Screen;
import com.google.gwt.core.client.EntryPoint;

/**
 * Base class for writing Gerrit Web UI plugins
 *
 * Writing a plugin:
 * <ol>
 * <li>Declare subtype of Plugin</li>
 * <li>Bind WebUiPlugin to GwtPlugin implementation in Gerrit-Module</li>
 * </ol>
 */
public abstract class Plugin implements EntryPoint {
  /**
   * The plugin entry point method, called automatically by loading
   * a module that declares an implementing class as an entry point.
   */
  public abstract void onPluginLoad();

  public final void onModuleLoad() {
    PluginInstance self = PluginInstance.get();
    try {
      onPluginLoad();
      self._initialized();
    } finally {
      self._loaded();
    }
  }

  public static void screen(String token, Screen.Callback cb) {
    PluginInstance.get().screen(token, cb);
  }

  public static void screenRegex(String pattern, Screen.Callback cb) {
    PluginInstance.get().screenRegex(pattern, cb);
  }

  public native static void go(String t)
  /*-{ $wnd.Gerrit.go(t) }-*/;

  public native static void refresh()
  /*-{ $wnd.Gerrit.refresh() }-*/;
}
