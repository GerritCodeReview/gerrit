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

package com.google.gerrit.acceptance.server.change;

import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import java.util.Arrays;
import java.util.HashMap;

/**
 * A utility class for creating {@link CommentInput} objects, publishing comments and creating draft
 * comments. Used by tests that require dealing with comments.
 */
class CommentsUtil {
  static CommentInput addComment(GerritApi gApi, String changeId) throws Exception {
    ReviewInput input = new ReviewInput();
    CommentInput comment = CommentsUtil.newComment(FILE_NAME, Side.REVISION, 0, "a message", false);
    input.comments = ImmutableMap.of(comment.path, Lists.newArrayList(comment));
    gApi.changes().id(changeId).current().review(input);
    return comment;
  }

  static void addComments(GerritApi gApi, Change.Id changeId, CommentInput... commentInputs)
      throws Exception {
    ReviewInput input = new ReviewInput();
    input.comments = Arrays.stream(commentInputs).collect(groupingBy(c -> c.path));
    gApi.changes().id(changeId.get()).current().review(input);
  }

  static void addComments(
      GerritApi gApi, String changeId, String revision, CommentInput... commentInputs)
      throws Exception {
    ReviewInput input = new ReviewInput();
    input.comments = Arrays.stream(commentInputs).collect(groupingBy(c -> c.path));
    gApi.changes().id(changeId).revision(revision).review(input);
  }

  static CommentInput newComment(String file, String message) {
    return newComment(file, Side.REVISION, 0, message, false);
  }

  static CommentInput newCommentWithOnlyMandatoryFields(String path, String message) {
    CommentInput c = new CommentInput();
    c.unresolved = false;
    return populate(c, path, null, null, null, null, message);
  }

  static CommentInput newComment(
      String path, Side side, int line, String message, Boolean unresolved) {
    CommentInput c = new CommentInput();
    c.unresolved = unresolved;
    return populate(c, path, side, null, line, message);
  }

  static CommentInput newCommentOnParent(String path, int parent, int line, String message) {
    CommentInput c = new CommentInput();
    c.unresolved = false;
    return populate(c, path, Side.PARENT, parent, line, message);
  }

  static DraftInput newDraft(String path, Side side, int line, String message) {
    DraftInput d = new DraftInput();
    d.unresolved = false;
    return populate(d, path, side, null, line, message);
  }

  static DraftInput newDraft(String path, Side side, Comment.Range range, String message) {
    DraftInput d = new DraftInput();
    d.unresolved = false;
    return populate(d, path, side, null, range.startLine, range, message);
  }

  static DraftInput newDraftOnParent(String path, int parent, int line, String message) {
    DraftInput d = new DraftInput();
    d.unresolved = false;
    return populate(d, path, Side.PARENT, parent, line, message);
  }

  static DraftInput newDraftWithOnlyMandatoryFields(String path, String message) {
    DraftInput d = new DraftInput();
    d.unresolved = false;
    return populate(d, path, null, null, null, null, message);
  }

  static <C extends Comment> C populate(
      C c,
      String path,
      Side side,
      Integer parent,
      Integer line,
      Comment.Range range,
      String message) {
    c.path = path;
    c.side = side;
    c.parent = parent;
    c.line = line != null && line != 0 ? line : null;
    c.message = message;
    if (range != null) {
      c.range = range;
    }
    return c;
  }

  static <C extends Comment> C populate(
      C c, String path, Side side, Integer parent, int line, String message) {
    return populate(c, path, side, parent, line, null, message);
  }

  static ReviewInput newInput(CommentInput c) {
    ReviewInput in = new ReviewInput();
    in.comments = new HashMap<>();
    in.comments.put(c.path, Lists.newArrayList(c));
    return in;
  }

  static void addComment(GerritApi gApi, PushOneCommit.Result r, String message) throws Exception {
    addComment(gApi, r, message, false, false, null, null, null);
  }

  static void addComment(
      GerritApi gApi,
      PushOneCommit.Result r,
      String message,
      boolean omitDuplicateComments,
      Boolean unresolved,
      String inReplyTo)
      throws Exception {
    addComment(gApi, r, message, omitDuplicateComments, unresolved, inReplyTo, null, null);
  }

  static void addCommentOnLine(GerritApi gApi, PushOneCommit.Result r, String message, int line)
      throws Exception {
    addComment(gApi, r, message, false, false, null, line, null);
  }

  static void addCommentOnRange(
      GerritApi gApi, PushOneCommit.Result r, String message, Comment.Range range)
      throws Exception {
    addComment(gApi, r, message, false, false, null, null, range);
  }

  static void addComment(
      GerritApi gApi,
      PushOneCommit.Result r,
      String message,
      boolean omitDuplicateComments,
      Boolean unresolved,
      String inReplyTo,
      Integer line,
      Comment.Range range)
      throws Exception {
    CommentInput c = new CommentInput();
    c.line = line == null ? 1 : line;
    c.message = message;
    c.path = FILE_NAME;
    c.unresolved = unresolved;
    c.inReplyTo = inReplyTo;
    c.range = range;
    ReviewInput in = CommentsUtil.newInput(c);
    in.omitDuplicateComments = omitDuplicateComments;
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(in);
  }
}
