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

package com.google.gerrit.httpd.rpc.actions;

import com.google.gerrit.common.audit.Audit;
import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.common.data.ActionService;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.plugins.actions.Action;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.inject.Inject;

public class ActionServiceImpl implements ActionService {

  @Inject
  DynamicSet<Action> actions;

  @Inject
  ActionServiceImpl(final DynamicSet<Action> actions) {
    this.actions = actions;
  }

  @Audit
  @SignInRequired
  public void fire(PatchSet.Id patchSetId, String actionName,
      AsyncCallback<ChangeDetail> callback) {
    for (Action action : actions) {
      if (action.getName().equals(actionName)) {
        action.firePatchSetAction(patchSetId);
      }
    }
  }
}
