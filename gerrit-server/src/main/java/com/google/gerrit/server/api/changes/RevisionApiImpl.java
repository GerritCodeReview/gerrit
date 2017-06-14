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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.CommentApi;
import com.google.gerrit.extensions.api.changes.DraftApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.FileApi;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.RevisionReviewerApi;
import com.google.gerrit.extensions.api.changes.RobotCommentApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.common.TestSubmitRuleInput;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ApplyFix;
import com.google.gerrit.server.change.CherryPick;
import com.google.gerrit.server.change.Comments;
import com.google.gerrit.server.change.CreateDraftComment;
import com.google.gerrit.server.change.DeleteDraftPatchSet;
import com.google.gerrit.server.change.DraftComments;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.Files;
import com.google.gerrit.server.change.Fixes;
import com.google.gerrit.server.change.GetCommit;
import com.google.gerrit.server.change.GetDescription;
import com.google.gerrit.server.change.GetMergeList;
import com.google.gerrit.server.change.GetPatch;
import com.google.gerrit.server.change.GetRevisionActions;
import com.google.gerrit.server.change.ListRevisionComments;
import com.google.gerrit.server.change.ListRevisionDrafts;
import com.google.gerrit.server.change.ListRobotComments;
import com.google.gerrit.server.change.Mergeable;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.PreviewSubmit;
import com.google.gerrit.server.change.PublishDraftPatchSet;
import com.google.gerrit.server.change.PutDescription;
import com.google.gerrit.server.change.PutMessage;
import com.google.gerrit.server.change.Rebase;
import com.google.gerrit.server.change.RebaseUtil;
import com.google.gerrit.server.change.Reviewed;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.RevisionReviewers;
import com.google.gerrit.server.change.RobotComments;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.change.TestSubmitType;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

class RevisionApiImpl implements RevisionApi {
  interface Factory {
    RevisionApiImpl create(RevisionResource r);
  }

  private final GitRepositoryManager repoManager;
  private final Changes changes;
  private final RevisionReviewers revisionReviewers;
  private final RevisionReviewerApiImpl.Factory revisionReviewerApi;
  private final CherryPick cherryPick;
  private final DeleteDraftPatchSet deleteDraft;
  private final Rebase rebase;
  private final RebaseUtil rebaseUtil;
  private final Submit submit;
  private final PreviewSubmit submitPreview;
  private final PublishDraftPatchSet publish;
  private final Reviewed.PutReviewed putReviewed;
  private final Reviewed.DeleteReviewed deleteReviewed;
  private final RevisionResource revision;
  private final Files files;
  private final Files.ListFiles listFiles;
  private final GetCommit getCommit;
  private final GetPatch getPatch;
  private final PostReview review;
  private final Mergeable mergeable;
  private final FileApiImpl.Factory fileApi;
  private final ListRevisionComments listComments;
  private final ListRobotComments listRobotComments;
  private final ApplyFix applyFix;
  private final Fixes fixes;
  private final ListRevisionDrafts listDrafts;
  private final CreateDraftComment createDraft;
  private final DraftComments drafts;
  private final DraftApiImpl.Factory draftFactory;
  private final Comments comments;
  private final CommentApiImpl.Factory commentFactory;
  private final RobotComments robotComments;
  private final RobotCommentApiImpl.Factory robotCommentFactory;
  private final GetRevisionActions revisionActions;
  private final TestSubmitType testSubmitType;
  private final TestSubmitType.Get getSubmitType;
  private final Provider<GetMergeList> getMergeList;
  private final PutDescription putDescription;
  private final GetDescription getDescription;
  private final PutMessage putMessage;

