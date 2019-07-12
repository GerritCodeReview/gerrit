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

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.inject.Inject;
import java.util.Collection;

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
    gApi.changes().id(changeId).revision(revId).createDraft(in).get();
  }

  public Collection<CommentInfo> getPublishedComments(String changeId) throws Exception {
    return gApi.changes()
        .id(changeId)
        .comments()
        .values()
        .stream()
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
}
