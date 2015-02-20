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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Revisions;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentsIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config noteDbEnabled() {
    return NotesMigration.allEnabledConfig();
  }

  @Inject
  private Provider<ChangesCollection> changes;

  @Inject
  private Provider<Revisions> revisions;

  @Inject
  private Provider<PostReview> postReview;

  private final Integer lines[] = {0, 1};

  @Test
  public void createDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput.CommentInput comment = newCommentInfo(
          "file1", Side.REVISION, line, "comment 1");
      addDraft(changeId, revId, comment);
      Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
      assertThat(result).hasSize(1);
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertCommentInfo(comment, actual);
    }
  }

  @Test
  public void postComment() throws Exception {
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push = pushFactory.create(db, admin.getIdent(),
          "first subject", file, contents);
      PushOneCommit.Result r = push.to(git, "refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      ReviewInput.CommentInput comment = newCommentInfo(
          file, Side.REVISION, line, "comment 1");
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertCommentInfo(comment, actual);
    }
  }

  @Test
  public void putDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput.CommentInput comment = newCommentInfo(
          "file1", Side.REVISION, line, "comment 1");
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
  }

  @Test
  public void getDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput.CommentInput comment = newCommentInfo(
          "file1", Side.REVISION, line, "comment 1");
      CommentInfo returned = addDraft(changeId, revId, comment);
      CommentInfo actual = getDraftComment(changeId, revId, returned.id);
      assertCommentInfo(comment, actual);
    }
  }

  @Test
  public void deleteDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput.CommentInput comment = newCommentInfo(
          "file1", Side.REVISION, line, "comment 1");
      CommentInfo returned = addDraft(changeId, revId, comment);
      deleteDraft(changeId, revId, returned.id);
      Map<String, List<CommentInfo>> drafts = getDraftComments(changeId, revId);
      assertThat(drafts).isEmpty();
    }
  }

  @Test
  public void insertCommentsWithHistoricTimestamp() throws Exception {
    Timestamp timestamp = new Timestamp(0);
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push = pushFactory.create(db, admin.getIdent(),
          "first subject", file, contents);
      PushOneCommit.Result r = push.to(git, "refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      ReviewInput.CommentInput comment = newCommentInfo(
          file, Side.REVISION, line, "comment 1");
      comment.updated = timestamp;
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      ChangeResource changeRsrc =
          changes.get().parse(TopLevelResource.INSTANCE,
              IdString.fromDecoded(changeId));
      RevisionResource revRsrc =
          revisions.get().parse(changeRsrc, IdString.fromDecoded(revId));
      postReview.get().apply(revRsrc, input, timestamp);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertCommentInfo(comment, actual);
      assertThat(comment.updated).isEqualTo(timestamp);
    }
  }

  private CommentInfo addDraft(String changeId, String revId,
      ReviewInput.CommentInput c) throws IOException {
    RestResponse r = userSession.put(
        "/changes/" + changeId + "/revisions/" + revId + "/drafts", c);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    return newGson().fromJson(r.getReader(), CommentInfo.class);
  }

  private void updateDraft(String changeId, String revId,
      ReviewInput.CommentInput c, String uuid) throws IOException {
    RestResponse r = userSession.put(
        "/changes/" + changeId + "/revisions/" + revId + "/drafts/" + uuid, c);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
  }

  private void deleteDraft(String changeId, String revId, String uuid)
      throws IOException {
    RestResponse r = userSession.delete(
        "/changes/" + changeId + "/revisions/" + revId + "/drafts/" + uuid);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);
  }

  private Map<String, List<CommentInfo>> getPublishedComments(String changeId,
      String revId) throws IOException {
    RestResponse r = userSession.get(
        "/changes/" + changeId + "/revisions/" + revId + "/comments/");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Type mapType = new TypeToken<Map<String, List<CommentInfo>>>() {}.getType();
    return newGson().fromJson(r.getReader(), mapType);
  }

  private Map<String, List<CommentInfo>> getDraftComments(String changeId,
      String revId) throws IOException {
    RestResponse r = userSession.get(
        "/changes/" + changeId + "/revisions/" + revId + "/drafts/");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Type mapType = new TypeToken<Map<String, List<CommentInfo>>>() {}.getType();
    return newGson().fromJson(r.getReader(), mapType);
  }

  private CommentInfo getDraftComment(String changeId, String revId,
      String uuid) throws IOException {
    RestResponse r = userSession.get(
        "/changes/" + changeId + "/revisions/" + revId + "/drafts/" + uuid);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    return newGson().fromJson(r.getReader(), CommentInfo.class);
  }

  private static void assertCommentInfo(ReviewInput.CommentInput expected,
      CommentInfo actual) {
    assertThat(actual.line).isEqualTo(expected.line);
    assertThat(actual.message).isEqualTo(expected.message);
    assertThat(actual.inReplyTo).isEqualTo(expected.inReplyTo);
    assertCommentRange(expected.range, actual.range);
    if (actual.side == null) {
      assertThat(Side.REVISION).isEqualTo(expected.side);
    }
  }

  private static void assertCommentRange(Comment.Range expected,
      Comment.Range actual) {
    if (expected == null) {
      assertThat(actual).isNull();
    } else {
      assertThat(actual).isNotNull();
      assertThat(actual.startLine).isEqualTo(expected.startLine);
      assertThat(actual.startCharacter).isEqualTo(expected.startCharacter);
      assertThat(actual.endLine).isEqualTo(expected.endLine);
      assertThat(actual.endCharacter).isEqualTo(expected.endCharacter);
    }
  }

  private ReviewInput.CommentInput newCommentInfo(String path,
      Side side, int line, String message) {
    ReviewInput.CommentInput input = new ReviewInput.CommentInput();
    input.path = path;
    input.side = side;
    input.line = line != 0 ? line : null;
    input.message = message;
    if (line != 0) {
      Comment.Range range = new Comment.Range();
      range.startLine = 1;
      range.startCharacter = 1;
      range.endLine = 1;
      range.endCharacter = 5;
      input.range = range;
    }
    return input;
  }
}
