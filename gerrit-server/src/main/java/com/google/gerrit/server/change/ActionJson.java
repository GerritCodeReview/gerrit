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
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.PrivateInternals_UiActionDescription;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.util.Providers;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public class ActionJson {
  private final Revisions revisions;
  private final ChangeResource.Factory changeResourceFactory;
  private final DynamicMap<RestView<ChangeResource>> changeViews;

  @Inject
  ActionJson(
      Revisions revisions,
      ChangeResource.Factory changeResourceFactory,
      DynamicMap<RestView<ChangeResource>> changeViews) {
    this.revisions = revisions;
    this.changeResourceFactory = changeResourceFactory;
    this.changeViews = changeViews;
  }

  public Map<String, ActionInfo> format(RevisionResource rsrc) {
    return toActionMap(rsrc);
  }

  public ChangeInfo addChangeActions(ChangeInfo to, ChangeControl ctl) {
    to.actions = toActionMap(ctl);
    return to;
  }

  public RevisionInfo addRevisionActions(RevisionInfo to, RevisionResource rsrc) {
    to.actions = toActionMap(rsrc);
    return to;
  }

  private Map<String, ActionInfo> toActionMap(ChangeControl ctl) {
    Map<String, ActionInfo> out = new LinkedHashMap<>();
    if (!ctl.getUser().isIdentifiedUser()) {
      return out;
    }

    Provider<CurrentUser> userProvider = Providers.of(ctl.getUser());
    for (UiAction.Description d :
        UiActions.from(changeViews, changeResourceFactory.create(ctl), userProvider)) {
      out.put(d.getId(), new ActionInfo(d));
    }

    // The followup action is a client-side only operation that does not
    // have a server side handler. It must be manually registered into the
    // resulting action map.
    if (ctl.getChange().getStatus().isOpen()) {
      UiAction.Description descr = new UiAction.Description();
      PrivateInternals_UiActionDescription.setId(descr, "followup");
      PrivateInternals_UiActionDescription.setMethod(descr, "POST");
      descr.setTitle("Create follow-up change");
      descr.setLabel("Follow-Up");
      out.put(descr.getId(), new ActionInfo(descr));
    }
    return out;
  }

  private Map<String, ActionInfo> toActionMap(RevisionResource rsrc) {
    Map<String, ActionInfo> out = new LinkedHashMap<>();
    if (rsrc.getControl().getUser().isIdentifiedUser()) {
      Provider<CurrentUser> userProvider = Providers.of(rsrc.getControl().getUser());
      for (UiAction.Description d : UiActions.from(revisions, rsrc, userProvider)) {
        out.put(d.getId(), new ActionInfo(d));
      }
    }
    return out;
  }
}
