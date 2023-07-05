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

import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.ContextLineInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.DraftCommentsReader;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.CommentJson.HumanCommentFormatter;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import java.util.Map;
import org.kohsuke.args4j.Option;

public class ListChangeDrafts implements RestReadView<ChangeResource> {
  private final ChangeData.Factory changeDataFactory;
  private final Provider<CommentJson> commentJson;
  private final DraftCommentsReader draftCommentsReader;

  private boolean includeContext;
  private int contextPadding;

  /**
   * Optional parameter. If set, the contextLines field of the {@link ContextLineInfo} of the
   * response will contain the lines of the source file where the comment was written.
   *
   * @param context If true, comment context will be attached to the response
   */
  @Option(name = "--enable-context")
  public void setContext(boolean context) {
    this.includeContext = context;
  }

  /**
   * Optional parameter. Works only if {@link #includeContext} is set to true. If {@link
   * #contextPadding} is set, the context lines in the response will be padded with {@link
   * #contextPadding} extra lines before and after the comment range.
   */
  @Option(name = "--context-padding")
  public void setContextPadding(int contextPadding) {
    this.contextPadding = contextPadding;
  }

  @Inject
  ListChangeDrafts(
      ChangeData.Factory changeDataFactory,
      Provider<CommentJson> commentJson,
      DraftCommentsReader draftCommentsReader) {
    this.changeDataFactory = changeDataFactory;
    this.commentJson = commentJson;
    this.draftCommentsReader = draftCommentsReader;
  }

  private Iterable<HumanComment> listComments(ChangeResource rsrc) {
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    return draftCommentsReader.getDraftsByChangeAndDraftAuthor(
        cd.notes(), rsrc.getUser().getAccountId());
  }

  @Override
  public Response<Map<String, List<CommentInfo>>> apply(ChangeResource rsrc)
      throws AuthException, PermissionBackendException {
    if (!rsrc.getUser().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    return Response.ok(getCommentFormatter(rsrc).format(listComments(rsrc)));
  }

  public List<CommentInfo> getComments(ChangeResource rsrc)
      throws AuthException, PermissionBackendException {
    if (!rsrc.getUser().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    return getCommentFormatter(rsrc).formatAsList(listComments(rsrc));
  }

  private HumanCommentFormatter getCommentFormatter(ChangeResource rsrc) {
    return commentJson
        .get()
        .setFillAccounts(false)
        .setFillPatchSet(true)
        .setFillCommentContext(includeContext)
        .setContextPadding(contextPadding)
        .setProjectKey(rsrc.getProject())
        .setChangeId(rsrc.getId())
        .newHumanCommentFormatter();
  }
}
