// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ListRevisionDrafts implements RestReadView<RevisionResource> {
  protected final Provider<CommentJson> commentJson;
  protected final CommentsUtil commentsUtil;

  @Inject
  ListRevisionDrafts(Provider<CommentJson> commentJson, CommentsUtil commentsUtil) {
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
  }

  protected Iterable<Comment> listComments(RevisionResource rsrc) throws StorageException {
    return commentsUtil.draftByPatchSetAuthor(
        rsrc.getPatchSet().getId(), rsrc.getAccountId(), rsrc.getNotes());
  }

  protected boolean includeAuthorInfo() {
    return false;
  }

  @Override
  public Map<String, List<CommentInfo>> apply(RevisionResource rsrc)
      throws StorageException, PermissionBackendException {
    return commentJson
        .get()
        .setFillAccounts(includeAuthorInfo())
        .newCommentFormatter()
        .format(listComments(rsrc));
  }

  public ImmutableList<CommentInfo> getComments(RevisionResource rsrc)
      throws StorageException, PermissionBackendException {
    return commentJson
        .get()
        .setFillAccounts(includeAuthorInfo())
        .newCommentFormatter()
        .formatAsList(listComments(rsrc));
  }
}
