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

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.ActionVisitor;
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
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ActionJson {
  private final DynamicMap<RestView<RevisionResource>> revisionViews;
  private final ChangeJson.Factory changeJsonFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final UiActions uiActions;
  private final DynamicMap<RestView<ChangeResource>> changeViews;
  private final DynamicSet<ActionVisitor> visitorSet;
  private final Provider<CurrentUser> userProvider;

  @Inject
  ActionJson(
      DynamicMap<RestView<RevisionResource>> views,
      ChangeJson.Factory changeJsonFactory,
      ChangeResource.Factory changeResourceFactory,
      UiActions uiActions,
      DynamicMap<RestView<ChangeResource>> changeViews,
      DynamicSet<ActionVisitor> visitorSet,
      Provider<CurrentUser> userProvider) {
    this.revisionViews = views;
    this.changeJsonFactory = changeJsonFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.uiActions = uiActions;
    this.changeViews = changeViews;
    this.visitorSet = visitorSet;
    this.userProvider = userProvider;
  }

  public Map<String, ActionInfo> format(RevisionResource rsrc) throws StorageException {
    ChangeInfo changeInfo = null;
    RevisionInfo revisionInfo = null;
    List<ActionVisitor> visitors = visitors();
    if (!visitors.isEmpty()) {
      changeInfo = changeJson().format(rsrc);
      revisionInfo = requireNonNull(Iterables.getOnlyElement(changeInfo.revisions.values()));
      changeInfo.revisions = null;
    }
    return toActionMap(rsrc, visitors, changeInfo, revisionInfo);
  }

  private ChangeJson changeJson() {
    return changeJsonFactory.noOptions();
  }

  private ArrayList<ActionVisitor> visitors() {
    return Lists.newArrayList(visitorSet);
  }

  public ChangeInfo addChangeActions(ChangeInfo to, ChangeNotes notes) {
    List<ActionVisitor> visitors = visitors();
    to.actions = toActionMap(notes, visitors, copy(visitors, to));
    return to;
  }

  public RevisionInfo addRevisionActions(
      @Nullable ChangeInfo changeInfo, RevisionInfo to, RevisionResource rsrc)
      throws StorageException {
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
    // Include all fields from ChangeJson#toChangeInfoImpl that are not protected by any
    // ListChangesOptions.
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
    copy.hasReviewStarted = changeInfo.hasReviewStarted;
    copy.isPrivate = changeInfo.isPrivate;
    copy.subject = changeInfo.subject;
    copy.status = changeInfo.status;
    copy.owner = changeInfo.owner;
    copy.created = changeInfo.created;
    copy.updated = changeInfo.updated;
    copy._number = changeInfo._number;
    copy.requirements = changeInfo.requirements;
    copy.revertOf = changeInfo.revertOf;
    copy.starred = changeInfo.starred;
    copy.stars = changeInfo.stars;
    copy.submitted = changeInfo.submitted;
    copy.submitter = changeInfo.submitter;
    copy.unresolvedCommentCount = changeInfo.unresolvedCommentCount;
    copy.workInProgress = changeInfo.workInProgress;
    copy.id = changeInfo.id;
    return copy;
  }

  private RevisionInfo copy(List<ActionVisitor> visitors, RevisionInfo revisionInfo) {
    if (visitors.isEmpty()) {
      return null;
    }
    // Include all fields from RevisionJson#toRevisionInfo that are not protected by any
    // ListChangesOptions.
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
      ChangeNotes notes, List<ActionVisitor> visitors, ChangeInfo changeInfo) {
    CurrentUser user = userProvider.get();
    Map<String, ActionInfo> out = new LinkedHashMap<>();
    if (!user.isIdentifiedUser()) {
      return out;
    }

    Iterable<UiAction.Description> descs =
        uiActions.from(changeViews, changeResourceFactory.create(notes, user));

    // The followup action is a client-side only operation that does not
    // have a server side handler. It must be manually registered into the
    // resulting action map.
    if (!notes.getChange().isAbandoned()) {
      UiAction.Description descr = new UiAction.Description();
      PrivateInternals_UiActionDescription.setId(descr, "followup");
      PrivateInternals_UiActionDescription.setMethod(descr, "POST");
      descr.setTitle("Create follow-up change");
      descr.setLabel("Follow-Up");
      descs = Iterables.concat(descs, Collections.singleton(descr));
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
    if (!rsrc.getUser().isIdentifiedUser()) {
      return ImmutableMap.of();
    }

    Map<String, ActionInfo> out = new LinkedHashMap<>();
    ACTION:
    for (UiAction.Description d : uiActions.from(revisionViews, rsrc)) {
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
