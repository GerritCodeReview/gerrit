// Copyright (C) 2016 The Android Open Source Project
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

package net.codemirror.addon;

import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.codemirror.lib.Loader;

public class AddonInjector {
  private static final Map<String, SafeUri> addonUris = new HashMap<>();

  static {
    addonUris.put(Addons.I.merge_bundled().getName(), Addons.I.merge_bundled().getSafeUri());
  }

  public static SafeUri getAddonScriptUri(String addon) {
    return addonUris.get(addon);
  }

  private static boolean canLoad(String addon) {
    return getAddonScriptUri(addon) != null;
  }

  private final Set<String> loading = new HashSet<>();
  private int pending;
  private AsyncCallback<Void> appCallback;

  public AddonInjector add(String name) {
    if (name == null) {
      return this;
    }

    if (!canLoad(name)) {
      Logger.getLogger("net.codemirror")
          .log(Level.WARNING, "CodeMirror addon " + name + " not configured.");
      return this;
    }

    loading.add(name);
    return this;
  }

  public void inject(AsyncCallback<Void> appCallback) {
    this.appCallback = appCallback;
    for (String addon : loading) {
      beginLoading(addon);
    }
    if (pending == 0) {
      appCallback.onSuccess(null);
    }
  }

  private void beginLoading(String addon) {
    pending++;
    Loader.injectScript(
        getAddonScriptUri(addon),
        new AsyncCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            pending--;
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
}
