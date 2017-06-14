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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.MergePatchSetInput;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ChangeApi {
  String id();

  /**
   * Look up the current revision for the change.
   *
   * <p><strong>Note:</strong> This method eagerly reads the revision. Methods that mutate the
   * revision do not necessarily re-read the revision. Therefore, calling a getter method on an
   * instance after calling a mutation method on that same instance is not guaranteed to reflect the
   * mutation. It is not recommended to store references to {@code RevisionApi} instances.
   *
   * @return API for accessing the revision.
   * @throws RestApiException if an error occurred.
   */
  RevisionApi current() throws RestApiException;

  /**
   * Look up a revision of a change by number.
   *
   * @see #current()
   */
  RevisionApi revision(int id) throws RestApiException;

  /**
   * Look up a revision of a change by commit SHA-1.
   *
   * @see #current()
   */
  RevisionApi revision(String id) throws RestApiException;

  /**
   * Look up the reviewer of the change.
   *
   * <p>
   *
   * @param id ID of the account, can be a string of the format "Full Name
   *     &lt;mail@example.com&gt;", just the email address, a full name if it is unique, an account
   *     ID, a user name or 'self' for the calling user.
   * @return API for accessing the reviewer.
   * @throws RestApiException if id is not account ID or is a user that isn't known to be a reviewer
   *     for this change.
   */
  ReviewerApi reviewer(String id) throws RestApiException;

  void abandon() throws RestApiException;

  void abandon(AbandonInput in) throws RestApiException;

  void restore() throws RestApiException;

  void restore(RestoreInput in) throws RestApiException;

  void move(String destination) throws RestApiException;

  void move(MoveInput in) throws RestApiException;

  void setPrivate(boolean value, @Nullable String message) throws RestApiException;

  void setWorkInProgress(String message) throws RestApiException;

  void setReadyForReview(String message) throws RestApiException;

  default void setWorkInProgress() throws RestApiException {
    setWorkInProgress(null);
  }

  default void setReadyForReview() throws RestApiException {
    setReadyForReview(null);
  }

  /**
   * Ignore or un-ignore this change.
   *
   * @param ignore ignore the change if true
   */
  void ignore(boolean ignore) throws RestApiException;

  /**
   * Mute or un-mute this change.
   *
   * @param mute mute the change if true
   */
  void mute(boolean mute) throws RestApiException;

  /**
   * Create a new change that reverts this change.
   *
   * @see Changes#id(int)
   */
  ChangeApi revert() throws RestApiException;

  /**
   * Create a new change that reverts this change.
   *
   * @see Changes#id(int)
   */
  ChangeApi revert(RevertInput in) throws RestApiException;

  /** Create a merge patch set for the change. */
  ChangeInfo createMergePatchSet(MergePatchSetInput in) throws RestApiException;

  List<ChangeInfo> submittedTogether() throws RestApiException;

  SubmittedTogetherInfo submittedTogether(EnumSet<SubmittedTogetherOption> options)
      throws RestApiException;

  SubmittedTogetherInfo submittedTogether(
      EnumSet<ListChangesOption> listOptions, EnumSet<SubmittedTogetherOption> submitOptions)
      throws RestApiException;

  /** Publishes a draft change. */
  void publish() throws RestApiException;

  /** Rebase the current revision of a change using default options. */
  void rebase() throws RestApiException;

  /** Rebase the current revision of a change. */
  void rebase(RebaseInput in) throws RestApiException;

  /** Deletes a change. */
  void delete() throws RestApiException;

  String topic() throws RestApiException;

  void topic(String topic) throws RestApiException;

  IncludedInInfo includedIn() throws RestApiException;

  AddReviewerResult addReviewer(AddReviewerInput in) throws RestApiException;

  AddReviewerResult addReviewer(String in) throws RestApiException;

  SuggestedReviewersRequest suggestReviewers() throws RestApiException;

  SuggestedReviewersRequest suggestReviewers(String query) throws RestApiException;

  ChangeInfo get(EnumSet<ListChangesOption> options) throws RestApiException;

  /** {@code get} with {@link ListChangesOption} set to all except CHECK. */
  ChangeInfo get() throws RestApiException;
  /** {@code get} with {@link ListChangesOption} set to none. */
  ChangeInfo info() throws RestApiException;

  /**
   * Retrieve change edit when exists.
   *
   * @deprecated Replaced by {@link ChangeApi#edit()} in combination with {@link
   *     ChangeEditApi#get()}.
   */
  @Deprecated
  EditInfo getEdit() throws RestApiException;

  /**
   * Provides access to an API regarding the change edit of this change.
   *
   * @return a {@code ChangeEditApi} for the change edit of this change
   * @throws RestApiException if the API isn't accessible
   */
  ChangeEditApi edit() throws RestApiException;

  /**
   * Change the commit message of the latest {@link PatchSet} and publish result as new {@link
   * PatchSet}.
   */
  void message(String message) throws RestApiException;

  /** Set hashtags on a change */
  void setHashtags(HashtagsInput input) throws RestApiException;

  /**
   * Get hashtags on a change.
   *
   * @return hashtags
   * @throws RestApiException
   */
  Set<String> getHashtags() throws RestApiException;

  /** Set the assignee of a change. */
  AccountInfo setAssignee(AssigneeInput input) throws RestApiException;

  /** Get the assignee of a change. */
  AccountInfo getAssignee() throws RestApiException;

  /** Get all past assignees. */
  List<AccountInfo> getPastAssignees() throws RestApiException;

  /**
   * Delete the assignee of a change.
   *
   * @return the assignee that was deleted, or null if there was no assignee.
   */
  AccountInfo deleteAssignee() throws RestApiException;

  /**
   * Get all published comments on a change.
   *
   * @return comments in a map keyed by path; comments have the {@code revision} field set to
   *     indicate their patch set.
   * @throws RestApiException
   */
  Map<String, List<CommentInfo>> comments() throws RestApiException;

  /**
   * Get all robot comments on a change.
   *
   * @return robot comments in a map keyed by path; robot comments have the {@code revision} field
   *     set to indicate their patch set.
   * @throws RestApiException
   */
  Map<String, List<RobotCommentInfo>> robotComments() throws RestApiException;

  /**
   * Get all draft comments for the current user on a change.
   *
   * @return drafts in a map keyed by path; comments have the {@code revision} field set to indicate
   *     their patch set.
   * @throws RestApiException
   */
  Map<String, List<CommentInfo>> drafts() throws RestApiException;

  ChangeInfo check() throws RestApiException;

  ChangeInfo check(FixInput fix) throws RestApiException;

  void index() throws RestApiException;

  abstract class SuggestedReviewersRequest {
    private String query;
    private int limit;

    public abstract List<SuggestedReviewerInfo> get() throws RestApiException;

    public SuggestedReviewersRequest withQuery(String query) {
      this.query = query;
      return this;
    }

    public SuggestedReviewersRequest withLimit(int limit) {
      this.limit = limit;
      return this;
    }

    public String getQuery() {
      return query;
    }

    public int getLimit() {
      return limit;
    }
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements ChangeApi {
    @Override
    public String id() {
      throw new NotImplementedException();
    }

    @Override
    public RevisionApi current() {
      throw new NotImplementedException();
    }

    @Override
    public RevisionApi revision(int id) {
      throw new NotImplementedException();
    }

    @Override
    public ReviewerApi reviewer(String id) {
      throw new NotImplementedException();
    }

    @Override
    public RevisionApi revision(String id) {
      throw new NotImplementedException();
    }

    @Override
    public void abandon() {
      throw new NotImplementedException();
    }

    @Override
    public void abandon(AbandonInput in) {
      throw new NotImplementedException();
    }

    @Override
    public void restore() {
      throw new NotImplementedException();
    }

    @Override
    public void restore(RestoreInput in) {
      throw new NotImplementedException();
    }

    @Override
    public void move(String destination) {
      throw new NotImplementedException();
    }

    @Override
    public void move(MoveInput in) {
      throw new NotImplementedException();
    }

    @Override
    public void setPrivate(boolean value, @Nullable String message) {
      throw new NotImplementedException();
    }

    @Override
    public void setWorkInProgress(String message) {
      throw new NotImplementedException();
    }

    @Override
    public void setReadyForReview(String message) {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi revert() {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi revert(RevertInput in) {
      throw new NotImplementedException();
    }

    @Override
    public void publish() {
      throw new NotImplementedException();
    }

    @Override
    public void rebase() {
      throw new NotImplementedException();
    }

    @Override
    public void rebase(RebaseInput in) {
      throw new NotImplementedException();
    }

    @Override
    public void delete() {
      throw new NotImplementedException();
    }

    @Override
    public String topic() {
      throw new NotImplementedException();
    }

    @Override
    public void topic(String topic) {
      throw new NotImplementedException();
    }

    @Override
    public IncludedInInfo includedIn() {
      throw new NotImplementedException();
    }

    @Override
    public AddReviewerResult addReviewer(AddReviewerInput in) {
      throw new NotImplementedException();
    }

    @Override
    public AddReviewerResult addReviewer(String in) {
      throw new NotImplementedException();
    }

    @Override
    public SuggestedReviewersRequest suggestReviewers() {
      throw new NotImplementedException();
    }

    @Override
    public SuggestedReviewersRequest suggestReviewers(String query) {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo get(EnumSet<ListChangesOption> options) {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo get() {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo info() {
      throw new NotImplementedException();
    }

    @Override
    public void message(String message) {
      throw new NotImplementedException();
    }

    @Override
    public EditInfo getEdit() {
      throw new NotImplementedException();
    }

    @Override
    public ChangeEditApi edit() {
      throw new NotImplementedException();
    }

    @Override
    public void setHashtags(HashtagsInput input) {
      throw new NotImplementedException();
    }

    @Override
    public Set<String> getHashtags() {
      throw new NotImplementedException();
    }

    @Override
    public AccountInfo setAssignee(AssigneeInput input) {
      throw new NotImplementedException();
    }

    @Override
    public AccountInfo getAssignee() {
      throw new NotImplementedException();
    }

    @Override
    public List<AccountInfo> getPastAssignees() {
      throw new NotImplementedException();
    }

    @Override
    public AccountInfo deleteAssignee() {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<CommentInfo>> comments() {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<RobotCommentInfo>> robotComments() {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<CommentInfo>> drafts() {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo check() {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo check(FixInput fix) {
      throw new NotImplementedException();
    }

    @Override
    public void index() {
      throw new NotImplementedException();
    }

    @Override
    public List<ChangeInfo> submittedTogether() {
      throw new NotImplementedException();
    }

    @Override
    public SubmittedTogetherInfo submittedTogether(EnumSet<SubmittedTogetherOption> options) {
      throw new NotImplementedException();
    }

    @Override
    public SubmittedTogetherInfo submittedTogether(
        EnumSet<ListChangesOption> a, EnumSet<SubmittedTogetherOption> b) {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo createMergePatchSet(MergePatchSetInput in) {
      throw new NotImplementedException();
    }

    @Override
    public void ignore(boolean ignore) {
      throw new NotImplementedException();
    }

    @Override
    public void mute(boolean mute) {
      throw new NotImplementedException();
    }
  }
}
