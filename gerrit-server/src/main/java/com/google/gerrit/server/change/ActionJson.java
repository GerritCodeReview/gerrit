// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

public class ActionJson {
  private final Provider<CurrentUser> userProvider;
  private final Revisions revisions;

  @Inject
  ActionJson(
      Provider<CurrentUser> user,
      Revisions revisions) {
    this.userProvider = user;
    this.revisions = revisions;
  }

  public Map<String, ActionInfo> format(RevisionResource rsrc) {
    return toActionMap(rsrc);
  }

  private Map<String, ActionInfo> toActionMap(RevisionResource rsrc) {
    Map<String, ActionInfo> out = new LinkedHashMap<>();
    if (userProvider.get().isIdentifiedUser()) {
      for (UiAction.Description d : UiActions.from(
          revisions, rsrc, userProvider)) {
        out.put(d.getId(), new ActionInfo(d));
      }
    }
    return out;
  }
}
