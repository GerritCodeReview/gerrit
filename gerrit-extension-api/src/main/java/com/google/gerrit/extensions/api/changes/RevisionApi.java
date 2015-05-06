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

import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RevisionApi {
  void delete() throws RestApiException;
  void review(ReviewInput in) throws RestApiException;

  /** {@code submit} with {@link SubmitInput#waitForMerge} set to true. */
  void submit() throws RestApiException;
  void submit(SubmitInput in) throws RestApiException;
  void publish() throws RestApiException;
  ChangeApi cherryPick(CherryPickInput in) throws RestApiException;
  ChangeApi rebase() throws RestApiException;
  ChangeApi rebase(RebaseInput in) throws RestApiException;
  boolean canRebase();

  void setReviewed(String path, boolean reviewed) throws RestApiException;
  Set<String> reviewed() throws RestApiException;

  Map<String, FileInfo> files() throws RestApiException;
  Map<String, FileInfo> files(String base) throws RestApiException;
  FileApi file(String path);
  MergeableInfo mergeable() throws RestApiException;
  MergeableInfo mergeableOtherBranches() throws RestApiException;

  Map<String, List<CommentInfo>> comments() throws RestApiException;
  Map<String, List<CommentInfo>> drafts() throws RestApiException;

  List<CommentInfo> commentsAsList() throws RestApiException;
  List<CommentInfo> draftsAsList() throws RestApiException;

  DraftApi createDraft(DraftInput in) throws RestApiException;
  DraftApi draft(String id) throws RestApiException;

  CommentApi comment(String id) throws RestApiException;

  Map<String, ActionInfo> actions() throws RestApiException;

  /**
   * A default implementation which allows source compatibility
   * when adding new methods to the interface.
   **/
  public class NotImplemented implements RevisionApi {
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
    public List<CommentInfo> commentsAsList() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<CommentInfo> draftsAsList() throws RestApiException {
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
    public Map<String, ActionInfo> actions() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
