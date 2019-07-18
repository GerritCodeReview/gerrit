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

import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.DraftCommentResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class GetDraftComment implements RestReadView<DraftCommentResource> {

  private final Provider<CommentJson> commentJson;

  @Inject
  GetDraftComment(Provider<CommentJson> commentJson) {
    this.commentJson = commentJson;
  }

  @Override
  public Response<CommentInfo> apply(DraftCommentResource rsrc) throws PermissionBackendException {
    return Response.ok(commentJson.get().newCommentFormatter().format(rsrc.getComment()));
  }
}
