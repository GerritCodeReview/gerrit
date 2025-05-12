// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.testing;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Test helper for dealing with comments/drafts. */
public class TestCommentHelper {
  private final GerritApi gApi;

  @Inject
  public TestCommentHelper(GerritApi gerritApi) {
    gApi = gerritApi;
  }

  public DraftInput newDraft(String message) {
    return populate(new DraftInput(), "file", message);
  }

  public DraftInput newDraft(String message, String inReplyTo) {
    return populate(new DraftInput(), "file", createLineRange(), message, inReplyTo);
  }

  public DraftInput newDraft(String path, Side side, int line, String message) {
    DraftInput d = new DraftInput();
    return populate(d, path, side, line, message);
  }

  public void addDraft(String changeId, String revId, DraftInput in) throws Exception {
    gApi.changes().id(changeId).revision(revId).createDraft(in);
  }

  public void addDraft(String changeId, DraftInput in) throws Exception {
    gApi.changes().id(changeId).current().createDraft(in);
  }

  public List<CommentInfo> getPublishedComments(String changeId) throws Exception {
    return gApi.changes().id(changeId).commentsRequest().get().values().stream()
        .flatMap(Collection::stream)
        .collect(toList());
  }

  public static <C extends Comment> C populate(C c, String path, String message) {
    return populate(c, path, createLineRange(), message, null);
  }

  private static <C extends Comment> C populate(
      C c, String path, Range range, String message, String inReplyTo) {
    int line = range.startLine;
    c.path = path;
    c.side = Side.REVISION;
    c.parent = null;
    c.line = line != 0 ? line : null;
    c.message = message;
    c.inReplyTo = inReplyTo;
    if (line != 0) c.range = range;
    return c;
  }

  private static <C extends Comment> C populate(
      C c, String path, Side side, Range range, String message) {
    int line = range.startLine;
    c.path = path;
    c.side = side;
    c.parent = null;
    c.line = line != 0 ? line : null;
    c.message = message;
    if (line != 0) c.range = range;
    return c;
  }

  private static <C extends Comment> C populate(
      C c, String path, Side side, int line, String message) {
    return populate(c, path, side, createLineRange(line), message);
  }

  private static Range createLineRange() {
    Range range = new Range();
    range.startLine = 0;
    range.startCharacter = 1;
    range.endLine = 0;
    range.endCharacter = 5;
    return range;
  }

  private static Range createLineRange(int line) {
    Range range = new Range();
    range.startLine = line;
    range.startCharacter = 1;
    range.endLine = line;
    range.endCharacter = 5;
    return range;
  }

  public static CommentInput createCommentInputWithMandatoryFields(String path) {
    CommentInput in = new CommentInput();
    in.message = "nit: trailing whitespace";
    in.path = path;
    return in;
  }

  public static CommentInput createCommentInput(
      String path, FixSuggestionInfo... fixSuggestionInfos) {
    CommentInput in = new CommentInput();
    in.message = "nit: trailing whitespace";
    in.path = path;
    in.fixSuggestions = Arrays.asList(fixSuggestionInfos);
    return in;
  }

  public void addComment(String targetChangeId, CommentInput commentInput) throws Exception {
    addComment(targetChangeId, commentInput, "comment test");
  }

  public void addComment(String targetChangeId, CommentInput commentInput, String message)
      throws Exception {
    ReviewInput reviewInput = createReviewInput(commentInput, message);
    gApi.changes().id(targetChangeId).current().review(reviewInput);
  }

  private ReviewInput createReviewInput(CommentInput commentInput, String message) {
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.comments =
        Collections.singletonMap(commentInput.path, ImmutableList.of(commentInput));
    reviewInput.message = message;
    reviewInput.tag = ChangeMessagesUtil.AUTOGENERATED_TAG_PREFIX;
    return reviewInput;
  }
}
