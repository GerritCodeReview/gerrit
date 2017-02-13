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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.ActionVisitor;
import com.google.gerrit.extensions.client.ListChangesOption;
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
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.util.Providers;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ActionJson {
  private final Revisions revisions;
  private final ChangeJson.Factory changeJsonFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final DynamicMap<RestView<ChangeResource>> changeViews;
  private final DynamicSet<ActionVisitor> visitorSet;

  @Inject
  ActionJson(
      Revisions revisions,
      ChangeJson.Factory changeJsonFactory,
      ChangeResource.Factory changeResourceFactory,
      DynamicMap<RestView<ChangeResource>> changeViews,
      DynamicSet<ActionVisitor> visitorSet) {
    this.revisions = revisions;
    this.changeJsonFactory = changeJsonFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.changeViews = changeViews;
    this.visitorSet = visitorSet;
  }

  public Map<String, ActionInfo> format(RevisionResource rsrc) throws OrmException {
    ChangeInfo changeInfo = null;
    RevisionInfo revisionInfo = null;
    List<ActionVisitor> visitors = visitors();
    if (!visitors.isEmpty()) {
      changeInfo = changeJson().format(rsrc);
      revisionInfo = checkNotNull(Iterables.getOnlyElement(changeInfo.revisions.values()));
      changeInfo.revisions = null;
    }
    return toActionMap(rsrc, visitors, changeInfo, revisionInfo);
  }

  private ChangeJson changeJson() {
    return changeJsonFactory.create(EnumSet.noneOf(ListChangesOption.class));
  }

  private ArrayList<ActionVisitor> visitors() {
    return Lists.newArrayList(visitorSet);
  }

  public ChangeInfo addChangeActions(ChangeInfo to, ChangeControl ctl) {
    List<ActionVisitor> visitors = visitors();
    to.actions = toActionMap(ctl, visitors, copy(visitors, to));
    return to;
  }

  public RevisionInfo addRevisionActions(
      @Nullable ChangeInfo changeInfo, RevisionInfo to, RevisionResource rsrc) throws OrmException {
    List<ActionVisitor> visitors = visitors();
    if (!visitors.isEmpty()) {
      if (changeInfo != null) {
        changeInfo = copy(visitors, changeInfo);
      } else {
        changeInfo = changeJson().format(rsrc);
      }
    }
    to.actions = toActionMap(rsrc, visitors, changeInfo, copy(visitors, to));
    return to;
  }

  private ChangeInfo copy(List<ActionVisitor> visitors, ChangeInfo changeInfo) {
    if (visitors.isEmpty()) {
      return null;
    }
    // Include all fields from ChangeJson#toChangeInfo that are not protected by
    // any ListChangesOptions.
    ChangeInfo copy = new ChangeInfo();
    copy.project = changeInfo.project;
    copy.branch = changeInfo.branch;
    copy.topic = changeInfo.topic;
    copy.assignee = changeInfo.assignee;
    copy.hashtags = changeInfo.hashtags;
    copy.changeId = changeInfo.changeId;
    copy.submitType = changeInfo.submitType;
    copy.mergeable = changeInfo.mergeable;
    copy.insertions = changeInfo.insertions;
    copy.deletions = changeInfo.deletions;
    copy.subject = changeInfo.subject;
    copy.status = changeInfo.status;
    copy.owner = changeInfo.owner;
    copy.created = changeInfo.created;
    copy.updated = changeInfo.updated;
    copy._number = changeInfo._number;
    copy.starred = changeInfo.starred;
    copy.stars = changeInfo.stars;
    copy.submitted = changeInfo.submitted;
    copy.id = changeInfo.id;
    return copy;
  }

  private RevisionInfo copy(List<ActionVisitor> visitors, RevisionInfo revisionInfo) {
    if (visitors.isEmpty()) {
      return null;
    }
    // Include all fields from ChangeJson#toRevisionInfo that are not protected
    // by any ListChangesOptions.
    RevisionInfo copy = new RevisionInfo();
    copy.isCurrent = revisionInfo.isCurrent;
    copy._number = revisionInfo._number;
    copy.ref = revisionInfo.ref;
    copy.created = revisionInfo.created;
    copy.uploader = revisionInfo.uploader;
    copy.fetch = revisionInfo.fetch;
    copy.kind = revisionInfo.kind;
    copy.description = revisionInfo.description;
    return copy;
  }

  private Map<String, ActionInfo> toActionMap(
      ChangeControl ctl, List<ActionVisitor> visitors, ChangeInfo changeInfo) {
    Map<String, ActionInfo> out = new LinkedHashMap<>();
    if (!ctl.getUser().isIdentifiedUser()) {
      return out;
    }

    Provider<CurrentUser> userProvider = Providers.of(ctl.getUser());
    FluentIterable<UiAction.Description> descs =
        UiActions.from(changeViews, changeResourceFactory.create(ctl), userProvider);
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

    ACTION:
    for (UiAction.Description d : descs) {
      ActionInfo actionInfo = new ActionInfo(d);
      for (ActionVisitor visitor : visitors) {
        if (!visitor.visit(d.getId(), actionInfo, changeInfo)) {
          continue ACTION;
        }
      }
      out.put(d.getId(), actionInfo);
    }
    return out;
  }

  private Map<String, ActionInfo> toActionMap(
      RevisionResource rsrc,
      List<ActionVisitor> visitors,
      ChangeInfo changeInfo,
      RevisionInfo revisionInfo) {
    if (!rsrc.getControl().getUser().isIdentifiedUser()) {
      return ImmutableMap.of();
    }
    Map<String, ActionInfo> out = new LinkedHashMap<>();
    Provider<CurrentUser> userProvider = Providers.of(rsrc.getControl().getUser());
    ACTION:
    for (UiAction.Description d : UiActions.from(revisions, rsrc, userProvider)) {
      ActionInfo actionInfo = new ActionInfo(d);
      for (ActionVisitor visitor : visitors) {
        if (!visitor.visit(d.getId(), actionInfo, changeInfo, revisionInfo)) {
          continue ACTION;
        }
      }
      out.put(d.getId(), actionInfo);
    }
    return out;
  }
}
