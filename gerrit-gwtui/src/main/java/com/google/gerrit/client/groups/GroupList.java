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
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;

/** Groups available from {@code /groups/} or {@code /accounts/[id]/groups}. */
public class GroupList extends JsArray<GroupInfo> {
  public static void my(AsyncCallback<GroupList> callback) {
    new RestApi("/accounts/self/groups").get(callback);
  }

  public static void included(AccountGroup.UUID group, AsyncCallback<GroupList> callback) {
    new RestApi("/groups/").id(group.get()).view("groups").get(callback);
  }

  protected GroupList() {}
}
