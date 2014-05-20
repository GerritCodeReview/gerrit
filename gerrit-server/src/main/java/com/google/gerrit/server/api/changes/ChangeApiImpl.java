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
import com.google.gerrit.extensions.api.changes.RestoreInput;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.Abandon;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.change.Restore;
import com.google.gerrit.server.change.Revert;
import com.google.gerrit.server.change.Revisions;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.EnumSet;

class ChangeApiImpl extends ChangeApi.NotImplemented implements ChangeApi {
  interface Factory {
    ChangeApiImpl create(ChangeResource change);
  }

  private final Changes changeApi;
  private final Revisions revisions;
  private final RevisionApiImpl.Factory revisionApi;
  private final ChangeResource change;
  private final Provider<Abandon> abandon;
  private final Provider<Revert> revert;
  private final Provider<Restore> restore;
  private final Provider<PostReviewers> postReviewers;
  private final ChangeJson changeJson;

  @Inject
  ChangeApiImpl(Changes changeApi,
      Revisions revisions,
      RevisionApiImpl.Factory revisionApi,
      Provider<Abandon> abandon,
      Provider<Revert> revert,
      Provider<Restore> restore,
      Provider<PostReviewers> postReviewers,
      ChangeJson changeJson,
      @Assisted ChangeResource change) {
    this.changeApi = changeApi;
    this.revert = revert;
    this.revisions = revisions;
    this.revisionApi = revisionApi;
    this.abandon = abandon;
    this.restore = restore;
    this.postReviewers = postReviewers;
    this.changeJson = changeJson;
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
    } catch (OrmException e) {
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
      abandon.get().apply(change, in);
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
      restore.get().apply(change, in);
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
      return changeApi.id(revert.get().apply(change, in)._number);
    } catch (OrmException | EmailException | IOException e) {
      throw new RestApiException("Cannot revert change", e);
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
      postReviewers.get().apply(change, in);
    } catch (OrmException | EmailException | IOException e) {
      throw new RestApiException("Cannot add change reviewer", e);
    }
  }

  @Override
  public ChangeInfo get(EnumSet<ListChangesOption> s)
      throws RestApiException {
    try {
      return ChangeInfoMapper.INSTANCE.apply(
          changeJson.addOptions(s).format(change));
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve change", e);
    }
  }

  @Override
  public ChangeInfo get() throws RestApiException {
    return get(EnumSet.allOf(ListChangesOption.class));
  }

  @Override
  public ChangeInfo info() throws RestApiException {
    return get(EnumSet.noneOf(ListChangesOption.class));
  }
}
