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

  void review(ReviewInput in) throws RestApiException;

  void submit() throws RestApiException;

  void submit(SubmitInput in) throws RestApiException;

  BinaryResult submitPreview() throws RestApiException;

  void publish() throws RestApiException;

  ChangeApi cherryPick(CherryPickInput in) throws RestApiException;

  ChangeApi rebase() throws RestApiException;

  ChangeApi rebase(RebaseInput in) throws RestApiException;

  boolean canRebase() throws RestApiException;

  void setReviewed(String path, boolean reviewed) throws RestApiException;

  Set<String> reviewed() throws RestApiException;

  Map<String, FileInfo> files() throws RestApiException;

  Map<String, FileInfo> files(String base) throws RestApiException;

  Map<String, FileInfo> files(int parentNum) throws RestApiException;

  FileApi file(String path);

  MergeableInfo mergeable() throws RestApiException;

  MergeableInfo mergeableOtherBranches() throws RestApiException;

  Map<String, List<CommentInfo>> comments() throws RestApiException;

  Map<String, List<RobotCommentInfo>> robotComments() throws RestApiException;

  Map<String, List<CommentInfo>> drafts() throws RestApiException;

  List<CommentInfo> commentsAsList() throws RestApiException;

  List<CommentInfo> draftsAsList() throws RestApiException;

  List<RobotCommentInfo> robotCommentsAsList() throws RestApiException;

  DraftApi createDraft(DraftInput in) throws RestApiException;

  DraftApi draft(String id) throws RestApiException;

  CommentApi comment(String id) throws RestApiException;

  RobotCommentApi robotComment(String id) throws RestApiException;

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
    public void delete() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void review(ReviewInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void submit() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void submit(SubmitInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void publish() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi cherryPick(CherryPickInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi rebase() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi rebase(RebaseInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public boolean canRebase() {
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
    public Map<String, FileInfo> files() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public FileApi file(String path) {
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
    public BinaryResult submitPreview() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public SubmitType testSubmitType(TestSubmitRuleInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public MergeListRequest getMergeList() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
