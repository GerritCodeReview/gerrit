// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.extensions.common.ChangeInfo;

public class ChangeInfoUtil {
  public static <T extends ChangeInfo> T copyChangeInfoFields(ChangeInfo from, T to) {
    to.id = from.id;
    to.project = from.project;
    to.branch = from.branch;
    to.topic = from.topic;
    to.assignee = from.assignee;
    to.hashtags = from.hashtags;
    to.changeId = from.changeId;
    to.subject = from.subject;
    to.status = from.status;
    to.created = from.created;
    to.updated = from.updated;
    to.submitted = from.submitted;
    to.submitter = from.submitter;
    to.starred = from.starred;
    to.stars = from.stars;
    to.reviewed = from.reviewed;
    to.submitType = from.submitType;
    to.mergeable = from.mergeable;
    to.submittable = from.submittable;
    to.insertions = from.insertions;
    to.deletions = from.deletions;
    to.unresolvedCommentCount = from.unresolvedCommentCount;
    to.isPrivate = from.isPrivate;
    to.workInProgress = from.workInProgress;
    to.hasReviewStarted = from.hasReviewStarted;
    to.revertOf = from.revertOf;
    to._number = from._number;
    to.owner = from.owner;
    to.actions = from.actions;
    to.labels = from.labels;
    to.permittedLabels = from.permittedLabels;
    to.removableReviewers = from.removableReviewers;
    to.reviewers = from.reviewers;
    to.pendingReviewers = from.pendingReviewers;
    to.reviewerUpdates = from.reviewerUpdates;
    to.messages = from.messages;
    to.currentRevision = from.currentRevision;
    to.revisions = from.revisions;
    to._moreChanges = from._moreChanges;
    to.problems = from.problems;
    to.plugins = from.plugins;
    to.trackingIds = from.trackingIds;
    to.requirements = from.requirements;
    return to;
  }

  private ChangeInfoUtil() {}
}
