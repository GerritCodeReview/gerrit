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

package net.codemirror.mode;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.codemirror.lib.Loader;

public class ModeInjector {
  private static boolean canLoad(String mode) {
    return ModeInfo.getModeScriptUri(mode) != null;
  }

  private static native boolean isModeLoaded(String n)
      /*-{ return $wnd.CodeMirror.modes.hasOwnProperty(n); }-*/ ;

  private static native boolean isMimeLoaded(String n)
      /*-{ return $wnd.CodeMirror.mimeModes.hasOwnProperty(n); }-*/ ;

  private static native JsArrayString getDependencies(String n)
      /*-{ return $wnd.CodeMirror.modes[n].dependencies || []; }-*/ ;

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
      Logger.getLogger("net.codemirror")
          .log(Level.WARNING, "CodeMirror mode " + name + " not configured.");
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
        ModeInfo.getModeScriptUri(mode),
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
        Logger.getLogger("net.codemirror")
            .log(Level.SEVERE, "CodeMirror mode " + d + " needs " + d);
        continue;
      }

      loading.add(d);
      beginLoading(d);
    }
  }
}