  @Inject
  RevisionApiImpl(
      GitRepositoryManager repoManager,
      Changes changes,
      RevisionReviewers revisionReviewers,
      RevisionReviewerApiImpl.Factory revisionReviewerApi,
      CherryPick cherryPick,
      DeleteDraftPatchSet deleteDraft,
      Rebase rebase,
      RebaseUtil rebaseUtil,
      Submit submit,
      PreviewSubmit submitPreview,
      PublishDraftPatchSet publish,
      Reviewed.PutReviewed putReviewed,
      Reviewed.DeleteReviewed deleteReviewed,
      Files files,
      Files.ListFiles listFiles,
      GetCommit getCommit,
      GetPatch getPatch,
      PostReview review,
      Mergeable mergeable,
      FileApiImpl.Factory fileApi,
      ListRevisionComments listComments,
      ListRobotComments listRobotComments,
      ApplyFix applyFix,
      Fixes fixes,
      ListRevisionDrafts listDrafts,
      CreateDraftComment createDraft,
      DraftComments drafts,
      DraftApiImpl.Factory draftFactory,
      Comments comments,
      CommentApiImpl.Factory commentFactory,
      RobotComments robotComments,
      RobotCommentApiImpl.Factory robotCommentFactory,
      GetRevisionActions revisionActions,
      TestSubmitType testSubmitType,
      TestSubmitType.Get getSubmitType,
      Provider<GetMergeList> getMergeList,
      PutDescription putDescription,
      GetDescription getDescription,
      PutMessage putMessage,
      @Assisted RevisionResource r) {
    this.repoManager = repoManager;
    this.changes = changes;
    this.revisionReviewers = revisionReviewers;
    this.revisionReviewerApi = revisionReviewerApi;
    this.cherryPick = cherryPick;
    this.deleteDraft = deleteDraft;
    this.rebase = rebase;
    this.rebaseUtil = rebaseUtil;
    this.review = review;
    this.submit = submit;
    this.submitPreview = submitPreview;
    this.publish = publish;
    this.files = files;
    this.putReviewed = putReviewed;
    this.deleteReviewed = deleteReviewed;
    this.listFiles = listFiles;
    this.getCommit = getCommit;
    this.getPatch = getPatch;
    this.mergeable = mergeable;
    this.fileApi = fileApi;
    this.listComments = listComments;
    this.robotComments = robotComments;
    this.listRobotComments = listRobotComments;
    this.applyFix = applyFix;
    this.fixes = fixes;
    this.listDrafts = listDrafts;
    this.createDraft = createDraft;
    this.drafts = drafts;
    this.draftFactory = draftFactory;
    this.comments = comments;
    this.commentFactory = commentFactory;
    this.robotCommentFactory = robotCommentFactory;
    this.revisionActions = revisionActions;
    this.testSubmitType = testSubmitType;
    this.getSubmitType = getSubmitType;
    this.getMergeList = getMergeList;
    this.putDescription = putDescription;
    this.getDescription = getDescription;
    this.putMessage = putMessage;
    this.revision = r;
  }

