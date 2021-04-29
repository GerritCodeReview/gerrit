// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

/** Computes the ported comments for a specific file path. */
@Singleton
public class GetPortedComments implements RestReadView<FileResource> {
  private final CommentsUtil commentsUtil;
  private final CommentPorter commentPorter;
  private final Provider<CommentJson> commentJson;

  @Inject
  public GetPortedComments(
      Provider<CommentJson> commentJson, CommentsUtil commentsUtil, CommentPorter commentPorter) {
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
    this.commentPorter = commentPorter;
  }

  @Override
  public Response<Map<String, List<CommentInfo>>> apply(FileResource fileResource)
      throws PermissionBackendException {
    PatchSet targetPatchset = fileResource.getRevision().getPatchSet();
    ChangeNotes notes = fileResource.getRevision().getNotes();

    List<HumanComment> allComments = commentsUtil.publishedHumanCommentsByChange(notes);

    // Filter comments for a specific file path
    allComments =
        allComments.stream()
            .filter(c -> c.key.filename.equals(fileResource.getPatchKey().fileName()))
            .collect(toImmutableList());

    ImmutableList<HumanComment> portedComments =
        commentPorter.portComments(
            notes, targetPatchset, allComments, ImmutableList.of(new UnresolvedCommentFilter()));
    return Response.ok(format(portedComments));
  }

  private Map<String, List<CommentInfo>> format(List<HumanComment> comments)
      throws PermissionBackendException {
    return commentJson
        .get()
        .setFillAccounts(true)
        .setFillPatchSet(true)
        .newHumanCommentFormatter()
        .format(comments);
  }
}
