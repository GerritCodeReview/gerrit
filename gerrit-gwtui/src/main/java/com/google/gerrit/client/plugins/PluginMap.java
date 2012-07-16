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

package com.google.gerrit.client.plugins;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gwtjsonrpc.common.AsyncCallback;

import java.util.List;

/** Plugins available from {@code /plugins/}. */
public class PluginMap extends NativeMap<PluginInfo> {
  public static void all(AsyncCallback<PluginMap> callback) {
    new RestApi("/plugins/").addParameterTrue("all")
        .send(NativeMap.copyKeysIntoChildren(callback));
  }

  public static void disable(List<String> pluginNames,
      ScreenLoadCallback<PluginDisabled> callback) {
    if (pluginNames == null || pluginNames.isEmpty()) {
      callback.onSuccess(null);
    } else {
      RestApi api = new RestApi("/plugins/");
      for (String pluginName : pluginNames) {
        api.addParameter("disable", pluginName);
      }
      api.send(callback);
    }
  }

  protected PluginMap() {
  }
}
