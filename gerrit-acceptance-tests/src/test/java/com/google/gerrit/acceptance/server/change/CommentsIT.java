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
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.acceptance.PushOneCommit.SUBJECT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.testutil.FakeEmailSender;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoHttpd
public class CommentsIT extends AbstractDaemonTest {
  @Inject
  private Provider<ChangesCollection> changes;

  @Inject
  private Provider<PostReview> postReview;

  @Inject
  private FakeEmailSender email;

  private final Integer[] lines = {0, 1};

  @Before
  public void setUp() {
    setApiUser(user);
  }

  @Test
  public void getNonExistingComment() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    exception.expect(ResourceNotFoundException.class);
    getPublishedComment(changeId, revId, "non-existing");
  }

  @Test
  public void createDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      DraftInput comment = newDraft("file1", Side.REVISION, line, "comment 1");
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
      PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
          "first subject", file, contents);
      PushOneCommit.Result r = push.to("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment 1");
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertCommentInfo(comment, actual);
      assertCommentInfo(actual, getPublishedComment(changeId, revId, actual.id));
    }
  }

  @Test
  public void listComments() throws Exception {
    String file = "file";
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        "first subject", file, "contents");
    PushOneCommit.Result r = push.to("refs/for/master");
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    assertThat(getPublishedComments(changeId, revId)).isEmpty();

    List<Comment> expectedComments = new ArrayList<>();
    for (Integer line : lines) {
      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment " + line);
      expectedComments.add(comment);
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
    }

    Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
    assertThat(result).isNotEmpty();
    List<CommentInfo> actualComments = result.get(file);
    assertThat(actualComments).hasSize(expectedComments.size());
    for (int i = 0; i < actualComments.size(); i++) {
      assertCommentInfo(expectedComments.get(i), actualComments.get(i));
    }
  }

  @Test
  public void putDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      Timestamp origLastUpdated = r.getChange().change().getLastUpdatedOn();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      DraftInput comment = newDraft("file1", Side.REVISION, line, "comment 1");
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

      // Posting a draft comment doesn't cause lastUpdatedOn to change.
      assertThat(r.getChange().change().getLastUpdatedOn())
          .isEqualTo(origLastUpdated);
    }
  }

  @Test
  public void listDrafts() throws Exception {
    String file = "file";
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    assertThat(getDraftComments(changeId, revId)).isEmpty();

    List<Comment> expectedDrafts = new ArrayList<>();
    for (Integer line : lines) {
      DraftInput comment = newDraft(file, Side.REVISION, line, "comment " + line);
      expectedDrafts.add(comment);
      addDraft(changeId, revId, comment);
    }

    Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
    assertThat(result).isNotEmpty();
    List<CommentInfo> actualComments = result.get(file);
    assertThat(actualComments).hasSize(expectedDrafts.size());
    for (int i = 0; i < actualComments.size(); i++) {
      assertCommentInfo(expectedDrafts.get(i), actualComments.get(i));
    }
  }

  @Test
  public void getDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      DraftInput comment = newDraft(
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
      Timestamp origLastUpdated = r.getChange().change().getLastUpdatedOn();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      DraftInput draft = newDraft("file1", Side.REVISION, line, "comment 1");
      CommentInfo returned = addDraft(changeId, revId, draft);
      deleteDraft(changeId, revId, returned.id);
      Map<String, List<CommentInfo>> drafts = getDraftComments(changeId, revId);
      assertThat(drafts).isEmpty();

      // Deleting a draft comment doesn't cause lastUpdatedOn to change.
      assertThat(r.getChange().change().getLastUpdatedOn())
          .isEqualTo(origLastUpdated);
    }
  }

  @Test
  public void insertCommentsWithHistoricTimestamp() throws Exception {
    Timestamp timestamp = new Timestamp(0);
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
          "first subject", file, contents);
      PushOneCommit.Result r = push.to("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      Timestamp origLastUpdated = r.getChange().change().getLastUpdatedOn();

      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment 1");
      comment.updated = timestamp;
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      ChangeResource changeRsrc =
          changes.get().parse(TopLevelResource.INSTANCE,
              IdString.fromDecoded(changeId));
      RevisionResource revRsrc =
          revisions.parse(changeRsrc, IdString.fromDecoded(revId));
      postReview.get().apply(revRsrc, input, timestamp);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertCommentInfo(comment, actual);
      assertThat(actual.updated)
          .isEqualTo(gApi.changes().id(r.getChangeId()).info().created);

      // Updating historic comments doesn't cause lastUpdatedOn to regress.
      assertThat(r.getChange().change().getLastUpdatedOn())
          .isEqualTo(origLastUpdated);
    }
  }

  @Test
  public void addDuplicateComments() throws Exception {
    PushOneCommit.Result r1 = createChange();
    String changeId = r1.getChangeId();
    String revId = r1.getCommit().getName();
    addComment(r1, "nit: trailing whitespace");
    addComment(r1, "nit: trailing whitespace");
    Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
    assertThat(result.get(FILE_NAME)).hasSize(2);
    addComment(r1, "nit: trailing whitespace", true);
    result = getPublishedComments(changeId, revId);
    assertThat(result.get(FILE_NAME)).hasSize(2);

    PushOneCommit.Result r2 = pushFactory.create(
          db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "content")
        .to("refs/for/master");
    changeId = r2.getChangeId();
    revId = r2.getCommit().getName();
    addComment(r2, "nit: trailing whitespace", true);
    result = getPublishedComments(changeId, revId);
    assertThat(result.get(FILE_NAME)).hasSize(1);
  }

  @Test
  public void listChangeDrafts() throws Exception {
    PushOneCommit.Result r1 = createChange();

    PushOneCommit.Result r2 = pushFactory.create(
          db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "new content",
          r1.getChangeId())
        .to("refs/for/master");


    setApiUser(admin);
    addDraft(r1.getChangeId(), r1.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "nit: trailing whitespace"));
    addDraft(r2.getChangeId(), r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "typo: content"));

    setApiUser(user);
    addDraft(r2.getChangeId(), r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "+1, please fix"));

    setApiUser(admin);
    Map<String, List<CommentInfo>> actual =
        gApi.changes().id(r1.getChangeId()).drafts();
    assertThat((Iterable<?>) actual.keySet()).containsExactly(FILE_NAME);
    List<CommentInfo> comments = actual.get(FILE_NAME);
    assertThat(comments).hasSize(2);

    CommentInfo c1 = comments.get(0);
    assertThat(c1.author).isNull();
    assertThat(c1.patchSet).isEqualTo(1);
    assertThat(c1.message).isEqualTo("nit: trailing whitespace");
    assertThat(c1.side).isNull();
    assertThat(c1.line).isEqualTo(1);

    CommentInfo c2 = comments.get(1);
    assertThat(c2.author).isNull();
    assertThat(c2.patchSet).isEqualTo(2);
    assertThat(c2.message).isEqualTo("typo: content");
    assertThat(c2.side).isNull();
    assertThat(c2.line).isEqualTo(1);
  }

  @Test
  public void listChangeComments() throws Exception {
    PushOneCommit.Result r1 = createChange();

    PushOneCommit.Result r2 = pushFactory.create(
          db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "new cntent",
          r1.getChangeId())
        .to("refs/for/master");

    addComment(r1, "nit: trailing whitespace");
    addComment(r2, "typo: content");

    Map<String, List<CommentInfo>> actual = gApi.changes()
        .id(r2.getChangeId())
        .comments();
    assertThat(actual.keySet()).containsExactly(FILE_NAME);

    List<CommentInfo> comments = actual.get(FILE_NAME);
    assertThat(comments).hasSize(2);

    CommentInfo c1 = comments.get(0);
    assertThat(c1.author._accountId).isEqualTo(user.getId().get());
    assertThat(c1.patchSet).isEqualTo(1);
    assertThat(c1.message).isEqualTo("nit: trailing whitespace");
    assertThat(c1.side).isNull();
    assertThat(c1.line).isEqualTo(1);

    CommentInfo c2 = comments.get(1);
    assertThat(c2.author._accountId).isEqualTo(user.getId().get());
    assertThat(c2.patchSet).isEqualTo(2);
    assertThat(c2.message).isEqualTo("typo: content");
    assertThat(c2.side).isNull();
    assertThat(c2.line).isEqualTo(1);
  }

  @Test
  public void listChangeWithDrafts() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      DraftInput comment = newDraft(
          "file1", Side.REVISION, line, "comment 1");
      addDraft(changeId, revId, comment);
      assertThat(gApi.changes().query(
          "change:" + changeId + " has:draft").get()).hasSize(1);
    }
  }

  @Test
  public void publishCommentsAllRevisions() throws Exception {
    PushOneCommit.Result r1 = createChange();

    PushOneCommit.Result r2 = pushFactory.create(
          db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "new\ncntent\n",
          r1.getChangeId())
        .to("refs/for/master");

    addDraft(r1.getChangeId(), r1.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "nit: trailing whitespace"));
    addDraft(r1.getChangeId(), r1.getCommit().getName(),
        newDraft(FILE_NAME, Side.PARENT, 2, "what happened to this?"));
    addDraft(r2.getChangeId(), r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "join lines"));
    addDraft(r2.getChangeId(), r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 2, "typo: content"));
    addDraft(r2.getChangeId(), r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.PARENT, 1, "comment 1 on base"));
    addDraft(r2.getChangeId(), r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.PARENT, 2, "comment 2 on base"));

    PushOneCommit.Result other = createChange();
    // Drafts on other changes aren't returned.
    addDraft(other.getChangeId(), other.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "unrelated comment"));

    setApiUser(admin);
    // Drafts by other users aren't returned.
    addDraft(r2.getChangeId(), r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 2, "oops"));
    setApiUser(user);

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
    reviewInput.message = "comments";
    gApi.changes()
       .id(r2.getChangeId())
       .current()
       .review(reviewInput);

    assertThat(gApi.changes()
          .id(r1.getChangeId())
          .revision(r1.getCommit().name())
          .drafts())
        .isEmpty();
    Map<String, List<CommentInfo>> ps1Map = gApi.changes()
        .id(r1.getChangeId())
        .revision(r1.getCommit().name())
        .comments();
    assertThat(ps1Map.keySet()).containsExactly(FILE_NAME);
    List<CommentInfo> ps1List = ps1Map.get(FILE_NAME);
    assertThat(ps1List).hasSize(2);
    assertThat(ps1List.get(0).message).isEqualTo("what happened to this?");
    assertThat(ps1List.get(0).side).isEqualTo(Side.PARENT);
    assertThat(ps1List.get(1).message).isEqualTo("nit: trailing whitespace");
    assertThat(ps1List.get(1).side).isNull();

    assertThat(gApi.changes()
          .id(r2.getChangeId())
          .revision(r2.getCommit().name())
          .drafts())
        .isEmpty();
    Map<String, List<CommentInfo>> ps2Map = gApi.changes()
        .id(r2.getChangeId())
        .revision(r2.getCommit().name())
        .comments();
    assertThat(ps2Map.keySet()).containsExactly(FILE_NAME);
    List<CommentInfo> ps2List = ps2Map.get(FILE_NAME);
    assertThat(ps2List).hasSize(4);
    assertThat(ps2List.get(0).message).isEqualTo("comment 1 on base");
    assertThat(ps2List.get(1).message).isEqualTo("comment 2 on base");
    assertThat(ps2List.get(2).message).isEqualTo("join lines");
    assertThat(ps2List.get(3).message).isEqualTo("typo: content");

    ImmutableList<Message> messages =
        email.getMessages(r2.getChangeId(), "comment");
    assertThat(messages).hasSize(1);
    String url = canonicalWebUrl.get();
    int c = r1.getChange().getId().get();
    assertThat(extractComments(messages.get(0).body())).isEqualTo(
        "Patch Set 2:\n"
        + "\n"
        + "(6 comments)\n"
        + "\n"
        + "comments\n"
        + "\n"
        + url + "#/c/" + c + "/1/a.txt\n"
        + "File a.txt:\n"
        + "\n"
        + "PS1, Line 2: \n"
        + "what happened to this?\n"
        + "\n"
        + "\n"
        + "PS1, Line 1: ew\n"
        + "nit: trailing whitespace\n"
        + "\n"
        + "\n"
        + url + "#/c/" + c + "/2/a.txt\n"
        + "File a.txt:\n"
        + "\n"
        + "PS2, Line 1: \n"
        + "comment 1 on base\n"
        + "\n"
        + "\n"
        + "PS2, Line 2: \n"
        + "comment 2 on base\n"
        + "\n"
        + "\n"
        + "PS2, Line 1: ew\n"
        + "join lines\n"
        + "\n"
        + "\n"
        + "PS2, Line 2: nten\n"
        + "typo: content\n"
        + "\n");
  }

  @Test
  public void commentTags() throws Exception {
    PushOneCommit.Result r = createChange();

    CommentInput pub = new CommentInput();
    pub.line = 1;
    pub.message = "published comment";
    pub.path = FILE_NAME;
    ReviewInput rin = newInput(pub);
    rin.tag = "tag1";
    gApi.changes().id(r.getChangeId()).current().review(rin);

    List<CommentInfo> comments =
        gApi.changes().id(r.getChangeId()).current().commentsAsList();
    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).tag).isEqualTo("tag1");

    DraftInput draft = new DraftInput();
    draft.line = 2;
    draft.message = "draft comment";
    draft.path = FILE_NAME;
    draft.tag = "tag2";
    addDraft(r.getChangeId(), r.getCommit().name(), draft);

    List<CommentInfo> drafts =
        gApi.changes().id(r.getChangeId()).current().draftsAsList();
    assertThat(drafts).hasSize(1);
    assertThat(drafts.get(0).tag).isEqualTo("tag2");
  }

  private static String extractComments(String msg) {
    // Extract lines between start "....." and end "-- ".
    Pattern p = Pattern.compile(".*[.]{5}\n+(.*)\\n+-- \n.*", Pattern.DOTALL);
    Matcher m = p.matcher(msg);
    return m.matches() ? m.group(1) : msg;
  }

  private ReviewInput newInput(CommentInput c) {
    ReviewInput in = new ReviewInput();
    in.comments = new HashMap<>();
    in.comments.put(c.path, Lists.newArrayList(c));
    return in;
  }

  private void addComment(PushOneCommit.Result r, String message)
      throws Exception {
    addComment(r, message, false);
  }

  private void addComment(PushOneCommit.Result r, String message,
      boolean omitDuplicateComments) throws Exception {
    CommentInput c = new CommentInput();
    c.line = 1;
    c.message = message;
    c.path = FILE_NAME;
    ReviewInput in = newInput(c);
    in.omitDuplicateComments = omitDuplicateComments;
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(in);
  }

  private CommentInfo addDraft(String changeId, String revId, DraftInput in)
      throws Exception {
    return gApi.changes().id(changeId).revision(revId).createDraft(in).get();
  }

  private void updateDraft(String changeId, String revId, DraftInput in,
      String uuid) throws Exception {
    gApi.changes().id(changeId).revision(revId).draft(uuid).update(in);
  }

  private void deleteDraft(String changeId, String revId, String uuid)
      throws Exception {
    gApi.changes().id(changeId).revision(revId).draft(uuid).delete();
  }

  private CommentInfo getPublishedComment(String changeId, String revId,
      String uuid) throws Exception {
    return gApi.changes().id(changeId).revision(revId).comment(uuid).get();
  }

  private Map<String, List<CommentInfo>> getPublishedComments(String changeId,
      String revId) throws Exception {
    return gApi.changes().id(changeId).revision(revId).comments();
  }

  private Map<String, List<CommentInfo>> getDraftComments(String changeId,
      String revId) throws Exception {
    return gApi.changes().id(changeId).revision(revId).drafts();
  }

  private CommentInfo getDraftComment(String changeId, String revId,
      String uuid) throws Exception {
    return gApi.changes().id(changeId).revision(revId).draft(uuid).get();
  }

  private static void assertCommentInfo(Comment expected, CommentInfo actual) {
    assertThat(actual.line).isEqualTo(expected.line);
    assertThat(actual.message).isEqualTo(expected.message);
    assertThat(actual.inReplyTo).isEqualTo(expected.inReplyTo);
    assertCommentRange(expected.range, actual.range);
    if (actual.side == null && expected.side != null) {
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

  private static CommentInput newComment(String path, Side side, int line,
      String message) {
    CommentInput c = new CommentInput();
    return populate(c, path, side, line, message);
  }

  private DraftInput newDraft(String path, Side side, int line,
      String message) {
    DraftInput d = new DraftInput();
    return populate(d, path, side, line, message);
  }

  private static <C extends Comment> C populate(C c, String path, Side side,
      int line, String message) {
    c.path = path;
    c.side = side;
    c.line = line != 0 ? line : null;
    c.message = message;
    if (line != 0) {
      Comment.Range range = new Comment.Range();
      range.startLine = line;
      range.startCharacter = 1;
      range.endLine = line;
      range.endCharacter = 5;
      c.range = range;
    }
    return c;
  }
}
