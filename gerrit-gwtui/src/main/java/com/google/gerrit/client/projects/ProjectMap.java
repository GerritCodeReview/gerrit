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
import com.google.gwt.user.client.rpc.AsyncCallback;

/** Projects available from {@code /projects/}. */
public class ProjectMap extends NativeMap<ProjectInfo> {
  public static void all(AsyncCallback<ProjectMap> callback) {
    new RestApi("/projects/")
        .addParameterRaw("type", "ALL")
        .addParameterTrue("all")
        .addParameterTrue("d") // description
        .get(NativeMap.copyKeysIntoChildren(callback));
  }

  public static void permissions(AsyncCallback<ProjectMap> callback) {
    new RestApi("/projects/")
        .addParameterRaw("type", "PERMISSIONS")
        .addParameterTrue("all")
        .addParameterTrue("d") // description
        .get(NativeMap.copyKeysIntoChildren(callback));
  }

  public static void parentCandidates(AsyncCallback<ProjectMap> callback) {
    new RestApi("/projects/")
        .addParameterRaw("type", "PARENT_CANDIDATES")
        .addParameterTrue("all")
        .addParameterTrue("d") // description
        .get(NativeMap.copyKeysIntoChildren(callback));
  }

  public static void suggest(String prefix, int limit, AsyncCallback<ProjectMap> cb) {
    new RestApi("/projects/")
        .addParameter("p", prefix)
        .addParameter("n", limit)
        .addParameterRaw("type", "ALL")
        .addParameterTrue("d") // description
        .background()
        .get(NativeMap.copyKeysIntoChildren(cb));
  }

  public static void match(String match, int limit, int start, AsyncCallback<ProjectMap> cb) {
    RestApi call = new RestApi("/projects/");
    if (match != null) {
      if (match.startsWith("^")) {
        call.addParameter("r", match);
      } else {
        call.addParameter("m", match);
      }
    }
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    if (start > 0) {
      call.addParameter("S", start);
    }
    call.addParameterRaw("type", "ALL");
    call.addParameterTrue("d"); // description
    call.get(NativeMap.copyKeysIntoChildren(cb));
  }

  public static void match(String match, AsyncCallback<ProjectMap> cb) {
    match(match, 0, 0, cb);
  }

  protected ProjectMap() {}
}
