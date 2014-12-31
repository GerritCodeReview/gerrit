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

import net.codemirror.mode.ModeInfo;
import net.codemirror.mode.Modes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ModeInjector {
  /** Map of names such as "clike" to URI for code download. */
  private static final Map<String, SafeUri> modeUris = new HashMap<>();

  static {
    indexModes(new DataResource[] {
      Modes.I.clike(),
      Modes.I.clojure(),
      Modes.I.coffeescript(),
      Modes.I.commonlisp(),
      Modes.I.css(),
      Modes.I.d(),
      Modes.I.dart(),
      Modes.I.diff(),
      Modes.I.dockerfile(),
      Modes.I.dtd(),
      Modes.I.erlang(),
      Modes.I.gas(),
      Modes.I.gerrit_commit(),
      Modes.I.gfm(),
      Modes.I.groovy(),
      Modes.I.haskell(),
      Modes.I.htmlmixed(),
      Modes.I.javascript(),
      Modes.I.lua(),
      Modes.I.markdown(),
      Modes.I.perl(),
      Modes.I.php(),
      Modes.I.pig(),
      Modes.I.properties(),
      Modes.I.python(),
      Modes.I.r(),
      Modes.I.rst(),
      Modes.I.ruby(),
      Modes.I.scheme(),
      Modes.I.shell(),
      Modes.I.smalltalk(),
      Modes.I.soy(),
      Modes.I.sql(),
      Modes.I.stex(),
      Modes.I.velocity(),
      Modes.I.verilog(),
      Modes.I.xml(),
      Modes.I.yaml(),
    });
    ModeInfo.buildMimeMap();

    alias("application/x-httpd-php-open", "application/x-httpd-php");
    alias("application/x-javascript", "application/javascript");
    alias("application/x-shellscript", "text/x-sh");
    alias("application/x-tcl", "text/x-tcl");
    alias("text/typescript", "application/typescript");
    alias("text/x-c", "text/x-csrc");
    alias("text/x-c++hdr", "text/x-c++src");
    alias("text/x-chdr", "text/x-csrc");
    alias("text/x-h", "text/x-csrc");
    alias("text/x-ini", "text/x-properties");
    alias("text/x-java-source", "text/x-java");
    alias("text/x-php", "application/x-httpd-php");
    alias("text/x-scripttcl", "text/x-tcl");
  }

  private static void indexModes(DataResource[] all) {
    for (DataResource r : all) {
      modeUris.put(r.getName(), r.getSafeUri());
    }
  }

  private static void alias(String serverMime, String toMime) {
    ModeInfo mode = ModeInfo.findModeByMIME(toMime);
    if (mode != null) {
      mode.addMime(serverMime);
    }
  }

  public static boolean canLoad(String mode) {
    return modeUris.containsKey(mode);
  }

  public static String getContentType(String mode) {
    if (canLoad(mode)) {
      return mode;
    }

    ModeInfo m = ModeInfo.findModeByMIME(mode);
    return m != null ? m.mime() : mode;
  }

  private static native boolean isModeLoaded(String n)
  /*-{ return $wnd.CodeMirror.modes.hasOwnProperty(n); }-*/;

  private static native boolean isMimeLoaded(String n)
  /*-{ return $wnd.CodeMirror.mimeModes.hasOwnProperty(n); }-*/;

  private static native JsArrayString getDependencies(String n)
  /*-{ return $wnd.CodeMirror.modes[n].dependencies || []; }-*/;

  private final Set<String> loading = new HashSet<>(4);
  private int pending;
  private AsyncCallback<Void> appCallback;

  public ModeInjector add(String name) {
    if (name == null || isModeLoaded(name) || isMimeLoaded(name)) {
      return this;
    }

    ModeInfo m = ModeInfo.findModeByMIME(name);
    if (m != null) {
      name = m.mode();
    }

    if (!canLoad(name)) {
      Logger.getLogger("net.codemirror").log(
        Level.WARNING,
        "CodeMirror mode " + name + " not configured.");
      return this;
    }

    loading.add(name);
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

      if (!canLoad(d)) {
        Logger.getLogger("net.codemirror").log(
          Level.SEVERE,
          "CodeMirror mode " + d + " needs " + d);
        continue;
      }

      loading.add(d);
      beginLoading(d);
    }
  }
}
