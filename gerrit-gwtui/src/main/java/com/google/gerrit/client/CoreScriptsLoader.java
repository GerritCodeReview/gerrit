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

package com.google.gerrit.client;

import com.google.gwt.core.client.ScriptInjector;
import com.google.gwt.resources.client.DataResource;
import com.google.gwt.safehtml.shared.SafeUri;

import java.util.HashMap;
import java.util.Map;

public class CoreScriptsLoader {

  /** Map of names such as "put-change" to URI for code download. */
  private static final Map<String, SafeUri> scriptUris;

  static {
    DataResource[] all = {
        CoreScripts.I.delete_project(),
    };

    scriptUris = new HashMap<>();

    for (DataResource m : all) {
      scriptUris.put(m.getName(), m.getSafeUri());
    }
  }

  public static void load() {
    for (SafeUri url : scriptUris.values()) {
      ScriptInjector.fromUrl(url.asString())
        .setWindow(ScriptInjector.TOP_WINDOW)
        .inject();
    }
  }
}
