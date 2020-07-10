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
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
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
import java.util.HashMap;

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

  public DraftInput newDraft(String path, Side side, int line, String message) {
    DraftInput d = new DraftInput();
    return populate(d, path, side, line, message);
  }

  public void addDraft(String changeId, String revId, DraftInput in) throws Exception {
    gApi.changes().id(changeId).revision(revId).createDraft(in);
  }

  public Collection<CommentInfo> getPublishedComments(String changeId) throws Exception {
    return gApi.changes().id(changeId).comments(false).values().stream()
        .flatMap(Collection::stream)
        .collect(toList());
  }

  public static <C extends Comment> C populate(C c, String path, String message) {
    return populate(c, path, createLineRange(), message);
  }

  private static <C extends Comment> C populate(C c, String path, Range range, String message) {
    int line = range.startLine;
    c.path = path;
    c.side = Side.REVISION;
    c.parent = null;
    c.line = line != 0 ? line : null;
    c.message = message;
    c.unresolved = false;
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
    c.unresolved = false;
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

  public static RobotCommentInput createRobotCommentInputWithMandatoryFields(String path) {
    RobotCommentInput in = new RobotCommentInput();
    in.robotId = "happyRobot";
    in.robotRunId = "1";
    in.message = "nit: trailing whitespace";
    in.path = path;
    return in;
  }

  public static RobotCommentInput createRobotCommentInput(
      String path, FixSuggestionInfo... fixSuggestionInfos) {
    RobotCommentInput in = TestCommentHelper.createRobotCommentInputWithMandatoryFields(path);
    in.url = "http://www.happy-robot.com";
    in.properties = new HashMap<>();
    in.properties.put("key1", "value1");
    in.properties.put("key2", "value2");
    in.fixSuggestions = Arrays.asList(fixSuggestionInfos);
    return in;
  }

  public void addRobotComment(String targetChangeId, RobotCommentInput robotCommentInput)
      throws Exception {
    addRobotComment(targetChangeId, robotCommentInput, "robot comment test");
  }

  public void addRobotComment(
      String targetChangeId, RobotCommentInput robotCommentInput, String message) throws Exception {
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.robotComments =
        Collections.singletonMap(robotCommentInput.path, ImmutableList.of(robotCommentInput));
    reviewInput.message = message;
    reviewInput.tag = ChangeMessagesUtil.AUTOGENERATED_TAG_PREFIX;
    gApi.changes().id(targetChangeId).current().review(reviewInput);
  }
}
