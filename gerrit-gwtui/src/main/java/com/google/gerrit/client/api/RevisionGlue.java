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

import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ActionInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.ui.Widget;

public class RevisionGlue {
  static final NativeMap<JavaScriptObject> actions = NativeMap.create();

  public static void onAction(
      ChangeInfo change,
      RevisionInfo revision,
      ActionInfo action,
      Widget button) {
    RestApi api = ChangeApi.revision(
          change.legacy_id().get(),
          revision.name())
      .view(action.id());

    JavaScriptObject f = actions.get(action.id());
    if (f != null) {
      ActionContext c = ActionContext.create(api);
      c.set(action);
      c.set(change);
      c.set(revision);
      ApiGlue.invoke(f, c);
    } else {
      ChangeGlue.simple(change, action, api);
    }
  }

  private RevisionGlue() {
  }
}
