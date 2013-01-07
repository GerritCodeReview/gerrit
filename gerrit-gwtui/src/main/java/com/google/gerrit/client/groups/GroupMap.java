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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.user.client.rpc.AsyncCallback;

/** Groups available from {@code /groups/}. */
public class GroupMap extends NativeMap<GroupInfo> {
  public static void all(AsyncCallback<GroupMap> callback) {
    new RestApi("/groups/")
        .get(NativeMap.copyKeysIntoChildren(callback));
  }

  public static void my(AsyncCallback<GroupMap> callback) {
    new RestApi("/groups/")
        .addParameter("user", Gerrit.getUserAccount().getId().get())
        .get(NativeMap.copyKeysIntoChildren(callback));
  }

  protected GroupMap() {
  }
}
