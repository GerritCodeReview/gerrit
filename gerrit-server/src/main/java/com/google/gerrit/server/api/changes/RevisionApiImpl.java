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
import com.google.gerrit.extensions.api.changes.CommentApi;
import com.google.gerrit.extensions.api.changes.DraftApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.FileApi;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.CherryPick;
import com.google.gerrit.server.change.Comments;
import com.google.gerrit.server.change.CreateDraftComment;
import com.google.gerrit.server.change.DeleteDraftPatchSet;
import com.google.gerrit.server.change.DraftComments;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.Files;
import com.google.gerrit.server.change.GetPatch;
import com.google.gerrit.server.change.GetRevisionActions;
import com.google.gerrit.server.change.ListRevisionComments;
import com.google.gerrit.server.change.ListRevisionDrafts;
import com.google.gerrit.server.change.Mergeable;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.PublishDraftPatchSet;
import com.google.gerrit.server.change.Rebase;
import com.google.gerrit.server.change.RebaseUtil;
import com.google.gerrit.server.change.Reviewed;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.git.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

class RevisionApiImpl implements RevisionApi {
  interface Factory {
    RevisionApiImpl create(RevisionResource r);
  }

  private final Changes changes;
  private final CherryPick cherryPick;
  private final DeleteDraftPatchSet deleteDraft;
  private final Rebase rebase;
  private final RebaseUtil rebaseUtil;
  private final Submit submit;
  private final PublishDraftPatchSet publish;
  private final Reviewed.PutReviewed putReviewed;
  private final Reviewed.DeleteReviewed deleteReviewed;
  private final RevisionResource revision;
  private final Provider<Files> files;
  private final Provider<Files.ListFiles> listFiles;
  private final Provider<GetPatch> getPatch;
  private final Provider<PostReview> review;
  private final Provider<Mergeable> mergeable;
  private final FileApiImpl.Factory fileApi;
  private final ListRevisionComments listComments;
  private final ListRevisionDrafts listDrafts;
  private final CreateDraftComment createDraft;
  private final DraftComments drafts;
  private final DraftApiImpl.Factory draftFactory;
  private final Comments comments;
  private final CommentApiImpl.Factory commentFactory;
  private final GetRevisionActions revisionActions;

  @Inject
  RevisionApiImpl(Changes changes,
      CherryPick cherryPick,
      DeleteDraftPatchSet deleteDraft,
      Rebase rebase,
      RebaseUtil rebaseUtil,
      Submit submit,
      PublishDraftPatchSet publish,
      Reviewed.PutReviewed putReviewed,
      Reviewed.DeleteReviewed deleteReviewed,
      Provider<Files> files,
      Provider<Files.ListFiles> listFiles,
      Provider<GetPatch> getPatch,
      Provider<PostReview> review,
      Provider<Mergeable> mergeable,
      FileApiImpl.Factory fileApi,
      ListRevisionComments listComments,
      ListRevisionDrafts listDrafts,
      CreateDraftComment createDraft,
      DraftComments drafts,
      DraftApiImpl.Factory draftFactory,
      Comments comments,
      CommentApiImpl.Factory commentFactory,
      GetRevisionActions revisionActions,
      @Assisted RevisionResource r) {
    this.changes = changes;
    this.cherryPick = cherryPick;
    this.deleteDraft = deleteDraft;
    this.rebase = rebase;
    this.rebaseUtil = rebaseUtil;
    this.review = review;
    this.submit = submit;
    this.publish = publish;
    this.files = files;
    this.putReviewed = putReviewed;
    this.deleteReviewed = deleteReviewed;
    this.listFiles = listFiles;
    this.getPatch = getPatch;
    this.mergeable = mergeable;
    this.fileApi = fileApi;
    this.listComments = listComments;
    this.listDrafts = listDrafts;
    this.createDraft = createDraft;
    this.drafts = drafts;
    this.draftFactory = draftFactory;
    this.comments = comments;
    this.commentFactory = commentFactory;
    this.revisionActions = revisionActions;
    this.revision = r;
  }

