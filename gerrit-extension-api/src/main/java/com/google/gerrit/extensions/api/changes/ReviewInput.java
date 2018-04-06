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

package com.google.gerrit.extensions.api.changes;

import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;

import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.restapi.DefaultInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Input passed to {@code POST /changes/[id]/revisions/[id]/review}. */
public class ReviewInput {
  @DefaultInput public String message;

  public String tag;

  public Map<String, Short> labels;
  public Map<String, List<CommentInput>> comments;
  public Map<String, List<RobotCommentInput>> robotComments;

  /**
   * How to process draft comments already in the database that were not also described in this
   * input request.
   *
   * <p>Defaults to DELETE, unless {@link #onBehalfOf} is set, in which case it defaults to KEEP and
   * any other value is disallowed.
   */
  public DraftHandling drafts;

  /** Who to send email notifications to after review is stored. */
  public NotifyHandling notify;

  public Map<RecipientType, NotifyInfo> notifyDetails;

  /** If true check to make sure that the comments being posted aren't already present. */
  public boolean omitDuplicateComments;

  /**
   * Account ID, name, email address or username of another user. The review will be posted/updated
   * on behalf of this named user instead of the caller. Caller must have the labelAs-$NAME
   * permission granted for each label that appears in {@link #labels}. This is in addition to the
   * named user also needing to have permission to use the labels.
   */
  public String onBehalfOf;

  /** Reviewers that should be added to this change. */
  public List<AddReviewerInput> reviewers;

  /**
   * If true mark the change as work in progress. It is an error for both {@link #workInProgress}
   * and {@link #ready} to be true.
   */
  public boolean workInProgress;

  /**
   * If true mark the change as ready for review. It is an error for both {@link #workInProgress}
   * and {@link #ready} to be true.
   */
  public boolean ready;

  public enum DraftHandling {
    /** Delete pending drafts on this revision only. */
    DELETE,

    /** Publish pending drafts on this revision only. */
    PUBLISH,

    /** Leave pending drafts alone. */
    KEEP,

    /** Publish pending drafts on all revisions. */
    PUBLISH_ALL_REVISIONS
  }

  public static class CommentInput extends Comment {}

  public static class RobotCommentInput extends CommentInput {
    public String robotId;
    public String robotRunId;
    public String url;
    public Map<String, String> properties;
    public List<FixSuggestionInfo> fixSuggestions;
  }

  public ReviewInput message(String msg) {
    message = msg != null && !msg.isEmpty() ? msg : null;
    return this;
  }

  public ReviewInput label(String name, short value) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException();
    }
    if (labels == null) {
      labels = new LinkedHashMap<>(4);
    }
    labels.put(name, value);
    return this;
  }

  public ReviewInput label(String name, int value) {
    if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
      throw new IllegalArgumentException();
    }
    return label(name, (short) value);
  }

  public ReviewInput label(String name) {
    return label(name, (short) 1);
  }

  public ReviewInput reviewer(String reviewer) {
    return reviewer(reviewer, REVIEWER, false);
  }

  public ReviewInput reviewer(String reviewer, ReviewerState state, boolean confirmed) {
    AddReviewerInput input = new AddReviewerInput();
    input.reviewer = reviewer;
    input.state = state;
    input.confirmed = confirmed;
    if (reviewers == null) {
      reviewers = new ArrayList<>();
    }
    reviewers.add(input);
    return this;
  }

  public ReviewInput setWorkInProgress(boolean workInProgress) {
    this.workInProgress = workInProgress;
    ready = !workInProgress;
    return this;
  }

  public ReviewInput setReady(boolean ready) {
    this.ready = ready;
    workInProgress = !ready;
    return this;
  }

  public static ReviewInput recommend() {
    return new ReviewInput().label("Code-Review", 1);
  }

  public static ReviewInput dislike() {
    return new ReviewInput().label("Code-Review", -1);
  }

  public static ReviewInput noScore() {
    return new ReviewInput().label("Code-Review", 0);
  }

  public static ReviewInput approve() {
    return new ReviewInput().label("Code-Review", 2);
  }

  public static ReviewInput reject() {
    return new ReviewInput().label("Code-Review", -2);
  }
}
