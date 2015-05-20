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

import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;

import java.util.EnumSet;

/** List of changes available from {@code /changes/}. */
public class ChangeList extends JsArray<ChangeInfo> {
  private static final String URI = "/changes/";
  // If changing default options, also update in
  // ChangeIT#defaultSearchDoesNotTouchDatabase().
  private static final EnumSet<ListChangesOption> OPTIONS = EnumSet.of(
      ListChangesOption.LABELS, ListChangesOption.DETAILED_ACCOUNTS);

  /** Run multiple queries in a single remote invocation. */
  public static void queryMultiple(
      final AsyncCallback<JsArray<ChangeList>> callback,
      EnumSet<ListChangesOption> options,
      String... queries) {
    if (queries.length == 0) {
      return;
    }
    RestApi call = new RestApi(URI);
    for (String q : queries) {
      call.addParameterRaw("q", KeyUtil.encode(q));
    }
    OPTIONS.addAll(options);
    addOptions(call, OPTIONS);
    if (queries.length == 1) {
      // Server unwraps a single query, so wrap it back in an array for the
      // callback.
      call.get(new AsyncCallback<ChangeList>() {
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

  public static void query(String query,
      EnumSet<ListChangesOption> options,
      AsyncCallback<ChangeList> callback) {
    RestApi call = newQuery(query);
    addOptions(call, options);
    call.get(callback);
  }

  public static void next(String query,
      int start, int limit,
      AsyncCallback<ChangeList> callback) {
    RestApi call = newQuery(query);
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    addOptions(call, OPTIONS);
    if (start != 0) {
      call.addParameter("S", start);
    }
    call.get(callback);
  }

  public static void addOptions(RestApi call, EnumSet<ListChangesOption> s) {
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

  protected ChangeList() {
  }
}
