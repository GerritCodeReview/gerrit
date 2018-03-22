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

package com.google.gerrit.extensions.common;

import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.SubmitType;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ChangeInfo {
  public String id;
  public String project;
  public String branch;
  public String topic;
  public AccountInfo assignee;
  public Collection<String> hashtags;
  public String changeId;
  public String subject;
  public ChangeStatus status;
  public Timestamp created;
  public Timestamp updated;
  public Timestamp submitted;
  public AccountInfo submitter;
  public Boolean starred;
  public Collection<String> stars;
  public Boolean reviewed;
  public SubmitType submitType;
  public Boolean mergeable;
  public Boolean submittable;
  public Integer insertions;
  public Integer deletions;
  public Integer unresolvedCommentCount;
  public Boolean isPrivate;
  public Boolean workInProgress;
  public Boolean hasReviewStarted;
  public Integer revertOf;

  public int _number;

  public AccountInfo owner;

  public Map<String, ActionInfo> actions;
  public Map<String, LabelInfo> labels;
  public Map<String, Collection<String>> permittedLabels;
  public Collection<AccountInfo> removableReviewers;
  public Map<ReviewerState, Collection<AccountInfo>> reviewers;
  public Map<ReviewerState, Collection<AccountInfo>> pendingReviewers;
  public Collection<ReviewerUpdateInfo> reviewerUpdates;
  public Collection<ChangeMessageInfo> messages;

  public String currentRevision;
  public Map<String, RevisionInfo> revisions;
  public Boolean _moreChanges;

  public List<ProblemInfo> problems;
  public List<PluginDefinedInfo> plugins;
  public Collection<TrackingIdInfo> trackingIds;
  public Collection<SubmitRequirementInfo> requirements;
}
