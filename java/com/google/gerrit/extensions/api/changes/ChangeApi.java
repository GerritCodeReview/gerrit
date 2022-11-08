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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInfoDifference;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.CommitMessageInput;
import com.google.gerrit.extensions.common.MergePatchSetInput;
import com.google.gerrit.extensions.common.PureRevertInfo;
import com.google.gerrit.extensions.common.RebaseChainInfo;
import com.google.gerrit.extensions.common.RevertSubmissionInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Arrays;
import java.util.Collection;
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
  default RevisionApi current() throws RestApiException {
    return revision("current");
  }

  /**
   * Look up a revision of a change by number.
   *
   * @see #current()
   */
  default RevisionApi revision(int id) throws RestApiException {
    return revision(Integer.toString(id));
  }

  /**
   * Look up a revision of a change by commit SHA-1 or other supported revision string.
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

  default void abandon() throws RestApiException {
    abandon(new AbandonInput());
  }

  void abandon(AbandonInput in) throws RestApiException;

  default void restore() throws RestApiException {
    restore(new RestoreInput());
  }

  void restore(RestoreInput in) throws RestApiException;

  default void move(String destination) throws RestApiException {
    MoveInput in = new MoveInput();
    in.destinationBranch = destination;
    move(in);
  }

  void move(MoveInput in) throws RestApiException;

  void setPrivate(boolean value, @Nullable String message) throws RestApiException;

  default void setPrivate(boolean value) throws RestApiException {
    setPrivate(value, null);
  }

  void setWorkInProgress(@Nullable String message) throws RestApiException;

  void setReadyForReview(@Nullable String message) throws RestApiException;

  default void setWorkInProgress() throws RestApiException {
    setWorkInProgress(null);
  }

  default void setReadyForReview() throws RestApiException {
    setReadyForReview(null);
  }

  /**
   * Create a new change that reverts this change.
   *
   * @see Changes#id(int)
   */
  default ChangeApi revert() throws RestApiException {
    return revert(new RevertInput());
  }

  /**
   * Create a new change that reverts this change.
   *
   * @see Changes#id(int)
   */
  ChangeApi revert(RevertInput in) throws RestApiException;

  default RevertSubmissionInfo revertSubmission() throws RestApiException {
    return revertSubmission(new RevertInput());
  }

  RevertSubmissionInfo revertSubmission(RevertInput in) throws RestApiException;

  /** Create a merge patch set for the change. */
  ChangeInfo createMergePatchSet(MergePatchSetInput in) throws RestApiException;

  ChangeInfo applyPatch(ApplyPatchPatchSetInput in) throws RestApiException;

  default List<ChangeInfo> submittedTogether() throws RestApiException {
    SubmittedTogetherInfo info =
        submittedTogether(
            EnumSet.noneOf(ListChangesOption.class), EnumSet.noneOf(SubmittedTogetherOption.class));
    return info.changes;
  }

  default SubmittedTogetherInfo submittedTogether(EnumSet<SubmittedTogetherOption> options)
      throws RestApiException {
    return submittedTogether(EnumSet.noneOf(ListChangesOption.class), options);
  }

  SubmittedTogetherInfo submittedTogether(
      EnumSet<ListChangesOption> listOptions, EnumSet<SubmittedTogetherOption> submitOptions)
      throws RestApiException;

  /** Rebase the current revision of a change using default options. */
  default void rebase() throws RestApiException {
    rebase(new RebaseInput());
  }

  /** Rebase the current revision of a change. */
  void rebase(RebaseInput in) throws RestApiException;

  /**
   * Rebase the current revisions of a change's chain using default options.
   *
   * @return a {@code RebaseChainInfo} contains the {@code ChangeInfo} data for the rebased the
   *     chain
   */
  default Response<RebaseChainInfo> rebaseChain() throws RestApiException {
    return rebaseChain(new RebaseInput());
  }

  /**
   * Rebase the current revisions of a change's chain.
   *
   * @return a {@code RebaseChainInfo} contains the {@code ChangeInfo} data for the rebased the
   *     chain
   */
  Response<RebaseChainInfo> rebaseChain(RebaseInput in) throws RestApiException;

  /** Deletes a change. */
  void delete() throws RestApiException;

  String topic() throws RestApiException;

  void topic(String topic) throws RestApiException;

  IncludedInInfo includedIn() throws RestApiException;

  default ReviewerResult addReviewer(String reviewer) throws RestApiException {
    ReviewerInput in = new ReviewerInput();
    in.reviewer = reviewer;
    return addReviewer(in);
  }

  ReviewerResult addReviewer(ReviewerInput in) throws RestApiException;

  SuggestedReviewersRequest suggestReviewers() throws RestApiException;

  default SuggestedReviewersRequest suggestReviewers(String query) throws RestApiException {
    return suggestReviewers().withQuery(query);
  }

  default SuggestedReviewersRequest suggestCcs(String query) throws RestApiException {
    return suggestReviewers().forCc().withQuery(query);
  }

  /**
   * Retrieve reviewers ({@code ReviewerState.REVIEWER} and {@code ReviewerState.CC}) on the change.
   */
  List<ReviewerInfo> reviewers() throws RestApiException;

  ChangeInfo get(
      EnumSet<ListChangesOption> options, ImmutableListMultimap<String, String> pluginOptions)
      throws RestApiException;

  default ChangeInfo get(ImmutableListMultimap<String, String> pluginOptions)
      throws RestApiException {
    return get(EnumSet.noneOf(ListChangesOption.class), pluginOptions);
  }

  default ChangeInfo get(EnumSet<ListChangesOption> options) throws RestApiException {
    return get(options, ImmutableListMultimap.of());
  }

  default ChangeInfo get(Iterable<ListChangesOption> options) throws RestApiException {
    return get(Sets.newEnumSet(options, ListChangesOption.class));
  }

  default ChangeInfo get(ListChangesOption... options) throws RestApiException {
    return get(Arrays.asList(options));
  }

  /**
   * {@link #get(ListChangesOption...)} with all options included, except for the following.
   *
   * <ul>
   *   <li>{@code CHECK} is omitted, to skip consistency checks.
   *   <li>{@code SKIP_DIFFSTAT} is omitted to ensure diffstat calculations.
   * </ul>
   */
  default ChangeInfo get() throws RestApiException {
    return get(
        EnumSet.complementOf(EnumSet.of(ListChangesOption.CHECK, ListChangesOption.SKIP_DIFFSTAT)));
  }

  default ChangeInfoDifference metaDiff(
      @Nullable String oldMetaRevId, @Nullable String newMetaRevId) throws RestApiException {
    return metaDiff(
        oldMetaRevId,
        newMetaRevId,
        EnumSet.noneOf(ListChangesOption.class),
        ImmutableListMultimap.of());
  }

  default ChangeInfoDifference metaDiff(
      @Nullable String oldMetaRevId, @Nullable String newMetaRevId, ListChangesOption... options)
      throws RestApiException {
    return metaDiff(oldMetaRevId, newMetaRevId, Arrays.asList(options));
  }

  default ChangeInfoDifference metaDiff(
      @Nullable String oldMetaRevId,
      @Nullable String newMetaRevId,
      Collection<ListChangesOption> options)
      throws RestApiException {
    return metaDiff(
        oldMetaRevId,
        newMetaRevId,
        Sets.newEnumSet(options, ListChangesOption.class),
        ImmutableListMultimap.of());
  }

  /**
   * Gets the diff between a change's metadata with the two given refs.
   *
   * @param oldMetaRevId the SHA-1 of the 'before' metadata diffed against {@code newMetaRevId}
   * @param newMetaRevId the SHA-1 of the 'after' metadata diffed against {@code oldMetaRevId}
   */
  ChangeInfoDifference metaDiff(
      @Nullable String oldMetaRevId,
      @Nullable String newMetaRevId,
      EnumSet<ListChangesOption> options,
      ImmutableListMultimap<String, String> pluginOptions)
      throws RestApiException;

  /** {@link #get(ListChangesOption...)} with no options included. */
  default ChangeInfo info() throws RestApiException {
    return get(EnumSet.noneOf(ListChangesOption.class));
  }

  /**
   * Provides access to an API regarding the change edit of this change.
   *
   * @return a {@code ChangeEditApi} for the change edit of this change
   * @throws RestApiException if the API isn't accessible
   */
  ChangeEditApi edit() throws RestApiException;

  /** Create a new patch set with a new commit message. */
  default void setMessage(String message) throws RestApiException {
    CommitMessageInput in = new CommitMessageInput();
    in.message = message;
    setMessage(in);
  }

  /** Create a new patch set with a new commit message. */
  void setMessage(CommitMessageInput in) throws RestApiException;

  /** Set hashtags on a change */
  void setHashtags(HashtagsInput input) throws RestApiException;

  /**
   * Get hashtags on a change.
   *
   * @return hashtags
   */
  Set<String> getHashtags() throws RestApiException;

  /**
   * Manage the attention set.
   *
   * @param id The account identifier.
   */
  AttentionSetApi attention(String id) throws RestApiException;

  /** Adds a user to the attention set. */
  AccountInfo addToAttentionSet(AttentionSetInput input) throws RestApiException;

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
   * @deprecated Callers should use {@link #commentsRequest()} instead
   */
  @Deprecated
  default Map<String, List<CommentInfo>> comments() throws RestApiException {
    return commentsRequest().get();
  }

  /**
   * Get all published comments on a change as a list.
   *
   * @return comments as a list; comments have the {@code revision} field set to indicate their
   *     patch set.
   * @deprecated Callers should use {@link #commentsRequest()} instead
   */
  @Deprecated
  default List<CommentInfo> commentsAsList() throws RestApiException {
    return commentsRequest().getAsList();
  }

  /**
   * Get a {@link CommentsRequest} entity that can be used to retrieve published comments.
   *
   * @return A {@link CommentsRequest} entity that can be used to retrieve the comments using the
   *     {@link CommentsRequest#get()} or {@link CommentsRequest#getAsList()}.
   */
  CommentsRequest commentsRequest() throws RestApiException;

  /**
   * Get all robot comments on a change.
   *
   * @return robot comments in a map keyed by path; robot comments have the {@code revision} field
   *     set to indicate their patch set.
   */
  Map<String, List<RobotCommentInfo>> robotComments() throws RestApiException;

  /**
   * Get all draft comments for the current user on a change.
   *
   * @return drafts in a map keyed by path; comments have the {@code revision} field set to indicate
   *     their patch set.
   */
  default Map<String, List<CommentInfo>> drafts() throws RestApiException {
    return draftsRequest().get();
  }

  /**
   * Get all draft comments for the current user on a change as a list.
   *
   * @return drafts as a list; comments have the {@code revision} field set to indicate their patch
   *     set.
   */
  default List<CommentInfo> draftsAsList() throws RestApiException {
    return draftsRequest().getAsList();
  }

  /**
   * Get a {@link DraftsRequest} entity that can be used to retrieve draft comments.
   *
   * @return A {@link DraftsRequest} entity that can be used to retrieve the draft comments using
   *     {@link DraftsRequest#get()} or {@link DraftsRequest#getAsList()}.
   */
  DraftsRequest draftsRequest() throws RestApiException;

  ChangeInfo check() throws RestApiException;

  ChangeInfo check(FixInput fix) throws RestApiException;

  abstract class CheckSubmitRequirementRequest {
    /** Submit requirement name. */
    private String name;

    /**
     * A change ID for a change in {@link com.google.gerrit.entities.RefNames#REFS_CONFIG} branch
     * from which the submit-requirement will be loaded.
     */
    private String refsConfigChangeId;

    public abstract SubmitRequirementResultInfo get() throws RestApiException;

    public CheckSubmitRequirementRequest srName(String srName) {
      this.name = srName;
      return this;
    }

    public CheckSubmitRequirementRequest refsConfigChangeId(String changeId) {
      this.refsConfigChangeId = changeId;
      return this;
    }

    protected String srName() {
      return name;
    }

    protected String getRefsConfigChangeId() {
      return refsConfigChangeId;
    }
  }

  CheckSubmitRequirementRequest checkSubmitRequirementRequest() throws RestApiException;

  /** Returns the result of evaluating the {@link SubmitRequirementInput} input on the change. */
  SubmitRequirementResultInfo checkSubmitRequirement(SubmitRequirementInput input)
      throws RestApiException;

  void index() throws RestApiException;

  /** Check if this change is a pure revert of the change stored in revertOf. */
  PureRevertInfo pureRevert() throws RestApiException;

  /** Check if this change is a pure revert of claimedOriginal (SHA1 in 40 digit hex). */
  PureRevertInfo pureRevert(String claimedOriginal) throws RestApiException;

  /**
   * Get all messages of a change with detailed account info.
   *
   * @return a list of messages sorted by their creation time.
   */
  List<ChangeMessageInfo> messages() throws RestApiException;

  /**
   * Look up a change message of a change by its id.
   *
   * @param id the id of the change message. In NoteDb, this id is the {@code ObjectId} of a commit
   *     on the change meta branch.
   * @return API for accessing a change message.
   * @throws RestApiException if the id is invalid.
   */
  ChangeMessageApi message(String id) throws RestApiException;

  abstract class CommentsRequest {
    private boolean enableContext;
    private int contextPadding;

    /**
     * Get all published comments on a change.
     *
     * @return comments in a map keyed by path; comments have the {@code revision} field set to
     *     indicate their patch set.
     */
    public abstract Map<String, List<CommentInfo>> get() throws RestApiException;

    /**
     * Get all published comments on a change as a list.
     *
     * @return comments as a list; comments have the {@code revision} field set to indicate their
     *     patch set.
     */
    public abstract List<CommentInfo> getAsList() throws RestApiException;

    public CommentsRequest withContext(boolean enableContext) {
      this.enableContext = enableContext;
      return this;
    }

    public CommentsRequest contextPadding(int contextPadding) {
      this.contextPadding = contextPadding;
      return this;
    }

    public CommentsRequest withContext() {
      this.enableContext = true;
      return this;
    }

    public boolean getContext() {
      return enableContext;
    }

    public int getContextPadding() {
      return contextPadding;
    }
  }

  abstract class DraftsRequest extends CommentsRequest {}

  abstract class SuggestedReviewersRequest {
    private String query;
    private int limit;
    private boolean excludeGroups;
    private ReviewerState reviewerState = ReviewerState.REVIEWER;

    public abstract List<SuggestedReviewerInfo> get() throws RestApiException;

    public SuggestedReviewersRequest withQuery(String query) {
      this.query = query;
      return this;
    }

    public SuggestedReviewersRequest withLimit(int limit) {
      this.limit = limit;
      return this;
    }

    public SuggestedReviewersRequest excludeGroups(boolean excludeGroups) {
      this.excludeGroups = excludeGroups;
      return this;
    }

    public SuggestedReviewersRequest forCc() {
      this.reviewerState = ReviewerState.CC;
      return this;
    }

    public String getQuery() {
      return query;
    }

    public int getLimit() {
      return limit;
    }

    public boolean getExcludeGroups() {
      return excludeGroups;
    }

    public ReviewerState getReviewerState() {
      return reviewerState;
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
    public ReviewerApi reviewer(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public RevisionApi revision(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void abandon(AbandonInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void restore(RestoreInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void move(MoveInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void setPrivate(boolean value, @Nullable String message) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void setWorkInProgress(String message) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void setReadyForReview(String message) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi revert(RevertInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public RevertSubmissionInfo revertSubmission(RevertInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void rebase(RebaseInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Response<RebaseChainInfo> rebaseChain(RebaseInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void delete() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public String topic() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void topic(String topic) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public IncludedInInfo includedIn() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ReviewerResult addReviewer(ReviewerInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public SuggestedReviewersRequest suggestReviewers() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public SuggestedReviewersRequest suggestReviewers(String query) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<ReviewerInfo> reviewers() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo get(
        EnumSet<ListChangesOption> options, ImmutableListMultimap<String, String> pluginOptions)
        throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfoDifference metaDiff(
        @Nullable String oldMetaRevId,
        @Nullable String newMetaRevId,
        EnumSet<ListChangesOption> options,
        ImmutableListMultimap<String, String> pluginOptions)
        throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void setMessage(CommitMessageInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeEditApi edit() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void setHashtags(HashtagsInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Set<String> getHashtags() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public AttentionSetApi attention(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public AccountInfo addToAttentionSet(AttentionSetInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public AccountInfo setAssignee(AssigneeInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public AccountInfo getAssignee() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<AccountInfo> getPastAssignees() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public AccountInfo deleteAssignee() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    @Deprecated
    public Map<String, List<CommentInfo>> comments() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    @Deprecated
    public List<CommentInfo> commentsAsList() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CommentsRequest commentsRequest() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<RobotCommentInfo>> robotComments() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<CommentInfo>> drafts() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<CommentInfo> draftsAsList() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public DraftsRequest draftsRequest() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo check() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo check(FixInput fix) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CheckSubmitRequirementRequest checkSubmitRequirementRequest() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public SubmitRequirementResultInfo checkSubmitRequirement(SubmitRequirementInput input)
        throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void index() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<ChangeInfo> submittedTogether() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public SubmittedTogetherInfo submittedTogether(EnumSet<SubmittedTogetherOption> options)
        throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public SubmittedTogetherInfo submittedTogether(
        EnumSet<ListChangesOption> a, EnumSet<SubmittedTogetherOption> b) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo createMergePatchSet(MergePatchSetInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo applyPatch(ApplyPatchPatchSetInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public PureRevertInfo pureRevert() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public PureRevertInfo pureRevert(String claimedOriginal) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<ChangeMessageInfo> messages() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeMessageApi message(String id) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
