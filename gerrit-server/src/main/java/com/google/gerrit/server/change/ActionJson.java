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

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.api.changes.ActionFilter;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ActionJson {
  private final Revisions revisions;
  private final ChangeResource.Factory changeResourceFactory;
  private final DynamicMap<RestView<ChangeResource>> changeViews;
  private final DynamicSet<ActionFilter> visitorSet;

  @Inject
  ActionJson(
      Revisions revisions,
      ChangeResource.Factory changeResourceFactory,
      DynamicMap<RestView<ChangeResource>> changeViews,
      DynamicSet<ActionFilter> visitorSet) {
    this.revisions = revisions;
    this.changeResourceFactory = changeResourceFactory;
    this.changeViews = changeViews;
    this.visitorSet = visitorSet;
  }

  public Map<String, ActionInfo> format(RevisionResource rsrc) {
    // TODO: generate from changeJson
    List<ActionFilter> visitors = visitors();
    return toActionMap(rsrc, visitors, null, null);
  }

  private ArrayList<ActionFilter> visitors() {
    return Lists.newArrayList(visitorSet);
  }

  public ChangeInfo addChangeActions(ChangeInfo to, ChangeControl ctl) {
    List<ActionFilter> visitors = visitors();
    to.actions = toActionMap(ctl, visitors, copy(visitors, to));
    return to;
  }

  public RevisionInfo addRevisionActions(ChangeInfo changeInfo, RevisionInfo to,
      RevisionResource rsrc) {
    List<ActionFilter> visitors = visitors();
    to.actions = toActionMap(
        rsrc, visitors, copy(visitors, changeInfo), copy(visitors, to));
    return to;
  }

  private ChangeInfo copy(List<ActionFilter> visitors, ChangeInfo changeInfo) {
    if (visitors.isEmpty()) {
      return null;
    }
    // TODO
    return null;
  }

  private RevisionInfo copy(List<ActionFilter> visitors,
      RevisionInfo revisionInfo) {
    if (visitors.isEmpty()) {
      return null;
    }
    // TODO
    return null;
  }

  private Map<String, ActionInfo> toActionMap(
      ChangeControl ctl, List<ActionFilter> visitors, ChangeInfo changeInfo) {
    Map<String, ActionInfo> out = new LinkedHashMap<>();
    if (!ctl.getUser().isIdentifiedUser()) {
      return out;
    }

    Provider<CurrentUser> userProvider = Providers.of(ctl.getUser());
    FluentIterable<UiAction.Description> descs = UiActions.from(
        changeViews,
        changeResourceFactory.create(ctl),
        userProvider);
    // The followup action is a client-side only operation that does not
    // have a server side handler. It must be manually registered into the
    // resulting action map.
    if (ctl.getChange().getStatus().isOpen()) {
      UiAction.Description descr = new UiAction.Description();
      PrivateInternals_UiActionDescription.setId(descr, "followup");
      PrivateInternals_UiActionDescription.setMethod(descr, "POST");
      descr.setTitle("Create follow-up change");
      descr.setLabel("Follow-Up");
      descs = descs.append(descr);
    }

    ACTION: for (UiAction.Description d : descs) {
      ActionInfo info = new ActionInfo(d);
      for (ActionFilter visitor : visitors) {
        if (!visitor.visit(info, changeInfo)) {
          continue ACTION;
        }
      }
      out.put(d.getId(), info);
    }
    return out;
  }

  private Map<String, ActionInfo> toActionMap(RevisionResource rsrc,
      List<ActionFilter> visitors,
      ChangeInfo changeInfo,
      RevisionInfo revisionInfo) {
    if (!rsrc.getControl().getUser().isIdentifiedUser()) {
      return ImmutableMap.of();
    }
    Map<String, ActionInfo> out = new LinkedHashMap<>();
    Provider<CurrentUser> userProvider = Providers.of(
        rsrc.getControl().getUser());
    ACTION: for (UiAction.Description d : UiActions.from(
        revisions, rsrc, userProvider)) {
      ActionInfo info = new ActionInfo(d);
      for (ActionFilter visitor : visitors) {
        if (!visitor.visit(info, changeInfo, revisionInfo)) {
          continue ACTION;
        }
      }
      out.put(d.getId(), info);
    }
    return out;
  }
}
