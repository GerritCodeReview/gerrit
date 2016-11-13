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

import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.MoveInput;
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
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.Abandon;
import com.google.gerrit.server.change.ChangeEdits;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.Check;
import com.google.gerrit.server.change.CreateMergePatchSet;
import com.google.gerrit.server.change.DeleteAssignee;
import com.google.gerrit.server.change.DeleteChange;
import com.google.gerrit.server.change.GetAssignee;
import com.google.gerrit.server.change.GetHashtags;
import com.google.gerrit.server.change.GetPastAssignees;
import com.google.gerrit.server.change.GetTopic;
import com.google.gerrit.server.change.Index;
import com.google.gerrit.server.change.ListChangeComments;
import com.google.gerrit.server.change.ListChangeDrafts;
import com.google.gerrit.server.change.Move;
import com.google.gerrit.server.change.PostHashtags;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.change.PublishDraftPatchSet;
import com.google.gerrit.server.change.PutAssignee;
import com.google.gerrit.server.change.PutTopic;
import com.google.gerrit.server.change.Restore;
import com.google.gerrit.server.change.Revert;
import com.google.gerrit.server.change.Reviewers;
import com.google.gerrit.server.change.Revisions;
import com.google.gerrit.server.change.SubmittedTogether;
import com.google.gerrit.server.change.SuggestChangeReviewers;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
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
  private final DeleteChange deleteChange;
  private final GetTopic getTopic;
  private final PutTopic putTopic;
  private final PostReviewers postReviewers;
  private final ChangeJson.Factory changeJson;
  private final PostHashtags postHashtags;
  private final GetHashtags getHashtags;
  private final PutAssignee putAssignee;
  private final GetAssignee getAssignee;
  private final GetPastAssignees getPastAssignees;
  private final DeleteAssignee deleteAssignee;
  private final ListChangeComments listComments;
  private final ListChangeDrafts listDrafts;
  private final Check check;
  private final Index index;
  private final ChangeEdits.Detail editDetail;
  private final Move move;

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
      DeleteChange deleteChange,
      GetTopic getTopic,
      PutTopic putTopic,
      PostReviewers postReviewers,
      ChangeJson.Factory changeJson,
      PostHashtags postHashtags,
      GetHashtags getHashtags,
      PutAssignee putAssignee,
      GetAssignee getAssignee,
      GetPastAssignees getPastAssignees,
      DeleteAssignee deleteAssignee,
      ListChangeComments listComments,
      ListChangeDrafts listDrafts,
      Check check,
      Index index,
      ChangeEdits.Detail editDetail,
      Move move,
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
    this.deleteChange = deleteChange;
    this.getTopic = getTopic;
    this.putTopic = putTopic;
    this.postReviewers = postReviewers;
    this.changeJson = changeJson;
    this.postHashtags = postHashtags;
    this.getHashtags = getHashtags;
    this.putAssignee = putAssignee;
    this.getAssignee = getAssignee;
    this.getPastAssignees = getPastAssignees;
    this.deleteAssignee = deleteAssignee;
    this.listComments = listComments;
    this.listDrafts = listDrafts;
    this.check = check;
    this.index = index;
    this.editDetail = editDetail;
    this.move = move;
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
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot parse revision", e);
    }
  }

  @Override
  public ReviewerApi reviewer(String id) throws RestApiException {
    try {
      return reviewerApi.create(reviewers.parse(change, IdString.fromDecoded(id)));
    } catch (OrmException e) {
      throw new RestApiException("Cannot parse reviewer", e);
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
    } catch (OrmException | UpdateException e) {
      throw new RestApiException("Cannot abandon change", e);
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
    } catch (OrmException | UpdateException e) {
      throw new RestApiException("Cannot restore change", e);
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
    } catch (OrmException | UpdateException e) {
      throw new RestApiException("Cannot move change", e);
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
    } catch (OrmException | IOException | UpdateException | NoSuchChangeException e) {
      throw new RestApiException("Cannot revert change", e);
    }
  }

  @Override
  public ChangeInfo createMergePatchSet(MergePatchSetInput in) throws RestApiException {
    try {
      return updateByMerge.apply(change, in).value();
    } catch (IOException
        | UpdateException
        | InvalidChangeOperationException
        | NoSuchChangeException
        | OrmException e) {
      throw new RestApiException("Cannot update change by merge", e);
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
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot query submittedTogether", e);
    }
  }

  @Override
  public void publish() throws RestApiException {
    try {
      publishDraftChange.apply(change, null);
    } catch (UpdateException e) {
      throw new RestApiException("Cannot publish change", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    try {
      deleteChange.apply(change, null);
    } catch (UpdateException e) {
      throw new RestApiException("Cannot delete change", e);
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
    } catch (UpdateException e) {
      throw new RestApiException("Cannot set topic", e);
    }
  }

  @Override
  public void addReviewer(String reviewer) throws RestApiException {
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = reviewer;
    addReviewer(in);
  }

  @Override
  public void addReviewer(AddReviewerInput in) throws RestApiException {
    try {
      postReviewers.apply(change, in);
    } catch (OrmException | IOException | UpdateException e) {
      throw new RestApiException("Cannot add change reviewer", e);
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
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot retrieve suggested reviewers", e);
    }
  }

  @Override
  public ChangeInfo get(EnumSet<ListChangesOption> s) throws RestApiException {
    try {
      return changeJson.create(s).format(change);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve change", e);
    }
  }

  @Override
  public ChangeInfo get() throws RestApiException {
    return get(EnumSet.complementOf(EnumSet.of(ListChangesOption.CHECK)));
  }

  @Override
  public EditInfo getEdit() throws RestApiException {
    try {
      Response<EditInfo> edit = editDetail.apply(change);
      return edit.isNone() ? null : edit.value();
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot retrieve change edit", e);
    }
  }

  @Override
  public ChangeInfo info() throws RestApiException {
    return get(EnumSet.noneOf(ListChangesOption.class));
  }

  @Override
  public void setHashtags(HashtagsInput input) throws RestApiException {
    try {
      postHashtags.apply(change, input);
    } catch (RestApiException | UpdateException e) {
      throw new RestApiException("Cannot post hashtags", e);
    }
  }

  @Override
  public Set<String> getHashtags() throws RestApiException {
    try {
      return getHashtags.apply(change).value();
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot get hashtags", e);
    }
  }

  @Override
  public AccountInfo setAssignee(AssigneeInput input) throws RestApiException {
    try {
      return putAssignee.apply(change, input).value();
    } catch (UpdateException | IOException | OrmException e) {
      throw new RestApiException("Cannot set assignee", e);
    }
  }

  @Override
  public AccountInfo getAssignee() throws RestApiException {
    try {
      Response<AccountInfo> r = getAssignee.apply(change);
      return r.isNone() ? null : r.value();
    } catch (OrmException e) {
      throw new RestApiException("Cannot get assignee", e);
    }
  }

  @Override
  public List<AccountInfo> getPastAssignees() throws RestApiException {
    try {
      return getPastAssignees.apply(change).value();
    } catch (Exception e) {
      throw new RestApiException("Cannot get past assignees", e);
    }
  }

  @Override
  public AccountInfo deleteAssignee() throws RestApiException {
    try {
      Response<AccountInfo> r = deleteAssignee.apply(change, null);
      return r.isNone() ? null : r.value();
    } catch (UpdateException e) {
      throw new RestApiException("Cannot delete assignee", e);
    }
  }

  @Override
  public Map<String, List<CommentInfo>> comments() throws RestApiException {
    try {
      return listComments.apply(change);
    } catch (OrmException e) {
      throw new RestApiException("Cannot get comments", e);
    }
  }

  @Override
  public Map<String, List<CommentInfo>> drafts() throws RestApiException {
    try {
      return listDrafts.apply(change);
    } catch (OrmException e) {
      throw new RestApiException("Cannot get drafts", e);
    }
  }

  @Override
  public ChangeInfo check() throws RestApiException {
    try {
      return check.apply(change).value();
    } catch (OrmException e) {
      throw new RestApiException("Cannot check change", e);
    }
  }

  @Override
  public ChangeInfo check(FixInput fix) throws RestApiException {
    try {
      return check.apply(change, fix).value();
    } catch (OrmException e) {
      throw new RestApiException("Cannot check change", e);
    }
  }

  @Override
  public void index() throws RestApiException {
    try {
      index.apply(change, new Index.Input());
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot index change", e);
    }
  }
}
