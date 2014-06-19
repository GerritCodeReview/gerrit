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
import com.google.gerrit.extensions.api.changes.FileApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.CherryPick;
import com.google.gerrit.server.change.DeleteDraftPatchSet;
import com.google.gerrit.server.change.Files;
import com.google.gerrit.server.change.Files.ListFiles;
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
import java.util.Map;

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
  private final RevisionResource revision;
  private final Provider<PostReview> review;
  private final Provider<Files> files;
  private final Provider<ListFiles> listFiles;
  private final FileApiImpl.Factory fileApi;

  @Inject
  RevisionApiImpl(Changes changes,
      CherryPick cherryPick,
      DeleteDraftPatchSet deleteDraft,
      Rebase rebase,
      RebaseChange rebaseChange,
      Submit submit,
      Publish publish,
      Provider<PostReview> review,
      Provider<Files> files,
      Provider<ListFiles> listFiles,
      FileApiImpl.Factory fileApi,
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
    this.listFiles = listFiles;
    this.fileApi = fileApi;
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
  @SuppressWarnings("unchecked")
  public Map<String, FileInfo> files() throws RestApiException {
    try {
      return (Map<String, FileInfo>)listFiles.get().apply(revision).value();
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve files", e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, FileInfo> files(String base) throws RestApiException {
    try {
      return (Map<String, FileInfo>) listFiles.get().setBase(base)
          .apply(revision).value();
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve files", e);
    }
  }

  @Override
  public FileApi file(String path) throws RestApiException {
    try {
      return fileApi.create(files.get().parse(revision,
          IdString.fromDecoded(path)));
    } catch (OrmException e) {
      throw new RestApiException("Cannot cherry pick", e);
    }
  }
}
