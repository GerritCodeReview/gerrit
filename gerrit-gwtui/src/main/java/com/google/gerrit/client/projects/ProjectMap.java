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

package com.google.gerrit.client.projects;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwt.http.client.URL;

/** Projects available from {@code /projects/}. */
public class ProjectMap extends NativeMap<ProjectInfo> {
  public static void all(AsyncCallback<ProjectMap> callback) {
    new RestApi("/projects/")
        .addParameterRaw("type", "ALL")
        .addParameterTrue("all")
        .addParameterTrue("d") // description
        .send(NativeMap.copyKeysIntoChildren(callback));
  }

  public static void permissions(AsyncCallback<ProjectMap> callback) {
    new RestApi("/projects/")
        .addParameterRaw("type", "PERMISSIONS")
        .addParameterTrue("all")
        .addParameterTrue("d") // description
        .send(NativeMap.copyKeysIntoChildren(callback));
  }

  public static void suggest(String prefix, int limit, AsyncCallback<ProjectMap> cb) {
    new RestApi("/projects/" + URL.encode(prefix).replaceAll("[?]", "%3F"))
        .addParameterRaw("type", "ALL")
        .addParameter("n", limit)
        .addParameterTrue("d") // description
        .send(NativeMap.copyKeysIntoChildren(cb));
  }

  protected ProjectMap() {
  }
}
