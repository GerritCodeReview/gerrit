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

package com.google.gerrit.server.api.changes;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ChangeEditApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.IncludedInInfo;
import com.google.gerrit.extensions.api.changes.MoveInput;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.RestoreInput;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.api.changes.ReviewerApi;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmittedTogetherInfo;
import com.google.gerrit.extensions.api.changes.SubmittedTogetherOption;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.MergePatchSetInput;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.Abandon;
import com.google.gerrit.server.change.ChangeIncludedIn;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.Check;
import com.google.gerrit.server.change.CreateMergePatchSet;
import com.google.gerrit.server.change.DeleteAssignee;
import com.google.gerrit.server.change.DeleteChange;
import com.google.gerrit.server.change.DeletePrivate;
import com.google.gerrit.server.change.GetAssignee;
import com.google.gerrit.server.change.GetHashtags;
import com.google.gerrit.server.change.GetPastAssignees;
import com.google.gerrit.server.change.GetTopic;
import com.google.gerrit.server.change.Ignore;
import com.google.gerrit.server.change.Index;
import com.google.gerrit.server.change.ListChangeComments;
import com.google.gerrit.server.change.ListChangeDrafts;
import com.google.gerrit.server.change.ListChangeRobotComments;
import com.google.gerrit.server.change.Move;
import com.google.gerrit.server.change.Mute;
import com.google.gerrit.server.change.PostHashtags;
import com.google.gerrit.server.change.PostPrivate;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.change.PublishDraftPatchSet;
import com.google.gerrit.server.change.PutAssignee;
import com.google.gerrit.server.change.PutTopic;
import com.google.gerrit.server.change.Rebase;
import com.google.gerrit.server.change.Restore;
import com.google.gerrit.server.change.Revert;
import com.google.gerrit.server.change.Reviewers;
import com.google.gerrit.server.change.Revisions;
import com.google.gerrit.server.change.SetPrivateOp;
import com.google.gerrit.server.change.SetReadyForReview;
import com.google.gerrit.server.change.SetWorkInProgress;
import com.google.gerrit.server.change.SubmittedTogether;
import com.google.gerrit.server.change.SuggestChangeReviewers;
import com.google.gerrit.server.change.Unignore;
import com.google.gerrit.server.change.Unmute;
import com.google.gerrit.server.change.WorkInProgressOp;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ChangeApiImpl implements ChangeApi {
  interface Factory {
    ChangeApiImpl create(ChangeResource change);
  }

  private final Changes changeApi;
  private final Reviewers reviewers;
  private final Revisions revisions;
  private final ReviewerApiImpl.Factory reviewerApi;
  private final RevisionApiImpl.Factory revisionApi;
  private final SuggestChangeReviewers suggestReviewers;
  private final ChangeResource change;
  private final Abandon abandon;
  private final Revert revert;
  private final Restore restore;
  private final CreateMergePatchSet updateByMerge;
  private final Provider<SubmittedTogether> submittedTogether;
  private final PublishDraftPatchSet.CurrentRevision publishDraftChange;
  private final Rebase.CurrentRevision rebase;
  private final DeleteChange deleteChange;
  private final GetTopic getTopic;
  private final PutTopic putTopic;
  private final ChangeIncludedIn includedIn;
  private final PostReviewers postReviewers;
  private final ChangeJson.Factory changeJson;
  private final PostHashtags postHashtags;
  private final GetHashtags getHashtags;
  private final PutAssignee putAssignee;
  private final GetAssignee getAssignee;
  private final GetPastAssignees getPastAssignees;
  private final DeleteAssignee deleteAssignee;
  private final ListChangeComments listComments;
  private final ListChangeRobotComments listChangeRobotComments;
  private final ListChangeDrafts listDrafts;
  private final ChangeEditApiImpl.Factory changeEditApi;
  private final Check check;
  private final Index index;
  private final Move move;
  private final PostPrivate postPrivate;
  private final DeletePrivate deletePrivate;
  private final Ignore ignore;
  private final Unignore unignore;
  private final Mute mute;
  private final Unmute unmute;
  private final SetWorkInProgress setWip;
  private final SetReadyForReview setReady;

  @Inject
  ChangeApiImpl(
      Changes changeApi,
      Reviewers reviewers,
      Revisions revisions,
      ReviewerApiImpl.Factory reviewerApi,
      RevisionApiImpl.Factory revisionApi,
      SuggestChangeReviewers suggestReviewers,
      Abandon abandon,
      Revert revert,
      Restore restore,
      CreateMergePatchSet updateByMerge,
      Provider<SubmittedTogether> submittedTogether,
      PublishDraftPatchSet.CurrentRevision publishDraftChange,
      Rebase.CurrentRevision rebase,
      DeleteChange deleteChange,
      GetTopic getTopic,
      PutTopic putTopic,
      ChangeIncludedIn includedIn,
      PostReviewers postReviewers,
      ChangeJson.Factory changeJson,
      PostHashtags postHashtags,
      GetHashtags getHashtags,
      PutAssignee putAssignee,
      GetAssignee getAssignee,
      GetPastAssignees getPastAssignees,
      DeleteAssignee deleteAssignee,
      ListChangeComments listComments,
      ListChangeRobotComments listChangeRobotComments,
      ListChangeDrafts listDrafts,
      ChangeEditApiImpl.Factory changeEditApi,
      Check check,
      Index index,
      Move move,
      PostPrivate postPrivate,
      DeletePrivate deletePrivate,
      Ignore ignore,
      Unignore unignore,
      Mute mute,
      Unmute unmute,
      SetWorkInProgress setWip,
      SetReadyForReview setReady,
      @Assisted ChangeResource change) {
    this.changeApi = changeApi;
    this.revert = revert;
    this.reviewers = reviewers;
    this.revisions = revisions;
    this.reviewerApi = reviewerApi;
    this.revisionApi = revisionApi;
    this.suggestReviewers = suggestReviewers;
    this.abandon = abandon;
    this.restore = restore;
    this.updateByMerge = updateByMerge;
    this.submittedTogether = submittedTogether;
    this.publishDraftChange = publishDraftChange;
    this.rebase = rebase;
    this.deleteChange = deleteChange;
    this.getTopic = getTopic;
    this.putTopic = putTopic;
    this.includedIn = includedIn;
    this.postReviewers = postReviewers;
    this.changeJson = changeJson;
    this.postHashtags = postHashtags;
    this.getHashtags = getHashtags;
    this.putAssignee = putAssignee;
    this.getAssignee = getAssignee;
    this.getPastAssignees = getPastAssignees;
    this.deleteAssignee = deleteAssignee;
    this.listComments = listComments;
    this.listChangeRobotComments = listChangeRobotComments;
    this.listDrafts = listDrafts;
    this.changeEditApi = changeEditApi;
    this.check = check;
    this.index = index;
    this.move = move;
    this.postPrivate = postPrivate;
    this.deletePrivate = deletePrivate;
    this.ignore = ignore;
    this.unignore = unignore;
    this.mute = mute;
    this.unmute = unmute;
    this.setWip = setWip;
    this.setReady = setReady;
    this.change = change;
  }

  @Override
  public String id() {
    return Integer.toString(change.getId().get());
  }

  @Override
  public RevisionApi current() throws RestApiException {
    return revision("current");
  }

  @Override
  public RevisionApi revision(int id) throws RestApiException {
    return revision(String.valueOf(id));
  }

  @Override
  public RevisionApi revision(String id) throws RestApiException {
    try {
      return revisionApi.create(revisions.parse(change, IdString.fromDecoded(id)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse revision", e);
    }
  }

  @Override
  public ReviewerApi reviewer(String id) throws RestApiException {
    try {
      return reviewerApi.create(reviewers.parse(change, IdString.fromDecoded(id)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse reviewer", e);
    }
  }

  @Override
  public void abandon() throws RestApiException {
    abandon(new AbandonInput());
  }

  @Override
  public void abandon(AbandonInput in) throws RestApiException {
    try {
      abandon.apply(change, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot abandon change", e);
    }
  }

  @Override
  public void restore() throws RestApiException {
    restore(new RestoreInput());
  }

  @Override
  public void restore(RestoreInput in) throws RestApiException {
    try {
      restore.apply(change, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot restore change", e);
    }
  }

  @Override
  public void move(String destination) throws RestApiException {
    MoveInput in = new MoveInput();
    in.destinationBranch = destination;
    move(in);
  }

  @Override
  public void move(MoveInput in) throws RestApiException {
    try {
      move.apply(change, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot move change", e);
    }
  }

  @Override
  public void setPrivate(boolean value, @Nullable String message) throws RestApiException {
    try {
      SetPrivateOp.Input input = new SetPrivateOp.Input(message);
      if (value) {
        postPrivate.apply(change, input);
      } else {
        deletePrivate.apply(change, input);
      }
    } catch (Exception e) {
      throw asRestApiException("Cannot change private status", e);
    }
  }

  @Override
  public void setWorkInProgress(String message) throws RestApiException {
    try {
      setWip.apply(change, new WorkInProgressOp.Input(message));
    } catch (Exception e) {
      throw asRestApiException("Cannot set work in progress state", e);
    }
  }

  @Override
  public void setReadyForReview(String message) throws RestApiException {
    try {
      setReady.apply(change, new WorkInProgressOp.Input(message));
    } catch (Exception e) {
      throw asRestApiException("Cannot set ready for review state", e);
    }
  }

  @Override
  public ChangeApi revert() throws RestApiException {
    return revert(new RevertInput());
  }

  @Override
  public ChangeApi revert(RevertInput in) throws RestApiException {
    try {
      return changeApi.id(revert.apply(change, in)._number);
    } catch (Exception e) {
      throw asRestApiException("Cannot revert change", e);
    }
  }

  @Override
  public ChangeInfo createMergePatchSet(MergePatchSetInput in) throws RestApiException {
    try {
      return updateByMerge.apply(change, in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot update change by merge", e);
    }
  }

  @Override
  public List<ChangeInfo> submittedTogether() throws RestApiException {
    SubmittedTogetherInfo info =
        submittedTogether(
            EnumSet.noneOf(ListChangesOption.class), EnumSet.noneOf(SubmittedTogetherOption.class));
    return info.changes;
  }

  @Override
  public SubmittedTogetherInfo submittedTogether(EnumSet<SubmittedTogetherOption> options)
      throws RestApiException {
    return submittedTogether(EnumSet.noneOf(ListChangesOption.class), options);
  }

  @Override
  public SubmittedTogetherInfo submittedTogether(
      EnumSet<ListChangesOption> listOptions, EnumSet<SubmittedTogetherOption> submitOptions)
      throws RestApiException {
    try {
      return submittedTogether
          .get()
          .addListChangesOption(listOptions)
          .addSubmittedTogetherOption(submitOptions)
          .applyInfo(change);
    } catch (Exception e) {
      throw asRestApiException("Cannot query submittedTogether", e);
    }
  }

  @Override
  public void publish() throws RestApiException {
    try {
      publishDraftChange.apply(change, null);
    } catch (Exception e) {
      throw asRestApiException("Cannot publish change", e);
    }
  }

  @Override
  public void rebase() throws RestApiException {
    rebase(new RebaseInput());
  }

  @Override
  public void rebase(RebaseInput in) throws RestApiException {
    try {
      rebase.apply(change, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot rebase change", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    try {
      deleteChange.apply(change, null);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete change", e);
    }
  }

  @Override
  public String topic() throws RestApiException {
    return getTopic.apply(change);
  }

  @Override
  public void topic(String topic) throws RestApiException {
    PutTopic.Input in = new PutTopic.Input();
    in.topic = topic;
    try {
      putTopic.apply(change, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot set topic", e);
    }
  }

  @Override
  public IncludedInInfo includedIn() throws RestApiException {
    try {
      return includedIn.apply(change);
    } catch (Exception e) {
      throw asRestApiException("Could not extract IncludedIn data", e);
    }
  }

  @Override
  public AddReviewerResult addReviewer(String reviewer) throws RestApiException {
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = reviewer;
    return addReviewer(in);
  }

  @Override
  public AddReviewerResult addReviewer(AddReviewerInput in) throws RestApiException {
    try {
      return postReviewers.apply(change, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot add change reviewer", e);
    }
  }

  @Override
  public SuggestedReviewersRequest suggestReviewers() throws RestApiException {
    return new SuggestedReviewersRequest() {
      @Override
      public List<SuggestedReviewerInfo> get() throws RestApiException {
        return ChangeApiImpl.this.suggestReviewers(this);
      }
    };
  }

  @Override
  public SuggestedReviewersRequest suggestReviewers(String query) throws RestApiException {
    return suggestReviewers().withQuery(query);
  }

  private List<SuggestedReviewerInfo> suggestReviewers(SuggestedReviewersRequest r)
      throws RestApiException {
    try {
      suggestReviewers.setQuery(r.getQuery());
      suggestReviewers.setLimit(r.getLimit());
      return suggestReviewers.apply(change);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve suggested reviewers", e);
    }
  }

  @Override
  public ChangeInfo get(EnumSet<ListChangesOption> s) throws RestApiException {
    try {
      return changeJson.create(s).format(change);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve change", e);
    }
  }

  @Override
  public ChangeInfo get() throws RestApiException {
    return get(EnumSet.complementOf(EnumSet.of(ListChangesOption.CHECK)));
  }

  @Override
  public EditInfo getEdit() throws RestApiException {
    return edit().get().orElse(null);
  }

  @Override
  public ChangeEditApi edit() throws RestApiException {
    return changeEditApi.create(change);
  }

  @Override
  public void message(String message) throws RestApiException {
    current().message(message);
  }

  @Override
  public ChangeInfo info() throws RestApiException {
    return get(EnumSet.noneOf(ListChangesOption.class));
  }

  @Override
  public void setHashtags(HashtagsInput input) throws RestApiException {
    try {
      postHashtags.apply(change, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot post hashtags", e);
    }
  }

  @Override
  public Set<String> getHashtags() throws RestApiException {
    try {
      return getHashtags.apply(change).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get hashtags", e);
    }
  }

  @Override
  public AccountInfo setAssignee(AssigneeInput input) throws RestApiException {
    try {
      return putAssignee.apply(change, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot set assignee", e);
    }
  }

  @Override
  public AccountInfo getAssignee() throws RestApiException {
    try {
      Response<AccountInfo> r = getAssignee.apply(change);
      return r.isNone() ? null : r.value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get assignee", e);
    }
  }

  @Override
  public List<AccountInfo> getPastAssignees() throws RestApiException {
    try {
      return getPastAssignees.apply(change).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get past assignees", e);
    }
  }

  @Override
  public AccountInfo deleteAssignee() throws RestApiException {
    try {
      Response<AccountInfo> r = deleteAssignee.apply(change, null);
      return r.isNone() ? null : r.value();
    } catch (Exception e) {
      throw asRestApiException("Cannot delete assignee", e);
    }
  }

  @Override
  public Map<String, List<CommentInfo>> comments() throws RestApiException {
    try {
      return listComments.apply(change);
    } catch (Exception e) {
      throw asRestApiException("Cannot get comments", e);
    }
  }

  @Override
  public Map<String, List<RobotCommentInfo>> robotComments() throws RestApiException {
    try {
      return listChangeRobotComments.apply(change);
    } catch (Exception e) {
      throw asRestApiException("Cannot get robot comments", e);
    }
  }

  @Override
  public Map<String, List<CommentInfo>> drafts() throws RestApiException {
    try {
      return listDrafts.apply(change);
    } catch (Exception e) {
      throw asRestApiException("Cannot get drafts", e);
    }
  }

  @Override
  public ChangeInfo check() throws RestApiException {
    try {
      return check.apply(change).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot check change", e);
    }
  }

  @Override
  public ChangeInfo check(FixInput fix) throws RestApiException {
    try {
      // TODO(dborowitz): Convert to RetryingRestModifyView. Needs to plumb BatchUpdate.Factory into
      // ConsistencyChecker.
      return check.apply(change, fix).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot check change", e);
    }
  }

  @Override
  public void index() throws RestApiException {
    try {
      index.apply(change, new Index.Input());
    } catch (Exception e) {
      throw asRestApiException("Cannot index change", e);
    }
  }

  @Override
  public void ignore(boolean ignore) throws RestApiException {
    // TODO(dborowitz): Convert to RetryingRestModifyView. Needs to plumb BatchUpdate.Factory into
    // StarredChangesUtil.
    if (ignore) {
      this.ignore.apply(change, new Ignore.Input());
    } else {
      unignore.apply(change, new Unignore.Input());
    }
  }

  @Override
  public void mute(boolean mute) throws RestApiException {
    // TODO(dborowitz): Convert to RetryingRestModifyView. Needs to plumb BatchUpdate.Factory into
    // StarredChangesUtil.
    if (mute) {
      this.mute.apply(change, new Mute.Input());
    } else {
      unmute.apply(change, new Unmute.Input());
    }
  }
}
