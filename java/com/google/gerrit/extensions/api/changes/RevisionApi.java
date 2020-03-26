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

package com.google.gerrit.extensions.api.changes;

import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.ArchiveFormat;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.common.TestSubmitRuleInfo;
import com.google.gerrit.extensions.common.TestSubmitRuleInput;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RevisionApi {
  @Deprecated
  default void delete() {
    throw new UnsupportedOperationException("draft workflow is discontinued");
  }

  String description() throws RestApiException;

  void description(String description) throws RestApiException;

  ReviewResult review(ReviewInput in) throws RestApiException;

  default void submit() throws RestApiException {
    SubmitInput in = new SubmitInput();
    submit(in);
  }

  void submit(SubmitInput in) throws RestApiException;

  default BinaryResult submitPreview() throws RestApiException {
    return submitPreview("zip");
  }

  BinaryResult submitPreview(String format) throws RestApiException;

  @Deprecated
  default void publish() {
    throw new UnsupportedOperationException("draft workflow is discontinued");
  }

  ChangeApi cherryPick(CherryPickInput in) throws RestApiException;

  ChangeInfo cherryPickAsInfo(CherryPickInput in) throws RestApiException;

  default ChangeApi rebase() throws RestApiException {
    RebaseInput in = new RebaseInput();
    return rebase(in);
  }

  ChangeApi rebase(RebaseInput in) throws RestApiException;

  boolean canRebase() throws RestApiException;

  RevisionReviewerApi reviewer(String id) throws RestApiException;

  void setReviewed(String path, boolean reviewed) throws RestApiException;

  Set<String> reviewed() throws RestApiException;

  default Map<String, FileInfo> files() throws RestApiException {
    return files(null);
  }

  Map<String, FileInfo> files(@Nullable String base) throws RestApiException;

  Map<String, FileInfo> files(int parentNum) throws RestApiException;

  List<String> queryFiles(String query) throws RestApiException;

  FileApi file(String path);

  CommitInfo commit(boolean addLinks) throws RestApiException;

  MergeableInfo mergeable() throws RestApiException;

  MergeableInfo mergeableOtherBranches() throws RestApiException;

  Map<String, List<CommentInfo>> comments() throws RestApiException;

  Map<String, List<RobotCommentInfo>> robotComments() throws RestApiException;

  Map<String, List<CommentInfo>> drafts() throws RestApiException;

  List<CommentInfo> commentsAsList() throws RestApiException;

  List<CommentInfo> draftsAsList() throws RestApiException;

  List<RobotCommentInfo> robotCommentsAsList() throws RestApiException;

  /**
   * Applies the indicated fix by creating a new change edit or integrating the fix with the
   * existing change edit. If no change edit exists before this call, the fix must refer to the
   * current patch set. If a change edit exists, the fix must refer to the patch set on which the
   * change edit is based.
   *
   * @param fixId the ID of the fix which should be applied
   * @throws RestApiException if the fix couldn't be applied
   */
  EditInfo applyFix(String fixId) throws RestApiException;

  Map<String, DiffInfo> getFixPreview(String fixId) throws RestApiException;

  DraftApi createDraft(DraftInput in) throws RestApiException;

  DraftApi draft(String id) throws RestApiException;

  CommentApi comment(String id) throws RestApiException;

  RobotCommentApi robotComment(String id) throws RestApiException;

  String etag() throws RestApiException;

  /** Returns patch of revision. */
  BinaryResult patch() throws RestApiException;

  BinaryResult patch(String path) throws RestApiException;

  Map<String, ActionInfo> actions() throws RestApiException;

  SubmitType submitType() throws RestApiException;

  SubmitType testSubmitType(TestSubmitRuleInput in) throws RestApiException;

  TestSubmitRuleInfo testSubmitRule(TestSubmitRuleInput in) throws RestApiException;

  MergeListRequest getMergeList() throws RestApiException;

  RelatedChangesInfo related() throws RestApiException;

  /** Returns votes on the revision. */
  ListMultimap<String, ApprovalInfo> votes() throws RestApiException;

  /**
   * Retrieves the revision as an archive.
   *
   * @param format the format of the archive
   * @return the archive as {@link BinaryResult}
   * @throws RestApiException
   */
  BinaryResult getArchive(ArchiveFormat format) throws RestApiException;

  abstract class MergeListRequest {
    private boolean addLinks;
    private int uninterestingParent = 1;

    public abstract List<CommitInfo> get() throws RestApiException;

    public MergeListRequest withLinks() {
      this.addLinks = true;
      return this;
    }

    public MergeListRequest withUninterestingParent(int uninterestingParent) {
      this.uninterestingParent = uninterestingParent;
      return this;
    }

    public boolean getAddLinks() {
      return addLinks;
    }

    public int getUninterestingParent() {
      return uninterestingParent;
    }
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements RevisionApi {
    @Override
    public ReviewResult review(ReviewInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void submit(SubmitInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi cherryPick(CherryPickInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo cherryPickAsInfo(CherryPickInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi rebase(RebaseInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public boolean canRebase() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public RevisionReviewerApi reviewer(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void setReviewed(String path, boolean reviewed) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Set<String> reviewed() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public MergeableInfo mergeable() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public MergeableInfo mergeableOtherBranches() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, FileInfo> files(String base) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, FileInfo> files(int parentNum) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<String> queryFiles(String query) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public FileApi file(String path) {
      throw new NotImplementedException();
    }

    @Override
    public CommitInfo commit(boolean addLinks) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<CommentInfo>> comments() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<RobotCommentInfo>> robotComments() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<CommentInfo> commentsAsList() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<CommentInfo> draftsAsList() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<RobotCommentInfo> robotCommentsAsList() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public EditInfo applyFix(String fixId) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, DiffInfo> getFixPreview(String fixId) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<CommentInfo>> drafts() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public DraftApi createDraft(DraftInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public DraftApi draft(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CommentApi comment(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public RobotCommentApi robotComment(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public BinaryResult patch() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public BinaryResult patch(String path) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, ActionInfo> actions() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public SubmitType submitType() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public BinaryResult submitPreview(String format) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public SubmitType testSubmitType(TestSubmitRuleInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public TestSubmitRuleInfo testSubmitRule(TestSubmitRuleInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public MergeListRequest getMergeList() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public RelatedChangesInfo related() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ListMultimap<String, ApprovalInfo> votes() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void description(String description) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public String description() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public String etag() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public BinaryResult getArchive(ArchiveFormat format) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
