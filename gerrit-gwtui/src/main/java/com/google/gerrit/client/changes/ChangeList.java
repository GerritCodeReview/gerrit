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

import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;
import java.util.Set;

/** List of changes available from {@code /changes/}. */
public class ChangeList extends JsArray<ChangeInfo> {
  private static final String URI = "/changes/";

  /** Run multiple queries in a single remote invocation. */
  public static void queryMultiple(
      final AsyncCallback<JsArray<ChangeList>> callback,
      Set<ListChangesOption> options,
      String... queries) {
    if (queries.length == 0) {
      return;
    }
    RestApi call = new RestApi(URI);
    for (String q : queries) {
      call.addParameterRaw("q", KeyUtil.encode(q));
    }
    addOptions(call, options);
    if (queries.length == 1) {
      // Server unwraps a single query, so wrap it back in an array for the
      // callback.
      call.get(
          new AsyncCallback<ChangeList>() {
            @Override
            public void onSuccess(ChangeList result) {
              JsArray<ChangeList> wrapped = JsArray.createArray(1).cast();
              wrapped.set(0, result);
              callback.onSuccess(wrapped);
            }

            @Override
            public void onFailure(Throwable caught) {
              callback.onFailure(caught);
            }
          });
    } else {
      call.get(callback);
    }
  }

  public static void query(
      String query, Set<ListChangesOption> options, AsyncCallback<ChangeList> callback) {
    query(query, options, callback, 0, 0);
  }

  public static void query(
      String query,
      Set<ListChangesOption> options,
      AsyncCallback<ChangeList> callback,
      int start,
      int limit) {
    RestApi call = newQuery(query);
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    addOptions(call, options);
    if (start != 0) {
      call.addParameter("S", start);
    }
    call.get(callback);
  }

  public static void addOptions(RestApi call, Set<ListChangesOption> s) {
    call.addParameterRaw("O", Integer.toHexString(ListChangesOption.toBits(s)));
  }

  private static RestApi newQuery(String query) {
    RestApi call = new RestApi(URI);
    // The server default is ?q=status:open so don't repeat it.
    if (!"status:open".equals(query) && !"is:open".equals(query)) {
      call.addParameterRaw("q", KeyUtil.encode(query));
    }
    return call;
  }

  protected ChangeList() {}
}
