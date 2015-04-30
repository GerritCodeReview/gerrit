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

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.RestoreInput;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.Abandon;
import com.google.gerrit.server.change.ChangeEdits;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.Check;
import com.google.gerrit.server.change.GetHashtags;
import com.google.gerrit.server.change.GetTopic;
import com.google.gerrit.server.change.ListChangeComments;
import com.google.gerrit.server.change.ListChangeDrafts;
import com.google.gerrit.server.change.PostHashtags;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.change.PutTopic;
import com.google.gerrit.server.change.Restore;
import com.google.gerrit.server.change.Revert;
import com.google.gerrit.server.change.Revisions;
import com.google.gerrit.server.change.SuggestReviewers;
import com.google.gerrit.server.project.InvalidChangeOperationException;
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

  private final Provider<CurrentUser> user;
  private final Changes changeApi;
  private final Revisions revisions;
  private final RevisionApiImpl.Factory revisionApi;
  private final Provider<SuggestReviewers> suggestReviewers;
  private final ChangeResource change;
  private final Abandon abandon;
  private final Revert revert;
  private final Restore restore;
  private final GetTopic getTopic;
  private final PutTopic putTopic;
  private final PostReviewers postReviewers;
  private final Provider<ChangeJson> changeJson;
  private final PostHashtags postHashtags;
  private final GetHashtags getHashtags;
  private final ListChangeComments listComments;
  private final ListChangeDrafts listDrafts;
  private final Check check;
  private final ChangeEdits.Detail editDetail;

  @Inject
  ChangeApiImpl(Provider<CurrentUser> user,
      Changes changeApi,
      Revisions revisions,
      RevisionApiImpl.Factory revisionApi,
      Provider<SuggestReviewers> suggestReviewers,
      Abandon abandon,
      Revert revert,
      Restore restore,
      GetTopic getTopic,
      PutTopic putTopic,
      PostReviewers postReviewers,
      Provider<ChangeJson> changeJson,
      PostHashtags postHashtags,
      GetHashtags getHashtags,
      ListChangeComments listComments,
      ListChangeDrafts listDrafts,
      Check check,
      ChangeEdits.Detail editDetail,
      @Assisted ChangeResource change) {
    this.user = user;
    this.changeApi = changeApi;
    this.revert = revert;
    this.revisions = revisions;
    this.revisionApi = revisionApi;
    this.suggestReviewers = suggestReviewers;
    this.abandon = abandon;
    this.restore = restore;
    this.getTopic = getTopic;
    this.putTopic = putTopic;
    this.postReviewers = postReviewers;
    this.changeJson = changeJson;
    this.postHashtags = postHashtags;
    this.getHashtags = getHashtags;
    this.listComments = listComments;
    this.listDrafts = listDrafts;
    this.check = check;
    this.editDetail = editDetail;
    this.change = change;
  }

  @Override
  public String id() {
    return Integer.toString(change.getChange().getId().get());
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
      return revisionApi.create(
          revisions.parse(change, IdString.fromDecoded(id)));
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot parse revision", e);
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
    } catch (OrmException | IOException e) {
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
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot restore change", e);
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
    } catch (OrmException | EmailException | IOException e) {
      throw new RestApiException("Cannot revert change", e);
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
    } catch (OrmException | IOException e) {
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
    } catch (OrmException | EmailException | IOException e) {
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
  public SuggestedReviewersRequest suggestReviewers(String query)
      throws RestApiException {
    return suggestReviewers().withQuery(query);
  }

  private List<SuggestedReviewerInfo> suggestReviewers(SuggestedReviewersRequest r)
      throws RestApiException {
    try {
      SuggestReviewers mySuggestReviewers = suggestReviewers.get();
      mySuggestReviewers.setQuery(r.getQuery());
      mySuggestReviewers.setLimit(r.getLimit());
      return mySuggestReviewers.apply(change);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot retrieve suggested reviewers", e);
    }
  }

  @Override
  public ChangeInfo get(EnumSet<ListChangesOption> s)
      throws RestApiException {
    try {
      CurrentUser u = user.get();
      if (u.isIdentifiedUser()) {
        ((IdentifiedUser) u).clearStarredChanges();
      }
      return changeJson.get().addOptions(s).format(change);
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
    } catch (IOException | OrmException | InvalidChangeOperationException e) {
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
    } catch (IOException | OrmException e) {
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
}
