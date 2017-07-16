// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.client.plugins;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.user.client.rpc.AsyncCallback;

/** Plugins available from {@code /plugins/}. */
public class PluginMap extends NativeMap<PluginInfo> {
  public static void all(AsyncCallback<PluginMap> callback) {
    new RestApi("/plugins/").addParameterTrue("all").get(NativeMap.copyKeysIntoChildren(callback));
  }

  public static void suggest(String match, int limit, AsyncCallback<PluginMap> cb) {
    new RestApi("/plugins/")
        .addParameter("m", match)
        .addParameter("n", limit)
        .background()
        .get(NativeMap.copyKeysIntoChildren(cb));
  }

  public static void match(String match, int limit, int start, AsyncCallback<PluginMap> cb) {
    RestApi call = new RestApi("/plugins/");
    if (match != null) {
      call.addParameter("m", match);
    }
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    if (start > 0) {
      call.addParameter("S", start);
    }
    call.get(NativeMap.copyKeysIntoChildren(cb));
  }

  public static void match(String match, AsyncCallback<PluginMap> cb) {
    match(match, 0, 0, cb);
  }

  protected PluginMap() {}
}
