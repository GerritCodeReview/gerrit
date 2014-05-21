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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.changes.VerifyInput;
import com.google.gerrit.extensions.common.VerificationInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.CherryPick;
import com.google.gerrit.server.change.DeleteDraftPatchSet;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.Files;
import com.google.gerrit.server.change.GetVerifications;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.PostVerification;
import com.google.gerrit.server.change.Publish;
import com.google.gerrit.server.change.Rebase;
import com.google.gerrit.server.change.Reviewed;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.changedetail.RebaseChange;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

class RevisionApiImpl extends RevisionApi.NotImplemented implements RevisionApi {
  interface Factory {
    RevisionApiImpl create(RevisionResource r);
  }

  private final Changes changes;
  private final CherryPick cherryPick;
  private final DeleteDraftPatchSet deleteDraft;
  private final Rebase rebase;
  private final RebaseChange rebaseChange;
  private final Submit submit;
  private final Publish publish;
  private final Reviewed.PutReviewed putReviewed;
  private final Reviewed.DeleteReviewed deleteReviewed;
  private final RevisionResource revision;
  private final Provider<Files> files;
  private final Provider<Files.ListFiles> listFiles;
  private final Provider<PostReview> review;
  private final PostVerification verify;
  private final GetVerifications getVerifications;

  @Inject
  RevisionApiImpl(Changes changes,
      CherryPick cherryPick,
      DeleteDraftPatchSet deleteDraft,
      Rebase rebase,
      RebaseChange rebaseChange,
      Submit submit,
      Publish publish,
      Reviewed.PutReviewed putReviewed,
      Reviewed.DeleteReviewed deleteReviewed,
      Provider<Files> files,
      Provider<Files.ListFiles> listFiles,
      Provider<PostReview> review,
      PostVerification verify,
      GetVerifications getVerifications,
      @Assisted RevisionResource r) {
    this.changes = changes;
    this.cherryPick = cherryPick;
    this.deleteDraft = deleteDraft;
    this.rebase = rebase;
    this.rebaseChange = rebaseChange;
    this.review = review;
    this.submit = submit;
    this.publish = publish;
    this.files = files;
    this.putReviewed = putReviewed;
    this.deleteReviewed = deleteReviewed;
    this.listFiles = listFiles;
    this.verify = verify;
    this.getVerifications = getVerifications;
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
  public void verify(VerifyInput in) throws RestApiException {
    try {
      verify.apply(revision, in);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot post verification", e);
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
      submit.apply(revision, in);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot submit change", e);
    }
  }

  @Override
  public void publish() throws RestApiException {
    try {
      publish.apply(revision, new Publish.Input());
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot publish draft patch set", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    try {
      deleteDraft.apply(revision, null);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot delete draft ps", e);
    }
  }

  @Override
  public ChangeApi rebase() throws RestApiException {
    try {
      return changes.id(rebase.apply(revision, null)._number);
    } catch (OrmException | EmailException e) {
      throw new RestApiException("Cannot rebase ps", e);
    }
  }

  @Override
  public boolean canRebase() {
    return rebaseChange.canRebase(revision);
  }

  @Override
  public ChangeApi cherryPick(CherryPickInput in) throws RestApiException {
    try {
      return changes.id(cherryPick.apply(revision, in)._number);
    } catch (OrmException | EmailException | IOException e) {
      throw new RestApiException("Cannot cherry pick", e);
    }
  }

  @Override
  public void setReviewed(String path, boolean reviewed) throws RestApiException {
    try {
      RestModifyView<FileResource, Reviewed.Input> view;
      if (reviewed) {
        view = putReviewed;
      } else {
        view = deleteReviewed;
      }
      view.apply(
          files.get().parse(revision, IdString.fromDecoded(path)),
          new Reviewed.Input());
    } catch (Exception e) {
      throw new RestApiException("Cannot update reviewed flag", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Set<String> reviewed() throws RestApiException {
    try {
      return ImmutableSet.copyOf((Iterable<String>) listFiles
          .get().setReviewed(true)
          .apply(revision).value());
    } catch (OrmException e) {
      throw new RestApiException("Cannot list reviewed files", e);
    }
  }

  @Override
  public Map<String, VerificationInfo> verifications()
      throws RestApiException {
    try {
      return getVerifications.apply(revision);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot retrieve verifications", e);
    }
  }
}
