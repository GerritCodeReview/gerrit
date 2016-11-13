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

import com.google.gerrit.client.actions.ActionButton;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.info.ActionInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

public class ChangeGlue {
  public static void fireShowChange(ChangeInfo change, RevisionInfo rev) {
    ApiGlue.fireEvent("showchange", change, rev);
  }

  public static boolean onSubmitChange(ChangeInfo change, RevisionInfo rev) {
    JsArray<JavaScriptObject> h = ApiGlue.getEventHandlers("submitchange");
    for (int i = 0; i < h.length(); i++) {
      if (!invoke(h.get(i), change, rev)) {
        return false;
      }
    }
    return true;
  }

  public static void onAction(ChangeInfo change, ActionInfo action, ActionButton button) {
    RestApi api = ChangeApi.change(change.legacyId().get()).view(action.id());
    JavaScriptObject f = get(action.id());
    if (f != null) {
      ActionContext c = ActionContext.create(api);
      c.set(action);
      c.set(change);
      c.button(button);
      ApiGlue.invoke(f, c);
    } else {
      DefaultActions.invoke(change, action, api);
    }
  }

  private static native JavaScriptObject get(String id) /*-{
    return $wnd.Gerrit.change_actions[id];
  }-*/;

  private static native boolean invoke(JavaScriptObject h, ChangeInfo a, RevisionInfo r)
      /*-{ return h(a,r) }-*/ ;

  private ChangeGlue() {}
}
