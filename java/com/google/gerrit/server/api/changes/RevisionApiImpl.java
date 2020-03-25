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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder.ListMultimapBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.CommentApi;
import com.google.gerrit.extensions.api.changes.DraftApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.FileApi;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.RelatedChangesInfo;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.RevisionReviewerApi;
import com.google.gerrit.extensions.api.changes.RobotCommentApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.ArchiveFormat;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.DescriptionInput;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.common.TestSubmitRuleInfo;
import com.google.gerrit.extensions.common.TestSubmitRuleInput;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.RebaseUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.restapi.change.ApplyFix;
import com.google.gerrit.server.restapi.change.CherryPick;
import com.google.gerrit.server.restapi.change.Comments;
import com.google.gerrit.server.restapi.change.CreateDraftComment;
import com.google.gerrit.server.restapi.change.DraftComments;
import com.google.gerrit.server.restapi.change.Files;
import com.google.gerrit.server.restapi.change.Fixes;
import com.google.gerrit.server.restapi.change.GetArchive;
import com.google.gerrit.server.restapi.change.GetCommit;
import com.google.gerrit.server.restapi.change.GetDescription;
import com.google.gerrit.server.restapi.change.GetFixPreview;
import com.google.gerrit.server.restapi.change.GetMergeList;
import com.google.gerrit.server.restapi.change.GetPatch;
import com.google.gerrit.server.restapi.change.GetRelated;
import com.google.gerrit.server.restapi.change.GetRevisionActions;
import com.google.gerrit.server.restapi.change.ListRevisionComments;
import com.google.gerrit.server.restapi.change.ListRevisionDrafts;
import com.google.gerrit.server.restapi.change.ListRobotComments;
import com.google.gerrit.server.restapi.change.Mergeable;
import com.google.gerrit.server.restapi.change.PostReview;
import com.google.gerrit.server.restapi.change.PreviewSubmit;
import com.google.gerrit.server.restapi.change.PutDescription;
import com.google.gerrit.server.restapi.change.Rebase;
import com.google.gerrit.server.restapi.change.Reviewed;
import com.google.gerrit.server.restapi.change.RevisionReviewers;
import com.google.gerrit.server.restapi.change.RobotComments;
import com.google.gerrit.server.restapi.change.Submit;
import com.google.gerrit.server.restapi.change.TestSubmitRule;
import com.google.gerrit.server.restapi.change.TestSubmitType;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
  private final Rebase rebase;
  private final RebaseUtil rebaseUtil;
  private final Submit submit;
  private final PreviewSubmit submitPreview;
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
  private final GetFixPreview getFixPreview;
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
  private final Provider<TestSubmitRule> testSubmitRule;
  private final Provider<GetMergeList> getMergeList;
  private final GetRelated getRelated;
  private final PutDescription putDescription;
  private final GetDescription getDescription;
  private final Provider<GetArchive> getArchiveProvider;
  private final ApprovalsUtil approvalsUtil;
  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  RevisionApiImpl(
      GitRepositoryManager repoManager,
      Changes changes,
      RevisionReviewers revisionReviewers,
      RevisionReviewerApiImpl.Factory revisionReviewerApi,
      CherryPick cherryPick,
      Rebase rebase,
      RebaseUtil rebaseUtil,
      Submit submit,
      PreviewSubmit submitPreview,
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
      GetFixPreview getFixPreview,
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
      Provider<TestSubmitRule> testSubmitRule,
      Provider<GetMergeList> getMergeList,
      GetRelated getRelated,
      PutDescription putDescription,
      GetDescription getDescription,
      Provider<GetArchive> getArchiveProvider,
      ApprovalsUtil approvalsUtil,
      AccountLoader.Factory accountLoaderFactory,
      @Assisted RevisionResource r) {
    this.repoManager = repoManager;
    this.changes = changes;
    this.revisionReviewers = revisionReviewers;
    this.revisionReviewerApi = revisionReviewerApi;
    this.cherryPick = cherryPick;
    this.rebase = rebase;
    this.rebaseUtil = rebaseUtil;
    this.review = review;
    this.submit = submit;
    this.submitPreview = submitPreview;
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
    this.getFixPreview = getFixPreview;
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
    this.testSubmitRule = testSubmitRule;
    this.getMergeList = getMergeList;
    this.getRelated = getRelated;
    this.putDescription = putDescription;
    this.getDescription = getDescription;
    this.getArchiveProvider = getArchiveProvider;
    this.approvalsUtil = approvalsUtil;
    this.accountLoaderFactory = accountLoaderFactory;
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
  public void submit(SubmitInput in) throws RestApiException {
    try {
      submit.apply(revision, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot submit change", e);
    }
  }

  @Override
  public BinaryResult submitPreview(String format) throws RestApiException {
    try {
      submitPreview.setFormat(format);
      return submitPreview.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get submit preview", e);
    }
  }

  @Override
  public ChangeApi rebase(RebaseInput in) throws RestApiException {
    try {
      return changes.id(rebase.apply(revision, in).value()._number);
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
      return changes.id(cherryPick.apply(revision, in).value()._number);
    } catch (Exception e) {
      throw asRestApiException("Cannot cherry pick", e);
    }
  }

  @Override
  public ChangeInfo cherryPickAsInfo(CherryPickInput in) throws RestApiException {
    try {
      return cherryPick.apply(revision, in).value();
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
      RestModifyView<FileResource, Input> view;
      if (reviewed) {
        view = putReviewed;
      } else {
        view = deleteReviewed;
      }
      view.apply(files.parse(revision, IdString.fromDecoded(path)), new Input());
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
      return mergeable.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot check mergeability", e);
    }
  }

  @Override
  public MergeableInfo mergeableOtherBranches() throws RestApiException {
    try {
      mergeable.setOtherBranches(true);
      return mergeable.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot check mergeability", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, FileInfo> files(@Nullable String base) throws RestApiException {
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

  @SuppressWarnings("unchecked")
  @Override
  public List<String> queryFiles(String query) throws RestApiException {
    try {
      checkArgument(query != null, "no query provided");
      return (List<String>) listFiles.setQuery(query).apply(revision).value();
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
      return listComments.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve comments", e);
    }
  }

  @Override
  public Map<String, List<RobotCommentInfo>> robotComments() throws RestApiException {
    try {
      return listRobotComments.apply(revision).value();
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
      return listDrafts.apply(revision).value();
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
  public Map<String, DiffInfo> getFixPreview(String fixId) throws RestApiException {
    try {
      return getFixPreview.apply(fixes.parse(revision, IdString.fromDecoded(fixId))).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get fix preview", e);
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
          .revision(revision.getPatchSet().id().get())
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
      return getPatch.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get patch", e);
    }
  }

  @Override
  public BinaryResult patch(String path) throws RestApiException {
    try {
      return getPatch.setPath(path).apply(revision).value();
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
      return getSubmitType.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get submit type", e);
    }
  }

  @Override
  public SubmitType testSubmitType(TestSubmitRuleInput in) throws RestApiException {
    try {
      return testSubmitType.apply(revision, in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot test submit type", e);
    }
  }

  @Override
  public TestSubmitRuleInfo testSubmitRule(TestSubmitRuleInput in) throws RestApiException {
    try {
      return testSubmitRule.get().apply(revision, in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot test submit rule", e);
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
  public RelatedChangesInfo related() throws RestApiException {
    try {
      return getRelated.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get related changes", e);
    }
  }

  @Override
  public ListMultimap<String, ApprovalInfo> votes() throws RestApiException {
    ListMultimap<String, ApprovalInfo> result =
        ListMultimapBuilder.treeKeys().arrayListValues().build();
    try {
      Iterable<PatchSetApproval> approvals =
          approvalsUtil.byPatchSet(revision.getNotes(), revision.getPatchSet().id(), null, null);
      AccountLoader accountLoader =
          accountLoaderFactory.create(
              EnumSet.of(
                  FillOptions.ID, FillOptions.NAME, FillOptions.EMAIL, FillOptions.USERNAME));
      for (PatchSetApproval approval : approvals) {
        String label = approval.label();
        ApprovalInfo info =
            new ApprovalInfo(
                approval.accountId().get(),
                Integer.valueOf(approval.value()),
                null,
                approval.tag().orElse(null),
                approval.granted());
        accountLoader.put(info);
        result.get(label).add(info);
      }
      accountLoader.fill();
    } catch (Exception e) {
      throw asRestApiException("Cannot get votes", e);
    }
    return result;
  }

  @Override
  public void description(String description) throws RestApiException {
    DescriptionInput in = new DescriptionInput();
    in.description = description;
    try {
      putDescription.apply(revision, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot set description", e);
    }
  }

  @Override
  public String description() throws RestApiException {
    try {
      return getDescription.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get description", e);
    }
  }

  @Override
  public String etag() throws RestApiException {
    return revisionActions.getETag(revision);
  }

  @Override
  public BinaryResult getArchive(ArchiveFormat format) throws RestApiException {
    GetArchive getArchive = getArchiveProvider.get();
    getArchive.setFormat(format.name().toLowerCase(Locale.US));
    try {
      return getArchive.apply(revision).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get archive", e);
    }
  }
}
