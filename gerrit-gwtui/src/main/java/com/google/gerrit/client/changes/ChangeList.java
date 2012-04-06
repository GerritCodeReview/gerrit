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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.rpc.NativeList;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;

/** List of changes available from {@code /changes/}. */
public class ChangeList extends NativeList<ChangeInfo> {
  public static void query(String query, AsyncCallback<ChangeList> callback) {
    api(query).send(callback);
  }

  public static void queryPrev(
      String query,
      String pos, int limit,
      AsyncCallback<ChangeList> callback) {
    RestApi api = api(query);
    if (limit > 0) {
      api.addParameter("n", limit);
    }
    if (!PagedSingleListScreen.MIN_SORTKEY.equals(pos)) {
      api.addParameter("P", pos);
    }
    api.send(callback);
  }

  public static void queryNext(
      String query,
      String pos, int limit,
      AsyncCallback<ChangeList> callback) {
    RestApi api = api(query);
    if (limit > 0) {
      api.addParameter("n", limit);
    }
    if (!PagedSingleListScreen.MAX_SORTKEY.equals(pos)) {
      api.addParameter("N", pos);
    }
    api.send(callback);
  }

  private static RestApi api(String query) {
    return new RestApi("/changes/" + KeyUtil.encode(query));
  }

  protected ChangeList() {
  }
}