  @Override
  public ReviewResult review(ReviewInput in) throws RestApiException {
    try {
      return review.apply(revision, in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot post review", e);
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
    } catch (Exception e) {
      throw asRestApiException("Cannot submit change", e);
    }
  }

  @Override
  public BinaryResult submitPreview() throws RestApiException {
    return submitPreview("zip");
  }

  @Override
  public BinaryResult submitPreview(String format) throws RestApiException {
    try {
      submitPreview.setFormat(format);
      return submitPreview.apply(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot get submit preview", e);
    }
  }

  @Override
  public void publish() throws RestApiException {
    try {
      publish.apply(revision, new PublishDraftPatchSet.Input());
    } catch (Exception e) {
      throw asRestApiException("Cannot publish draft patch set", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    try {
      deleteDraft.apply(revision, null);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete draft ps", e);
    }
  }

  @Override
  public void message(String in) throws RestApiException {
    try {
      PutMessage.Input input = new PutMessage.Input();
      input.message = in;
      putMessage.apply(revision, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot edit commit message", e);
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
    } catch (Exception e) {
      throw asRestApiException("Cannot rebase ps", e);
    }
  }

  @Override
  public boolean canRebase() throws RestApiException {
    try (Repository repo = repoManager.openRepository(revision.getProject());
        RevWalk rw = new RevWalk(repo)) {
      return rebaseUtil.canRebase(revision.getPatchSet(), revision.getChange().getDest(), repo, rw);
    } catch (Exception e) {
      throw asRestApiException("Cannot check if rebase is possible", e);
    }
  }

  @Override
  public ChangeApi cherryPick(CherryPickInput in) throws RestApiException {
    try {
      return changes.id(cherryPick.apply(revision, in)._number);
    } catch (Exception e) {
      throw asRestApiException("Cannot cherry pick", e);
    }
  }

  @Override
  public RevisionReviewerApi reviewer(String id) throws RestApiException {
    try {
      return revisionReviewerApi.create(
          revisionReviewers.parse(revision, IdString.fromDecoded(id)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse reviewer", e);
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
      view.apply(files.parse(revision, IdString.fromDecoded(path)), new Reviewed.Input());
    } catch (Exception e) {
      throw asRestApiException("Cannot update reviewed flag", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Set<String> reviewed() throws RestApiException {
    try {
      return ImmutableSet.copyOf(
          (Iterable<String>) listFiles.setReviewed(true).apply(revision).value());
    } catch (Exception e) {
      throw asRestApiException("Cannot list reviewed files", e);
    }
  }

  @Override
  public MergeableInfo mergeable() throws RestApiException {
    try {
      return mergeable.apply(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot check mergeability", e);
    }
  }

  @Override
  public MergeableInfo mergeableOtherBranches() throws RestApiException {
    try {
      mergeable.setOtherBranches(true);
      return mergeable.apply(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot check mergeability", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, FileInfo> files() throws RestApiException {
    try {
      return (Map<String, FileInfo>) listFiles.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve files", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, FileInfo> files(String base) throws RestApiException {
    try {
      return (Map<String, FileInfo>) listFiles.setBase(base).apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve files", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, FileInfo> files(int parentNum) throws RestApiException {
    try {
      return (Map<String, FileInfo>) listFiles.setParent(parentNum).apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve files", e);
    }
  }

  @Override
  public FileApi file(String path) {
    return fileApi.create(files.parse(revision, IdString.fromDecoded(path)));
  }

  @Override
  public CommitInfo commit(boolean addLinks) throws RestApiException {
    try {
      return getCommit.setAddLinks(addLinks).apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve commit", e);
    }
  }

  @Override
  public Map<String, List<CommentInfo>> comments() throws RestApiException {
    try {
      return listComments.apply(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve comments", e);
    }
  }

  @Override
  public Map<String, List<RobotCommentInfo>> robotComments() throws RestApiException {
    try {
      return listRobotComments.apply(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve robot comments", e);
    }
  }

  @Override
  public List<CommentInfo> commentsAsList() throws RestApiException {
    try {
      return listComments.getComments(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve comments", e);
    }
  }

  @Override
  public Map<String, List<CommentInfo>> drafts() throws RestApiException {
    try {
      return listDrafts.apply(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve drafts", e);
    }
  }

  @Override
  public List<RobotCommentInfo> robotCommentsAsList() throws RestApiException {
    try {
      return listRobotComments.getComments(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve robot comments", e);
    }
  }

  @Override
  public EditInfo applyFix(String fixId) throws RestApiException {
    try {
      return applyFix.apply(fixes.parse(revision, IdString.fromDecoded(fixId)), null).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot apply fix", e);
    }
  }

  @Override
  public List<CommentInfo> draftsAsList() throws RestApiException {
    try {
      return listDrafts.getComments(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve drafts", e);
    }
  }

  @Override
  public DraftApi draft(String id) throws RestApiException {
    try {
      return draftFactory.create(drafts.parse(revision, IdString.fromDecoded(id)));
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve draft", e);
    }
  }

  @Override
  public DraftApi createDraft(DraftInput in) throws RestApiException {
    try {
      String id = createDraft.apply(revision, in).value().id;
      // Reread change to pick up new notes refs.
      return changes
          .id(revision.getChange().getId().get())
          .revision(revision.getPatchSet().getId().get())
          .draft(id);
    } catch (Exception e) {
      throw asRestApiException("Cannot create draft", e);
    }
  }

  @Override
  public CommentApi comment(String id) throws RestApiException {
    try {
      return commentFactory.create(comments.parse(revision, IdString.fromDecoded(id)));
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve comment", e);
    }
  }

  @Override
  public RobotCommentApi robotComment(String id) throws RestApiException {
    try {
      return robotCommentFactory.create(robotComments.parse(revision, IdString.fromDecoded(id)));
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve robot comment", e);
    }
  }

  @Override
  public BinaryResult patch() throws RestApiException {
    try {
      return getPatch.apply(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot get patch", e);
    }
  }

  @Override
  public BinaryResult patch(String path) throws RestApiException {
    try {
      return getPatch.setPath(path).apply(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot get patch", e);
    }
  }

  @Override
  public Map<String, ActionInfo> actions() throws RestApiException {
    try {
      return revisionActions.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get actions", e);
    }
  }

  @Override
  public SubmitType submitType() throws RestApiException {
    try {
      return getSubmitType.apply(revision);
    } catch (Exception e) {
      throw asRestApiException("Cannot get submit type", e);
    }
  }

  @Override
  public SubmitType testSubmitType(TestSubmitRuleInput in) throws RestApiException {
    try {
      return testSubmitType.apply(revision, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot test submit type", e);
    }
  }

  @Override
  public MergeListRequest getMergeList() throws RestApiException {
    return new MergeListRequest() {
      @Override
      public List<CommitInfo> get() throws RestApiException {
        try {
          GetMergeList gml = getMergeList.get();
          gml.setUninterestingParent(getUninterestingParent());
          gml.setAddLinks(getAddLinks());
          return gml.apply(revision).value();
        } catch (Exception e) {
          throw asRestApiException("Cannot get merge list", e);
        }
      }
    };
  }

  @Override
  public void description(String description) throws RestApiException {
    PutDescription.Input in = new PutDescription.Input();
    in.description = description;
    try {
      putDescription.apply(revision, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot set description", e);
    }
  }

  @Override
  public String description() throws RestApiException {
    return getDescription.apply(revision);
  }

  @Override
  public String etag() throws RestApiException {
    return revisionActions.getETag(revision);
  }
}
