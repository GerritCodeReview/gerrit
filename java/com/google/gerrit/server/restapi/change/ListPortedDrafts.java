// Copyright (C) 2020 The Android Open Source Project
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
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.DraftCommentsReader;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ListPortedDrafts implements RestReadView<RevisionResource> {

  private final DraftCommentsReader draftCommentsReader;
  private final CommentPorter commentPorter;
  private final Provider<CommentJson> commentJson;

  @Inject
  public ListPortedDrafts(
      Provider<CommentJson> commentJson,
      DraftCommentsReader draftCommentsReader,
      CommentPorter commentPorter) {
    this.commentJson = commentJson;
    this.draftCommentsReader = draftCommentsReader;
    this.commentPorter = commentPorter;
  }

  @Override
  public Response<Map<String, List<CommentInfo>>> apply(RevisionResource revisionResource)
      throws PermissionBackendException, RestApiException {
    if (!revisionResource.getUser().isIdentifiedUser()) {
      throw new AuthException("requires authentication; only authenticated users can have drafts");
    }
    PatchSet targetPatchset = revisionResource.getPatchSet();

    List<HumanComment> draftComments =
        draftCommentsReader.getDraftsByChangeAndDraftAuthor(
            revisionResource.getNotes(), revisionResource.getAccountId());
    ImmutableList<HumanComment> portedDraftComments =
        commentPorter.portComments(
            revisionResource.getNotes(), targetPatchset, draftComments, ImmutableList.of());
    return Response.ok(format(portedDraftComments));
  }

  private Map<String, List<CommentInfo>> format(List<HumanComment> comments)
      throws PermissionBackendException {
    return commentJson
        .get()
        // Always unset for draft comments as only draft comments of the requesting user are
        // returned.
        .setFillAccounts(false)
        .setFillPatchSet(true)
        .newHumanCommentFormatter()
        .format(comments);
  }
}
