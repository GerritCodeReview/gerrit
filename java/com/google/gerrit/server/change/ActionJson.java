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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
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
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
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
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String AI_REVIEW_ACTION_ID = "aiReview";

  private final DynamicMap<RestView<RevisionResource>> revisionViews;
  private final ChangeJson.Factory changeJsonFactory;
  private final ChangeResource.Factory changeResourceFactory;
  private final UiActions uiActions;
  private final DynamicMap<RestView<ChangeResource>> changeViews;
  private final DynamicSet<ActionVisitor> visitorSet;
  private final Provider<CurrentUser> userProvider;
  private final PermissionBackend permissionBackend;

  @Inject
  ActionJson(
      DynamicMap<RestView<RevisionResource>> views,
      ChangeJson.Factory changeJsonFactory,
      ChangeResource.Factory changeResourceFactory,
      UiActions uiActions,
      DynamicMap<RestView<ChangeResource>> changeViews,
      DynamicSet<ActionVisitor> visitorSet,
      Provider<CurrentUser> userProvider,
      PermissionBackend permissionBackend) {
    this.revisionViews = views;
    this.changeJsonFactory = changeJsonFactory;
    this.changeResourceFactory = changeResourceFactory;
    this.uiActions = uiActions;
    this.changeViews = changeViews;
    this.visitorSet = visitorSet;
    this.userProvider = userProvider;
    this.permissionBackend = permissionBackend;
  }

  public Map<String, ActionInfo> format(RevisionResource rsrc) {
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

  void addChangeActions(ChangeInfo to, ChangeData changeData) {
    List<ActionVisitor> visitors = visitors();
    to.actions = toActionMap(changeData, visitors, copy(visitors, to));
  }

  void addRevisionActions(@Nullable ChangeInfo changeInfo, RevisionInfo to, RevisionResource rsrc) {
    List<ActionVisitor> visitors = visitors();
    if (!visitors.isEmpty()) {
      if (changeInfo != null) {
        changeInfo = copy(visitors, changeInfo);
      } else {
        changeInfo = changeJson().format(rsrc);
      }
    }
    to.actions = toActionMap(rsrc, visitors, changeInfo, copy(visitors, to));
  }

  @Nullable
  private ChangeInfo copy(List<ActionVisitor> visitors, ChangeInfo changeInfo) {
    if (visitors.isEmpty()) {
      return null;
    }
    // Include all fields from ChangeJson#toChangeInfoImpl that are not protected by any
    // ListChangesOptions.
    ChangeInfo copy = new ChangeInfo();
    copy.project = changeInfo.project;
    copy.branch = changeInfo.branch;
    copy.fullBranch = changeInfo.fullBranch;
    copy.topic = changeInfo.topic;
    copy.attentionSet =
        changeInfo.attentionSet == null ? null : ImmutableMap.copyOf(changeInfo.attentionSet);
    copy.removedFromAttentionSet =
        changeInfo.removedFromAttentionSet == null
            ? null
            : ImmutableMap.copyOf(changeInfo.removedFromAttentionSet);
    copy.customKeyedValues =
        changeInfo.customKeyedValues == null
            ? null
            : ImmutableMap.copyOf(changeInfo.customKeyedValues);
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
    copy.submissionId = changeInfo.submissionId;
    copy.starred = changeInfo.starred;
    copy.submitted = changeInfo.submitted;
    copy.submitter = changeInfo.submitter;
    copy.unresolvedCommentCount = changeInfo.unresolvedCommentCount;
    copy.workInProgress = changeInfo.workInProgress;
    copy.id = changeInfo.id;
    copy.tripletId = changeInfo.tripletId;
    copy.cherryPickOfChange = changeInfo.cherryPickOfChange;
    copy.cherryPickOfPatchSet = changeInfo.cherryPickOfPatchSet;
    return copy;
  }

  @Nullable
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
    copy.branch = revisionInfo.branch;
    copy.created = revisionInfo.created;
    copy.uploader = revisionInfo.uploader;
    copy.realUploader = revisionInfo.realUploader;
    copy.fetch = revisionInfo.fetch;
    copy.kind = revisionInfo.kind;
    copy.description = revisionInfo.description;
    return copy;
  }

  private Map<String, ActionInfo> toActionMap(
      ChangeData changeData, List<ActionVisitor> visitors, ChangeInfo changeInfo) {
    try (TraceTimer timer =
        TraceContext.newTimer(
            "Get actions",
            Metadata.builder().changeId(changeData.change().getId().get()).build())) {
      CurrentUser user = userProvider.get();
      Map<String, ActionInfo> out = new LinkedHashMap<>();
      if (!user.isIdentifiedUser()) {
        return out;
      }

      Iterable<UiAction.Description> descs =
          uiActions.from(changeViews, changeResourceFactory.create(changeData, user));

      // The followup action is a client-side only operation that does not
      // have a server side handler. It must be manually registered into the
      // resulting action map.
      if (!changeData.change().isAbandoned()) {
        UiAction.Description followup =
            clientSideAction("followup", "Follow-Up", "Create follow-up change");
        PrivateInternals_UiActionDescription.setMethod(followup, "POST");
        descs = Iterables.concat(descs, Collections.singleton(followup));
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
  }

  private ImmutableMap<String, ActionInfo> toActionMap(
      RevisionResource rsrc,
      List<ActionVisitor> visitors,
      ChangeInfo changeInfo,
      RevisionInfo revisionInfo) {
    Iterable<UiAction.Description> descs =
        addAiReviewAction(
            rsrc,
            rsrc.getUser().isIdentifiedUser() ? uiActions.from(revisionViews, rsrc) : List.of());
    Map<String, ActionInfo> out = new LinkedHashMap<>();
    ACTION:
    for (UiAction.Description d : descs) {
      ActionInfo actionInfo = new ActionInfo(d);
      // Preserve explicit Description.enabled=false for aiReview; see
      // ActionInfo(Description) which otherwise maps it to null and would
      // drop the denied state from JSON.
      if (AI_REVIEW_ACTION_ID.equals(d.getId())) {
        actionInfo.enabled = false;
      }
      for (ActionVisitor visitor : visitors) {
        if (!visitor.visit(d.getId(), actionInfo, changeInfo, revisionInfo)) {
          continue ACTION;
        }
      }
      out.put(d.getId(), actionInfo);
    }
    return ImmutableMap.copyOf(out);
  }

  private Iterable<UiAction.Description> addAiReviewAction(
      RevisionResource rsrc, Iterable<UiAction.Description> descs) {
    // The aiReview action is a client-side only operation that does not have a
    // server side handler. It is emitted only when AI review is denied; the
    // frontend treats an absent entry as the default-allow state.
    try {
      boolean permitted =
          permissionBackend
              .user(rsrc.getUser())
              .change(rsrc.getChangeResource().getChangeData())
              .test(ChangePermission.AI_REVIEW);
      if (!permitted) {
        UiAction.Description aiReview =
            clientSideAction(AI_REVIEW_ACTION_ID, "AI Review", "Run AI Review on this change");
        aiReview.setEnabled(false);
        descs = Iterables.concat(descs, Collections.singleton(aiReview));
      }
    } catch (PermissionBackendException e) {
      logger.atWarning().withCause(e).log(
          "Failed to check AI review permission for change %s", rsrc.getChange().getId());
    }
    return descs;
  }

  private static UiAction.Description clientSideAction(String id, String label, String title) {
    UiAction.Description descr = new UiAction.Description();
    PrivateInternals_UiActionDescription.setId(descr, id);
    descr.setLabel(label);
    descr.setTitle(title);
    return descr;
  }
}
