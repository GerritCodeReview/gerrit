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

package com.google.gerrit.client.api;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.change.ActionButton;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ActionInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class ChangeGlue {
  public static void onAction(
      ChangeInfo change,
      ActionInfo action,
      ActionButton button) {
    RestApi api = ChangeApi.change(change.legacy_id().get()).view(action.id());
    JavaScriptObject f = get(action.id());
    if (f != null) {
      ActionContext c = ActionContext.create(api);
      c.set(action);
      c.set(change);
      c.button(button);
      ApiGlue.invoke(f, c);
    } else {
      simple(change, action, api);
    }
  }

  static void simple(ChangeInfo change, ActionInfo action, RestApi api) {
    final Change.Id id = change.legacy_id();
    AsyncCallback<JavaScriptObject> cb = new GerritCallback<JavaScriptObject>() {
      @Override
      public void onSuccess(JavaScriptObject msg) {
        Gerrit.display(PageLinks.toChange2(id));
      }
    };
    if ("PUT".equalsIgnoreCase(action.method())) {
      api.put(JavaScriptObject.createObject(), cb);
    } else if ("DELETE".equalsIgnoreCase(action.method())) {
      api.delete(cb);
    } else {
      api.post(JavaScriptObject.createObject(), cb);
    }
  }

  private static final native JavaScriptObject get(String id) /*-{
    return $wnd.Gerrit.change_actions[id];
  }-*/;

  private ChangeGlue() {
  }
}
