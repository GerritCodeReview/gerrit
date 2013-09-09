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
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;

import java.util.EnumSet;

/** List of changes available from {@code /changes/}. */
public class ChangeList extends JsArray<ChangeInfo> {
  private static final String URI = "/changes/";

  /** Run 2 or more queries in a single remote invocation. */
  public static void query(
      AsyncCallback<JsArray<ChangeList>> callback,
      EnumSet<ListChangesOption> options,
      String... queries) {
    assert queries.length >= 2; // At least 2 is required for correct result.
    RestApi call = new RestApi(URI);
    for (String q : queries) {
      call.addParameterRaw("q", KeyUtil.encode(q));
    }

    EnumSet<ListChangesOption> o = EnumSet.of(ListChangesOption.LABELS);
    o.addAll(options);
    addOptions(call, o);
    call.get(callback);
  }

  public static void prev(String query,
      int limit, String sortkey,
      AsyncCallback<ChangeList> callback) {
    RestApi call = newQuery(query);
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    addOptions(call, EnumSet.of(ListChangesOption.LABELS));
    if (!PagedSingleListScreen.MIN_SORTKEY.equals(sortkey)) {
      call.addParameter("P", sortkey);
    }
    call.get(callback);
  }

  public static void next(String query,
      int limit, String sortkey,
      AsyncCallback<ChangeList> callback) {
    RestApi call = newQuery(query);
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    addOptions(call, EnumSet.of(ListChangesOption.LABELS));
    if (!PagedSingleListScreen.MAX_SORTKEY.equals(sortkey)) {
      call.addParameter("N", sortkey);
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
