// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.client.info.ChangeInfo.EditInfo;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;

public class EditGlue {
  public static void onAction(
      ChangeInfo change, EditInfo edit, ActionInfo action, ActionButton button) {
    RestApi api = ChangeApi.edit(change.project(), change.legacyId().get()).view(action.id());

    JavaScriptObject f = get(action.id());
    if (f != null) {
      ActionContext c = ActionContext.create(api);
      c.set(action);
      c.set(change);
      c.set(edit);
      c.button(button);
      ApiGlue.invoke(f, c);
    } else {
      DefaultActions.invoke(change, action, api);
    }
  }

  private static native JavaScriptObject get(String id) /*-{
    return $wnd.Gerrit.edit_actions[id];
  }-*/;

  private EditGlue() {}
}
