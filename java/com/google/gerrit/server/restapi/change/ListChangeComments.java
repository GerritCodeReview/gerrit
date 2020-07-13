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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.comment.CommentContextCache;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.kohsuke.args4j.Option;

@Singleton
public class ListChangeComments implements RestReadView<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangeMessagesUtil changeMessagesUtil;
  private final ChangeData.Factory changeDataFactory;
  private final Provider<CommentJson> commentJson;
  private final CommentsUtil commentsUtil;
  private final CommentContextCache contextCache;

  private boolean includeContext;

  /**
   * Optional parameter. If set, the comments of the response will contain context lines of the
   * source code around and including the line/range where the comment was written.
   *
   * @param includeContext If true, comment context will be attached to the response
   */
  @Option(name = "--context-lines")
  public void setContextLines(boolean includeContext) {
    this.includeContext = includeContext;
  }

  @Inject
  ListChangeComments(
      ChangeData.Factory changeDataFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil,
      ChangeMessagesUtil changeMessagesUtil,
      CommentContextCache contextCache) {
    this.changeDataFactory = changeDataFactory;
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
    this.changeMessagesUtil = changeMessagesUtil;
    this.contextCache = contextCache;
  }

  @Override
  public Response<Map<String, List<CommentInfo>>> apply(ChangeResource rsrc)
      throws AuthException, PermissionBackendException {
    return Response.ok(getAsMap(listComments(rsrc), rsrc));
  }

  public List<CommentInfo> getComments(ChangeResource rsrc) throws PermissionBackendException {
    return getAsList(listComments(rsrc), rsrc);
  }

  private Iterable<HumanComment> listComments(ChangeResource rsrc) {
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    return commentsUtil.publishedHumanCommentsByChange(cd.notes());
  }

  private ImmutableList<CommentInfo> getAsList(Iterable<HumanComment> comments, ChangeResource rsrc)
      throws PermissionBackendException {
    ImmutableList<CommentInfo> commentInfos = getCommentFormatter().formatAsList(comments);
    ImmutableMap.Builder<CommentInfo, String> commentPaths = ImmutableMap.builder();
    commentInfos.forEach(c -> commentPaths.put(c, c.path));
    enrichCommentInfos(commentPaths.build(), rsrc);
    return commentInfos;
  }

  private Map<String, List<CommentInfo>> getAsMap(
      Iterable<HumanComment> comments, ChangeResource rsrc) throws PermissionBackendException {
    Map<String, List<CommentInfo>> commentInfosMap = getCommentFormatter().format(comments);
    ImmutableMap.Builder<CommentInfo, String> commentPaths = ImmutableMap.builder();
    commentInfosMap.forEach(
        (String path, List<CommentInfo> cs) -> {
          cs.stream().forEach(c -> commentPaths.put(c, path));
        });
    enrichCommentInfos(commentPaths.build(), rsrc);
    return commentInfosMap;
  }

  /**
   * Append extra fields to the comment infos. This method adds the change message ID and the
   * comment context to all comments.
   *
   * @param commentPaths an {@link ImmutableMap} of commentInfos to their paths
   * @param rsrc the change resource
   */
  private void enrichCommentInfos(
      ImmutableMap<CommentInfo, String> commentPaths, ChangeResource rsrc) {
    List<ChangeMessage> changeMessages = changeMessagesUtil.byChange(rsrc.getNotes());
    List<CommentInfo> commentInfos = new ArrayList<>(commentPaths.keySet());
    CommentsUtil.linkCommentsToChangeMessages(commentInfos, changeMessages, true);
    if (includeContext) {
      for (Map.Entry<CommentInfo, String> entry : commentPaths.entrySet()) {
        CommentInfo commentInfo = entry.getKey();
        Integer startLine = null;
        Integer endLine = null;
        if (commentInfo.range != null) {
          startLine = commentInfo.range.startLine;
          endLine = commentInfo.range.endLine + 1;
        } else if (commentInfo.line != null) {
          startLine = commentInfo.line;
          endLine = commentInfo.line + 1;
        }
        try {
          commentInfo.contextLines =
              contextCache.get(
                  rsrc.getProject(),
                  PatchSet.id(rsrc.getChange().getId(), commentInfo.patchSet),
                  commentInfo.id,
                  commentInfo.path,
                  startLine,
                  endLine);
        } catch (ExecutionException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private CommentJson.HumanCommentFormatter getCommentFormatter() {
    return commentJson.get().setFillAccounts(true).setFillPatchSet(true).newHumanCommentFormatter();
  }
}
