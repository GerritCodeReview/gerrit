// Copyright (C) 2014 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.Comment;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentsIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config noteDbEnabled() {
    return NotesMigration.allEnabledConfig();
  }

  @Test
  public void createDraft() throws GitAPIException, IOException {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    ReviewInput.CommentInput comment = newCommentInfo(
        "file1", Comment.Side.REVISION, 1, "comment 1");
    addDraft(changeId, revId, comment);
    Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
    assertEquals(1, result.size());
    CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
    assertCommentInfo(comment, actual);
  }

  @Test
  public void postComment() throws RestApiException, Exception {
    String file = "file";
    String contents = "contents";
    PushOneCommit push = pushFactory.create(db, admin.getIdent(),
        "first subject", file, contents);
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    ReviewInput input = new ReviewInput();
    ReviewInput.CommentInput comment = newCommentInfo(
        file, Comment.Side.REVISION, 1, "comment 1");
    input.comments = new HashMap<String, List<ReviewInput.CommentInput>>();
    input.comments.put(comment.path, Lists.newArrayList(comment));
    revision(r).review(input);
    Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
    assertTrue(!result.isEmpty());
    CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
    assertCommentInfo(comment, actual);
  }

  @Test
  public void putDraft() throws GitAPIException, IOException {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    ReviewInput.CommentInput comment = newCommentInfo(
        "file1", Comment.Side.REVISION, 1, "comment 1");
    addDraft(changeId, revId, comment);
    Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
    CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
    assertCommentInfo(comment, actual);
    String uuid = actual.id;
    comment.message = "updated comment 1";
    updateDraft(changeId, revId, comment, uuid);
    result = getDraftComments(changeId, revId);
    actual = Iterables.getOnlyElement(result.get(comment.path));
    assertCommentInfo(comment, actual);
  }

  @Test
  public void getDraft() throws GitAPIException, IOException {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    ReviewInput.CommentInput comment = newCommentInfo(
        "file1", Comment.Side.REVISION, 1, "comment 1");
    CommentInfo returned = addDraft(changeId, revId, comment);
    CommentInfo actual = getDraftComment(changeId, revId, returned.id);
    assertCommentInfo(comment, actual);
  }

  @Test
  public void deleteDraft() throws IOException, GitAPIException {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    ReviewInput.CommentInput comment = newCommentInfo(
        "file1", Comment.Side.REVISION, 1, "comment 1");
    CommentInfo returned = addDraft(changeId, revId, comment);
    deleteDraft(changeId, revId, returned.id);
    Map<String, List<CommentInfo>> drafts = getDraftComments(changeId, revId);
    assertTrue(drafts.isEmpty());
  }

  private CommentInfo addDraft(String changeId, String revId,
      ReviewInput.CommentInput c) throws IOException {
    RestResponse r = userSession.put(
        "/changes/" + changeId + "/revisions/" + revId + "/drafts", c);
    assertEquals(HttpStatus.SC_CREATED, r.getStatusCode());
    return newGson().fromJson(r.getReader(), CommentInfo.class);
  }

  private void updateDraft(String changeId, String revId,
      ReviewInput.CommentInput c, String uuid) throws IOException {
    RestResponse r = userSession.put(
        "/changes/" + changeId + "/revisions/" + revId + "/drafts/" + uuid, c);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
  }

  private void deleteDraft(String changeId, String revId, String uuid)
      throws IOException {
    RestResponse r = userSession.delete(
        "/changes/" + changeId + "/revisions/" + revId + "/drafts/" + uuid);
    assertEquals(HttpStatus.SC_NO_CONTENT, r.getStatusCode());
  }

  private Map<String, List<CommentInfo>> getPublishedComments(String changeId,
      String revId) throws IOException {
    RestResponse r = userSession.get(
        "/changes/" + changeId + "/revisions/" + revId + "/comments/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Type mapType = new TypeToken<Map<String, List<CommentInfo>>>() {}.getType();
    return newGson().fromJson(r.getReader(), mapType);
  }

  private Map<String, List<CommentInfo>> getDraftComments(String changeId,
      String revId) throws IOException {
    RestResponse r = userSession.get(
        "/changes/" + changeId + "/revisions/" + revId + "/drafts/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Type mapType = new TypeToken<Map<String, List<CommentInfo>>>() {}.getType();
    return newGson().fromJson(r.getReader(), mapType);
  }

  private CommentInfo getDraftComment(String changeId, String revId,
      String uuid) throws IOException {
    RestResponse r = userSession.get(
        "/changes/" + changeId + "/revisions/" + revId + "/drafts/" + uuid);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    return newGson().fromJson(r.getReader(), CommentInfo.class);
  }

  private static void assertCommentInfo(ReviewInput.CommentInput expected,
      CommentInfo actual) {
    assertEquals(expected.line, actual.line);
    assertEquals(expected.message, actual.message);
    assertEquals(expected.inReplyTo, actual.inReplyTo);
    if (actual.side == null) {
      assertEquals(expected.side, Comment.Side.REVISION);
    }
  }

  private ReviewInput.CommentInput newCommentInfo(String path,
      Comment.Side side, int line, String message) {
    ReviewInput.CommentInput input = new ReviewInput.CommentInput();
    input.path = path;
    input.side = side;
    input.line = line;
    input.message = message;
    return input;
  }
}
