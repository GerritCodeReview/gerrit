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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CommentContextException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.CommentJson.HumanCommentFormatter;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ListChangeDrafts implements RestReadView<ChangeResource> {
  private final ChangeData.Factory changeDataFactory;
  private final Provider<CommentJson> commentJson;
  private final CommentsUtil commentsUtil;

  @Inject
  ListChangeDrafts(
      ChangeData.Factory changeDataFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil) {
    this.changeDataFactory = changeDataFactory;
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
  }

  private Iterable<HumanComment> listComments(ChangeResource rsrc) {
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    return commentsUtil.draftByChangeAuthor(cd.notes(), rsrc.getUser().getAccountId());
  }

  @Override
  public Response<Map<String, List<CommentInfo>>> apply(ChangeResource rsrc)
      throws AuthException, PermissionBackendException, CommentContextException {
    if (!rsrc.getUser().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    return Response.ok(getCommentFormatter().format(listComments(rsrc)));
  }

  public List<CommentInfo> getComments(ChangeResource rsrc)
      throws AuthException, PermissionBackendException, CommentContextException {
    if (!rsrc.getUser().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    return getCommentFormatter().formatAsList(listComments(rsrc));
  }

  private HumanCommentFormatter getCommentFormatter() {
    return commentJson
        .get()
        .setFillAccounts(false)
        .setFillPatchSet(true)
        .newHumanCommentFormatter();
  }
}
