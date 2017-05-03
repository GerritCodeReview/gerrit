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
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RevisionApi {
  void delete() throws RestApiException;

  String description() throws RestApiException;

  void description(String description) throws RestApiException;

  ReviewResult review(ReviewInput in) throws RestApiException;

  void submit() throws RestApiException;

  void submit(SubmitInput in) throws RestApiException;

  BinaryResult submitPreview() throws RestApiException;

  BinaryResult submitPreview(String format) throws RestApiException;

  void publish() throws RestApiException;

  ChangeApi cherryPick(CherryPickInput in) throws RestApiException;

  ChangeApi rebase() throws RestApiException;

  ChangeApi rebase(RebaseInput in) throws RestApiException;

  boolean canRebase() throws RestApiException;

  RevisionReviewerApi reviewer(String id) throws RestApiException;

  void setReviewed(String path, boolean reviewed) throws RestApiException;

  Set<String> reviewed() throws RestApiException;

  Map<String, FileInfo> files() throws RestApiException;

  Map<String, FileInfo> files(String base) throws RestApiException;

  Map<String, FileInfo> files(int parentNum) throws RestApiException;

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

  MergeListRequest getMergeList() throws RestApiException;

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
    public void delete() {
      throw new NotImplementedException();
    }

    @Override
    public ReviewResult review(ReviewInput in) {
      throw new NotImplementedException();
    }

    @Override
    public void submit() {
      throw new NotImplementedException();
    }

    @Override
    public void submit(SubmitInput in) {
      throw new NotImplementedException();
    }

    @Override
    public void publish() {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi cherryPick(CherryPickInput in) {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi rebase() {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi rebase(RebaseInput in) {
      throw new NotImplementedException();
    }

    @Override
    public boolean canRebase() {
      throw new NotImplementedException();
    }

    @Override
    public RevisionReviewerApi reviewer(String id) {
      throw new NotImplementedException();
    }

    @Override
    public void setReviewed(String path, boolean reviewed) {
      throw new NotImplementedException();
    }

    @Override
    public Set<String> reviewed() {
      throw new NotImplementedException();
    }

    @Override
    public MergeableInfo mergeable() {
      throw new NotImplementedException();
    }

    @Override
    public MergeableInfo mergeableOtherBranches() {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, FileInfo> files(String base) {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, FileInfo> files(int parentNum) {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, FileInfo> files() {
      throw new NotImplementedException();
    }

    @Override
    public FileApi file(String path) {
      throw new NotImplementedException();
    }

    @Override
    public CommitInfo commit(boolean addLinks) {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<CommentInfo>> comments() {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<RobotCommentInfo>> robotComments() {
      throw new NotImplementedException();
    }

    @Override
    public List<CommentInfo> commentsAsList() {
      throw new NotImplementedException();
    }

    @Override
    public List<CommentInfo> draftsAsList() {
      throw new NotImplementedException();
    }

    @Override
    public List<RobotCommentInfo> robotCommentsAsList() {
      throw new NotImplementedException();
    }

    @Override
    public EditInfo applyFix(String fixId) {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, List<CommentInfo>> drafts() {
      throw new NotImplementedException();
    }

    @Override
    public DraftApi createDraft(DraftInput in) {
      throw new NotImplementedException();
    }

    @Override
    public DraftApi draft(String id) {
      throw new NotImplementedException();
    }

    @Override
    public CommentApi comment(String id) {
      throw new NotImplementedException();
    }

    @Override
    public RobotCommentApi robotComment(String id) {
      throw new NotImplementedException();
    }

    @Override
    public BinaryResult patch() {
      throw new NotImplementedException();
    }

    @Override
    public BinaryResult patch(String path) {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, ActionInfo> actions() {
      throw new NotImplementedException();
    }

    @Override
    public SubmitType submitType() {
      throw new NotImplementedException();
    }

    @Override
    public BinaryResult submitPreview() {
      throw new NotImplementedException();
    }

    @Override
    public BinaryResult submitPreview(String format) {
      throw new NotImplementedException();
    }

    @Override
    public SubmitType testSubmitType(TestSubmitRuleInput in) {
      throw new NotImplementedException();
    }

    @Override
    public MergeListRequest getMergeList() {
      throw new NotImplementedException();
    }

    @Override
    public void description(String description) {
      throw new NotImplementedException();
    }

    @Override
    public String description() {
      throw new NotImplementedException();
    }

    @Override
    public String etag() {
      throw new NotImplementedException();
    }
  }
}
