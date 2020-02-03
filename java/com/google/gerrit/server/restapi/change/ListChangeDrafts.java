// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.CommentJson.CommentFormatter;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ListChangeDrafts implements RestReadView<ChangeResource> {
  protected final ChangeData.Factory changeDataFactory;
  protected final Provider<CommentJson> commentJson;
  protected final CommentsUtil commentsUtil;

  @Inject
  ListChangeDrafts(
      ChangeData.Factory changeDataFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil) {
    this.changeDataFactory = changeDataFactory;
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
  }

  /** Returns the draft comments from the NoteDb storage */
  protected Iterable<Comment> listComments(ChangeResource rsrc) {
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    return commentsUtil.draftByChangeAuthor(cd.notes(), rsrc.getUser().getAccountId());
  }

  private ImmutableList<CommentInfo> getAsList(Iterable<Comment> comments, ChangeResource rsrc)
      throws PermissionBackendException {
    ImmutableList<CommentInfo> commentInfos = getCommentFormatter().formatAsList(comments);
    postOp(commentInfos, rsrc);
    return commentInfos;
  }

  private Map<String, List<CommentInfo>> getAsMap(Iterable<Comment> comments, ChangeResource rsrc)
      throws PermissionBackendException {
    Map<String, List<CommentInfo>> commentInfos = getCommentFormatter().format(comments);
    postOp(commentInfos.values().stream().flatMap(List::stream).collect(toImmutableList()), rsrc);
    return commentInfos;
  }

  protected boolean includeAuthorInfo() {
    return false;
  }

  public boolean requireAuthentication() {
    return true;
  }

  /**
   * Override this method if you want to apply an operation to commentInfos before returning it to
   * client
   */
  protected void postOp(ImmutableList<CommentInfo> commentInfos, ChangeResource rsrc) {}

  @Override
  public Response<Map<String, List<CommentInfo>>> apply(ChangeResource rsrc)
      throws AuthException, PermissionBackendException {
    if (requireAuthentication() && !rsrc.getUser().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    return Response.ok(getAsMap(listComments(rsrc), rsrc));
  }

  public List<CommentInfo> getComments(ChangeResource rsrc)
      throws AuthException, PermissionBackendException {
    if (requireAuthentication() && !rsrc.getUser().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    return getAsList(listComments(rsrc), rsrc);
  }

  protected CommentFormatter getCommentFormatter() {
    return commentJson
        .get()
        .setFillAccounts(includeAuthorInfo())
        .setFillPatchSet(true)
        .newCommentFormatter();
  }
}
