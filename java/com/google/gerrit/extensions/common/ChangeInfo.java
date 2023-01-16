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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.SubmitType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Representation of a change used in the API. Internally {@link
 * com.google.gerrit.server.query.change.ChangeData} and {@link com.google.gerrit.entities.Change}
 * are used.
 *
 * <p>Many fields are actually nullable.
 */
public class ChangeInfo {
  // ActionJson#copy(List, ChangeInfo) must be adapted if new fields are added that are not
  // protected by any ListChangesOption.

  public String id;
  public String project;
  public String branch;
  public String topic;
  /**
   * The <a href="https://www.gerritcodereview.com/design-docs/attention-set.html">attention set</a>
   * for this change. Keyed by account ID. We don't use {@link
   * com.google.gerrit.entities.Account.Id} to avoid a circular dependency.
   */
  public Map<Integer, AttentionSetInfo> attentionSet;

  public Map<Integer, AttentionSetInfo> removedFromAttentionSet;

  public AccountInfo assignee;
  public Collection<String> hashtags;
  public String changeId;
  public String subject;
  public ChangeStatus status;

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
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
  public Integer totalCommentCount;
  public Integer unresolvedCommentCount;
  public Boolean isPrivate;
  public Boolean workInProgress;
  public Boolean hasReviewStarted;
  public Integer revertOf;
  public String submissionId;
  public Integer cherryPickOfChange;
  public Integer cherryPickOfPatchSet;
  public String metaRevId;

  /**
   * Whether the change contains conflicts.
   *
   * <p>If {@code true}, some of the file contents of the change contain git conflict markers to
   * indicate the conflicts.
   *
   * <p>Only set if this change info is returned in response to a request that creates a new change
   * or patch set and conflicts are allowed. In particular this field is only populated if the
   * change info is returned by one of the following REST endpoints: {@link
   * com.google.gerrit.server.restapi.change.CreateChange}, {@link
   * com.google.gerrit.server.restapi.change.CreateMergePatchSet}, {@link
   * com.google.gerrit.server.restapi.change.CherryPick}, {@link
   * com.google.gerrit.server.restapi.change.CherryPickCommit}, {@link
   * com.google.gerrit.server.restapi.change.Rebase}
   */
  public Boolean containsGitConflicts;

  public Integer _number;

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
  public Collection<LegacySubmitRequirementInfo> requirements;
  public Collection<SubmitRecordInfo> submitRecords;
  public Collection<SubmitRequirementResultInfo> submitRequirements;

  public ChangeInfo() {}

  public ChangeInfo(ChangeMessageInfo... messages) {
    this.messages = ImmutableList.copyOf(messages);
  }

  public ChangeInfo(Map<String, RevisionInfo> revisions) {
    this.revisions = ImmutableMap.copyOf(revisions);
  }

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public Instant getCreated() {
    return created.toInstant();
  }

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public void setCreated(Instant when) {
    created = Timestamp.from(when);
  }

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public Instant getUpdated() {
    return updated.toInstant();
  }

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public void setUpdated(Instant when) {
    updated = Timestamp.from(when);
  }

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public Instant getSubmitted() {
    return submitted.toInstant();
  }

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public void setSubmitted(Instant when, AccountInfo who) {
    submitted = Timestamp.from(when);
    submitter = who;
  }
}
