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

package com.google.gerrit.client.groups;

import com.google.gerrit.client.info.GroupInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.user.client.rpc.AsyncCallback;

/** Groups available from {@code /groups/}. */
public class GroupMap extends NativeMap<GroupInfo> {
  public static void all(AsyncCallback<GroupMap> callback) {
    groups().get(NativeMap.copyKeysIntoChildren(callback));
  }

  public static void match(String match, int limit, int start, AsyncCallback<GroupMap> cb) {
    RestApi call = groups();
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

  public static void suggestAccountGroupForProject(
      String project, String query, int limit, AsyncCallback<GroupMap> cb) {
    RestApi call = groups();
    if (project != null) {
      call.addParameter("p", project);
    }
    if (query != null) {
      call.addParameter("s", query);
    }
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    call.get(NativeMap.copyKeysIntoChildren(cb));
  }

  public static void myOwned(AsyncCallback<GroupMap> cb) {
    myOwnedGroups().get(NativeMap.copyKeysIntoChildren(cb));
  }

  public static void myOwned(String groupName, AsyncCallback<GroupMap> cb) {
    myOwnedGroups().addParameter("q", groupName).get(NativeMap.copyKeysIntoChildren(cb));
  }

  private static RestApi myOwnedGroups() {
    return groups().addParameterTrue("owned");
  }

  private static RestApi groups() {
    return new RestApi("/groups/");
  }

  protected GroupMap() {}
}
