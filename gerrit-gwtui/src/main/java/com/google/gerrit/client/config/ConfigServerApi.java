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

package com.google.gerrit.client.config;

import com.google.gerrit.client.extensions.TopMenuList;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A collection of static methods which work on the Gerrit REST API for server
 * configuration.
 */
public class ConfigServerApi {
  /** map of the server wide capabilities (core & plugins). */
  public static void capabilities(AsyncCallback<NativeMap<CapabilityInfo>> cb) {
    new RestApi("/config/server/capabilities/").get(cb);
  }

  public static void topMenus(AsyncCallback<TopMenuList> cb) {
    new RestApi("/config/server/top-menus").get(cb);
  }
}