  @Override
  public void review(ReviewInput in) throws RestApiException {
    try {
      review.get().apply(revision, in);
    } catch (OrmException | UpdateException e) {
      throw new RestApiException("Cannot post review", e);
    }
  }

  @Override
  public void submit() throws RestApiException {
    SubmitInput in = new SubmitInput();
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
      publish.apply(revision, new PublishDraftPatchSet.Input());
    } catch (UpdateException e) {
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
    RebaseInput in = new RebaseInput();
    return rebase(in);
  }

  @Override
  public ChangeApi rebase(RebaseInput in) throws RestApiException {
    try {
      return changes.id(rebase.apply(revision, in)._number);
    } catch (OrmException | EmailException | UpdateException | IOException e) {
      throw new RestApiException("Cannot rebase ps", e);
    }
  }

  @Override
  public boolean canRebase() {
    return rebaseUtil.canRebase(revision);
  }

  @Override
  public ChangeApi cherryPick(CherryPickInput in) throws RestApiException {
    try {
      return changes.id(cherryPick.apply(revision, in)._number);
    } catch (OrmException | IOException | UpdateException e) {
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
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot list reviewed files", e);
    }
  }

  @Override
  public MergeableInfo mergeable() throws RestApiException {
    try {
      return mergeable.get().apply(revision);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot check mergeability", e);
    }
  }

  @Override
  public MergeableInfo mergeableOtherBranches() throws RestApiException {
    try {
      Mergeable m = mergeable.get();
      m.setOtherBranches(true);
      return m.apply(revision);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot check mergeability", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, FileInfo> files() throws RestApiException {
    try {
      return (Map<String, FileInfo>)listFiles.get().apply(revision).value();
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot retrieve files", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, FileInfo> files(String base) throws RestApiException {
    try {
      return (Map<String, FileInfo>) listFiles.get().setBase(base)
          .apply(revision).value();
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot retrieve files", e);
    }
  }

  @Override
  public FileApi file(String path) {
    return fileApi.create(files.get().parse(revision,
        IdString.fromDecoded(path)));
  }

  @Override
  public Map<String, List<CommentInfo>> comments() throws RestApiException {
    try {
      return listComments.apply(revision);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve comments", e);
    }
  }

  @Override
  public List<CommentInfo> commentsAsList() throws RestApiException {
    try {
      return listComments.getComments(revision);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve comments", e);
    }
  }

  @Override
  public Map<String, List<CommentInfo>> drafts() throws RestApiException {
    try {
      return listDrafts.apply(revision);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve drafts", e);
    }
  }

  @Override
  public List<CommentInfo> draftsAsList() throws RestApiException {
    try {
      return listDrafts.getComments(revision);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve drafts", e);
    }
  }

  @Override
  public DraftApi draft(String id) throws RestApiException {
    try {
      return draftFactory.create(drafts.parse(revision,
          IdString.fromDecoded(id)));
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve draft", e);
    }
  }

  @Override
  public DraftApi createDraft(DraftInput in) throws RestApiException {
    try {
      String id = createDraft.apply(revision, in).value().id;
      // Reread change to pick up new notes refs.
      return changes.id(revision.getChange().getId().get())
          .revision(revision.getPatchSet().getId().get())
          .draft(id);
    } catch (UpdateException | OrmException e) {
      throw new RestApiException("Cannot create draft", e);
    }
  }

  @Override
  public CommentApi comment(String id) throws RestApiException {
    try {
      return commentFactory.create(comments.parse(revision,
          IdString.fromDecoded(id)));
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve comment", e);
    }
  }

  @Override
  public BinaryResult patch() throws RestApiException {
    try {
      return getPatch.get().apply(revision);
    } catch (IOException e) {
      throw new RestApiException("Cannot get patch", e);
    }
  }

  @Override
  public Map<String, ActionInfo> actions() throws RestApiException {
    return revisionActions.apply(revision).value();
  }
}
