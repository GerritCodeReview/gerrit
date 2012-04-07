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
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;

/** List of changes available from {@code /changes/}. */
public class ChangeList extends NativeList<ChangeInfo> {
  private static final String URI = "/changes/";

  public static void prev(String query,
      int limit, String sortkey,
      AsyncCallback<ChangeList> callback) {
    RestApi call = newQuery(query);
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    if (!PagedSingleListScreen.MIN_SORTKEY.equals(sortkey)) {
      call.addParameter("P", sortkey);
    }
    call.send(callback);
  }

  public static void next(String query,
      int limit, String sortkey,
      AsyncCallback<ChangeList> callback) {
    RestApi call = newQuery(query);
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    if (!PagedSingleListScreen.MAX_SORTKEY.equals(sortkey)) {
      call.addParameter("N", sortkey);
    }
    call.send(callback);
  }

  private static RestApi newQuery(String query) {
    RestApi call = new RestApi(URI);
    // The server default is ?q=status:open so don't repeat it.
    if (!"status:open".equals(query) && !"is:open".equals(query)) {
      call.addParameterRaw("q", KeyUtil.encode(query));
    }
    return call;
  }

  protected ChangeList() {
  }
}
