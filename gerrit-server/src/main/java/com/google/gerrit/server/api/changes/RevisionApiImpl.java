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
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.CherryPick;
import com.google.gerrit.server.change.DeleteDraftPatchSet;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.Publish;
import com.google.gerrit.server.change.Rebase;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.changedetail.RebaseChange;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

class RevisionApiImpl implements RevisionApi {
  interface Factory {
    RevisionApiImpl create(RevisionResource r);
  }

  private final Changes changes;
  private final Provider<CherryPick> cherryPick;
  private final Provider<DeleteDraftPatchSet> deleteDraft;
  private final Provider<Rebase> rebase;
  private final Provider<RebaseChange> rebaseChange;
  private final Provider<PostReview> review;
  private final Provider<Submit> submit;
  private final Provider<Publish> publish;
  private final RevisionResource revision;

  @Inject
  RevisionApiImpl(Changes changes,
      Provider<CherryPick> cherryPick,
      Provider<DeleteDraftPatchSet> deleteDraft,
      Provider<Rebase> rebase,
      Provider<RebaseChange> rebaseChange,
      Provider<PostReview> review,
      Provider<Submit> submit,
      Provider<Publish> publish,
      @Assisted RevisionResource r) {
    this.changes = changes;
    this.cherryPick = cherryPick;
    this.deleteDraft = deleteDraft;
    this.rebase = rebase;
    this.rebaseChange = rebaseChange;
    this.review = review;
    this.submit = submit;
    this.publish = publish;
    this.revision = r;
  }

  @Override
  public void review(ReviewInput in) throws RestApiException {
    try {
      review.get().apply(revision, in);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot post review", e);
    }
  }

  @Override
  public void submit() throws RestApiException {
    SubmitInput in = new SubmitInput();
    in.waitForMerge = true;
    submit(in);
  }

  @Override
  public void submit(SubmitInput in) throws RestApiException {
    try {
      submit.get().apply(revision, in);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot submit change", e);
    }
  }

  @Override
  public void publish() throws RestApiException {
    try {
      publish.get().apply(revision, new Publish.Input());
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot publish draft patch set", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    try {
      deleteDraft.get().apply(revision, null);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot delete draft ps", e);
    }
  }

  @Override
  public ChangeApi rebase() throws RestApiException {
    try {
      return changes.id(rebase.get().apply(revision, null)._number);
    } catch (OrmException | EmailException e) {
      throw new RestApiException("Cannot rebase ps", e);
    }
  }

  @Override
  public boolean canRebase() {
    return rebaseChange.get().canRebase(revision);
  }

  @Override
  public ChangeApi cherryPick(CherryPickInput in) throws RestApiException {
    try {
      return changes.id(cherryPick.get().apply(revision, in)._number);
    } catch (OrmException | EmailException | IOException e) {
      throw new RestApiException("Cannot cherry pick", e);
    }
  }
}
