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

package net.codemirror.lib;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.resources.client.DataResource;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.user.client.rpc.AsyncCallback;

import net.codemirror.mode.Modes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ModeInjector {
  /** Map of server content type to CodeMiror mode or content type. */
  private static final Map<String, String> mimeAlias;

  /** Map of content type "text/x-java" to mode name "clike". */
  private static final Map<String, String> mimeModes;

  /** Map of names such as "clike" to URI for code download. */
  private static final Map<String, SafeUri> modeUris;

  static {
    DataResource[] all = {
      Modes.I.clike(),
      Modes.I.clojure(),
      Modes.I.commonlisp(),
      Modes.I.css(),
      Modes.I.go(),
      Modes.I.groovy(),
      Modes.I.htmlmixed(),
      Modes.I.javascript(),
      Modes.I.perl(),
      Modes.I.properties(),
      Modes.I.python(),
      Modes.I.ruby(),
      Modes.I.shell(),
      Modes.I.sql(),
      Modes.I.velocity(),
      Modes.I.xml(),
    };

    mimeAlias = new HashMap<String, String>();
    mimeModes = new HashMap<String, String>();
    modeUris = new HashMap<String, SafeUri>();

    for (DataResource m : all) {
      modeUris.put(m.getName(), m.getSafeUri());
    }
    parseModeMap();
  }

  private static void parseModeMap() {
    String mode = null;
    for (String line : Modes.I.mode_map().getText().split("\n")) {
      int eq = line.indexOf('=');
      if (0 < eq) {
        mimeAlias.put(
          line.substring(0, eq).trim(),
          line.substring(eq + 1).trim());
      } else if (line.endsWith(":")) {
        String n = line.substring(0, line.length() - 1);
        if (modeUris.containsKey(n)) {
          mode = n;
        }
      } else if (mode != null && line.contains("/")) {
        mimeModes.put(line, mode);
      } else {
        mode = null;
      }
    }
  }

  public static String getContentType(String mode) {
    String real = mode != null ? mimeAlias.get(mode) : null;
    return real != null ? real : mode;
  }

  private static native boolean isModeLoaded(String n)
  /*-{ return $wnd.CodeMirror.modes.hasOwnProperty(n); }-*/;

  private static native boolean isMimeLoaded(String n)
  /*-{ return $wnd.CodeMirror.mimeModes.hasOwnProperty(n); }-*/;

  private static native JsArrayString getDependencies(String n)
  /*-{ return $wnd.CodeMirror.modes[n].dependencies || []; }-*/;

  private final Set<String> loading = new HashSet<String>(4);
  private int pending;
  private AsyncCallback<Void> appCallback;

  public ModeInjector add(String name) {
    if (name == null || isModeLoaded(name) || isMimeLoaded(name)) {
      return this;
    }

    String mode = mimeModes.get(name);
    if (mode == null) {
      mode = name;
    }

    SafeUri uri = modeUris.get(mode);
    if (uri == null) {
      Logger.getLogger("net.codemirror").log(
        Level.WARNING,
        "CodeMirror mode " + mode + " not configured.");
      return this;
    }

    loading.add(mode);
    return this;
  }

  public void inject(AsyncCallback<Void> appCallback) {
    this.appCallback = appCallback;
    for (String mode : loading) {
      beginLoading(mode);
    }
    if (pending == 0) {
      appCallback.onSuccess(null);
    }
  }

  private void beginLoading(final String mode) {
    pending++;
    Loader.injectScript(
      modeUris.get(mode),
      new AsyncCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
          pending--;
          ensureDependenciesAreLoaded(mode);
          if (pending == 0) {
            appCallback.onSuccess(null);
          }
        }

        @Override
        public void onFailure(Throwable caught) {
          if (--pending == 0) {
            appCallback.onFailure(caught);
          }
        }
      });
  }

  private void ensureDependenciesAreLoaded(String mode) {
    JsArrayString deps = getDependencies(mode);
    for (int i = 0; i < deps.length(); i++) {
      String d = deps.get(i);
      if (loading.contains(d) || isModeLoaded(d)) {
        continue;
      }

      SafeUri uri = modeUris.get(d);
      if (uri == null) {
        Logger.getLogger("net.codemirror").log(
          Level.SEVERE,
          "CodeMirror mode " + mode + " needs " + d);
        continue;
      }

      loading.add(d);
      beginLoading(d);
    }
  }
}
