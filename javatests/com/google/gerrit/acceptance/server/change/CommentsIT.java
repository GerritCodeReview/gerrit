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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.acceptance.PushOneCommit.SUBJECT;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.DeleteCommentInput;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.DeleteCommentRewriter;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.server.restapi.change.PostReview;
import com.google.gerrit.testing.FakeEmailSender;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CommentsIT extends AbstractDaemonTest {

  @Inject private Provider<ChangesCollection> changes;

  @Inject private Provider<PostReview> postReview;

  @Inject private FakeEmailSender email;

  @Inject private ChangeNoteUtil noteUtil;

  private final Integer[] lines = {0, 1};

  @Before
  public void setUp() throws Exception {
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
      String path = "file1";
      DraftInput comment = newDraft(path, Side.REVISION, line, "comment 1");
      addDraft(changeId, revId, comment);
      Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
      assertThat(result).hasSize(1);
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertThat(comment).isEqualTo(infoToDraft(path).apply(actual));
    }
  }

  @Test
  public void createDraftOnMergeCommitChange() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createMergeCommitChange("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      String path = "file1";
      DraftInput c1 = newDraft(path, Side.REVISION, line, "ps-1");
      DraftInput c2 = newDraft(path, Side.PARENT, line, "auto-merge of ps-1");
      DraftInput c3 = newDraftOnParent(path, 1, line, "parent-1 of ps-1");
      DraftInput c4 = newDraftOnParent(path, 2, line, "parent-2 of ps-1");
      addDraft(changeId, revId, c1);
      addDraft(changeId, revId, c2);
      addDraft(changeId, revId, c3);
      addDraft(changeId, revId, c4);
      Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
      assertThat(result).hasSize(1);
      assertThat(Lists.transform(result.get(path), infoToDraft(path)))
          .containsExactly(c1, c2, c3, c4);
    }
  }

  @Test
  public void postComment() throws Exception {
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push =
          pushFactory.create(db, admin.getIdent(), testRepo, "first subject", file, contents);
      PushOneCommit.Result r = push.to("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment 1", false);
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertThat(comment).isEqualTo(infoToInput(file).apply(actual));
      assertThat(comment)
          .isEqualTo(infoToInput(file).apply(getPublishedComment(changeId, revId, actual.id)));
    }
  }

  @Test
  public void postCommentWithReply() throws Exception {
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push =
          pushFactory.create(db, admin.getIdent(), testRepo, "first subject", file, contents);
      PushOneCommit.Result r = push.to("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment 1", false);
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));

      input = new ReviewInput();
      comment = newComment(file, Side.REVISION, line, "comment 1 reply", false);
      comment.inReplyTo = actual.id;
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
      result = getPublishedComments(changeId, revId);
      actual = result.get(comment.path).get(1);
      assertThat(comment).isEqualTo(infoToInput(file).apply(actual));
      assertThat(comment)
          .isEqualTo(infoToInput(file).apply(getPublishedComment(changeId, revId, actual.id)));
    }
  }

  @Test
  public void postCommentWithUnresolved() throws Exception {
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push =
          pushFactory.create(db, admin.getIdent(), testRepo, "first subject", file, contents);
      PushOneCommit.Result r = push.to("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment 1", true);
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertThat(comment).isEqualTo(infoToInput(file).apply(actual));
      assertThat(comment)
          .isEqualTo(infoToInput(file).apply(getPublishedComment(changeId, revId, actual.id)));
    }
  }

  @Test
  public void postCommentOnMergeCommitChange() throws Exception {
    for (Integer line : lines) {
      String file = "foo";
      PushOneCommit.Result r = createMergeCommitChange("refs/for/master", file);
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput c1 = newComment(file, Side.REVISION, line, "ps-1", false);
      CommentInput c2 = newComment(file, Side.PARENT, line, "auto-merge of ps-1", false);
      CommentInput c3 = newCommentOnParent(file, 1, line, "parent-1 of ps-1");
      CommentInput c4 = newCommentOnParent(file, 2, line, "parent-2 of ps-1");
      input.comments = new HashMap<>();
      input.comments.put(file, ImmutableList.of(c1, c2, c3, c4));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      assertThat(Lists.transform(result.get(file), infoToInput(file)))
          .containsExactly(c1, c2, c3, c4);
    }

    // for the commit message comments on the auto-merge are not possible
    for (Integer line : lines) {
      String file = Patch.COMMIT_MSG;
      PushOneCommit.Result r = createMergeCommitChange("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput c1 = newComment(file, Side.REVISION, line, "ps-1", false);
      CommentInput c2 = newCommentOnParent(file, 1, line, "parent-1 of ps-1");
      CommentInput c3 = newCommentOnParent(file, 2, line, "parent-2 of ps-1");
      input.comments = new HashMap<>();
      input.comments.put(file, ImmutableList.of(c1, c2, c3));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      assertThat(Lists.transform(result.get(file), infoToInput(file))).containsExactly(c1, c2, c3);
    }
  }

  @Test
  public void postCommentOnCommitMessageOnAutoMerge() throws Exception {
    PushOneCommit.Result r = createMergeCommitChange("refs/for/master");
    ReviewInput input = new ReviewInput();
    CommentInput c = newComment(Patch.COMMIT_MSG, Side.PARENT, 0, "comment on auto-merge", false);
    input.comments = new HashMap<>();
    input.comments.put(Patch.COMMIT_MSG, ImmutableList.of(c));
    exception.expect(BadRequestException.class);
    exception.expectMessage("cannot comment on " + Patch.COMMIT_MSG + " on auto-merge");
    revision(r).review(input);
  }

  @Test
  public void listComments() throws Exception {
    String file = "file";
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, "first subject", file, "contents");
    PushOneCommit.Result r = push.to("refs/for/master");
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    assertThat(getPublishedComments(changeId, revId)).isEmpty();

    List<CommentInput> expectedComments = new ArrayList<>();
    for (Integer line : lines) {
      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment " + line, false);
      expectedComments.add(comment);
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
    }

    Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
    assertThat(result).isNotEmpty();
    List<CommentInfo> actualComments = result.get(file);
    assertThat(Lists.transform(actualComments, infoToInput(file)))
        .containsExactlyElementsIn(expectedComments);
  }

  @Test
  public void putDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      Timestamp origLastUpdated = r.getChange().change().getLastUpdatedOn();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      String path = "file1";
      DraftInput comment = newDraft(path, Side.REVISION, line, "comment 1");
      addDraft(changeId, revId, comment);
      Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertThat(comment).isEqualTo(infoToDraft(path).apply(actual));
      String uuid = actual.id;
      comment.message = "updated comment 1";
      updateDraft(changeId, revId, comment, uuid);
      result = getDraftComments(changeId, revId);
      actual = Iterables.getOnlyElement(result.get(comment.path));
      assertThat(comment).isEqualTo(infoToDraft(path).apply(actual));

      // Posting a draft comment doesn't cause lastUpdatedOn to change.
      assertThat(r.getChange().change().getLastUpdatedOn()).isEqualTo(origLastUpdated);
    }
  }

  @Test
  public void listDrafts() throws Exception {
    String file = "file";
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    assertThat(getDraftComments(changeId, revId)).isEmpty();

    List<DraftInput> expectedDrafts = new ArrayList<>();
    for (Integer line : lines) {
      DraftInput comment = newDraft(file, Side.REVISION, line, "comment " + line);
      expectedDrafts.add(comment);
      addDraft(changeId, revId, comment);
    }

    Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
    assertThat(result).isNotEmpty();
    List<CommentInfo> actualComments = result.get(file);
    assertThat(Lists.transform(actualComments, infoToDraft(file)))
        .containsExactlyElementsIn(expectedDrafts);
  }

  @Test
  public void getDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      String path = "file1";
      DraftInput comment = newDraft(path, Side.REVISION, line, "comment 1");
      CommentInfo returned = addDraft(changeId, revId, comment);
      CommentInfo actual = getDraftComment(changeId, revId, returned.id);
      assertThat(comment).isEqualTo(infoToDraft(path).apply(actual));
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
      assertThat(r.getChange().change().getLastUpdatedOn()).isEqualTo(origLastUpdated);
    }
  }

  @Test
  public void insertCommentsWithHistoricTimestamp() throws Exception {
    Timestamp timestamp = new Timestamp(0);
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push =
          pushFactory.create(db, admin.getIdent(), testRepo, "first subject", file, contents);
      PushOneCommit.Result r = push.to("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      Timestamp origLastUpdated = r.getChange().change().getLastUpdatedOn();

      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment 1", false);
      comment.updated = timestamp;
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      ChangeResource changeRsrc =
          changes.get().parse(TopLevelResource.INSTANCE, IdString.fromDecoded(changeId));
      RevisionResource revRsrc = revisions.parse(changeRsrc, IdString.fromDecoded(revId));
      postReview.get().apply(batchUpdateFactory, revRsrc, input, timestamp);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      CommentInput ci = infoToInput(file).apply(actual);
      ci.updated = comment.updated;
      assertThat(comment).isEqualTo(ci);
      assertThat(actual.updated).isEqualTo(gApi.changes().id(r.getChangeId()).info().created);

      // Updating historic comments doesn't cause lastUpdatedOn to regress.
      assertThat(r.getChange().change().getLastUpdatedOn()).isEqualTo(origLastUpdated);
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
    addComment(r1, "nit: trailing whitespace", true, false, null);
    result = getPublishedComments(changeId, revId);
    assertThat(result.get(FILE_NAME)).hasSize(2);

    PushOneCommit.Result r2 =
        pushFactory
            .create(db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "content")
            .to("refs/for/master");
    changeId = r2.getChangeId();
    revId = r2.getCommit().getName();
    addComment(r2, "nit: trailing whitespace", true, false, null);
    result = getPublishedComments(changeId, revId);
    assertThat(result.get(FILE_NAME)).hasSize(1);
  }

  @Test
  public void listChangeDrafts() throws Exception {
    PushOneCommit.Result r1 = createChange();

    PushOneCommit.Result r2 =
        pushFactory
            .create(
                db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "new content", r1.getChangeId())
            .to("refs/for/master");

    setApiUser(admin);
    addDraft(
        r1.getChangeId(),
        r1.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "nit: trailing whitespace"));
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "typo: content"));

    setApiUser(user);
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "+1, please fix"));

    setApiUser(admin);
    Map<String, List<CommentInfo>> actual = gApi.changes().id(r1.getChangeId()).drafts();
    assertThat(actual.keySet()).containsExactly(FILE_NAME);
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

    PushOneCommit.Result r2 =
        pushFactory
            .create(
                db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "new cntent", r1.getChangeId())
            .to("refs/for/master");

    addComment(r1, "nit: trailing whitespace");
    addComment(r2, "typo: content");

    Map<String, List<CommentInfo>> actual = gApi.changes().id(r2.getChangeId()).comments();
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
      DraftInput comment = newDraft("file1", Side.REVISION, line, "comment 1");
      addDraft(changeId, revId, comment);
      assertThat(gApi.changes().query("change:" + changeId + " has:draft").get()).hasSize(1);
    }
  }

  @Test
  public void publishCommentsAllRevisions() throws Exception {
    PushOneCommit.Result r1 = createChange();

    PushOneCommit.Result r2 =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                testRepo,
                SUBJECT,
                FILE_NAME,
                "new\ncntent\n",
                r1.getChangeId())
            .to("refs/for/master");

    addDraft(
        r1.getChangeId(),
        r1.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "nit: trailing whitespace"));
    addDraft(
        r1.getChangeId(),
        r1.getCommit().getName(),
        newDraft(FILE_NAME, Side.PARENT, 2, "what happened to this?"));
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "join lines"));
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 2, "typo: content"));
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.PARENT, 1, "comment 1 on base"));
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.PARENT, 2, "comment 2 on base"));

    PushOneCommit.Result other = createChange();
    // Drafts on other changes aren't returned.
    addDraft(
        other.getChangeId(),
        other.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "unrelated comment"));

    setApiUser(admin);
    // Drafts by other users aren't returned.
    addDraft(
        r2.getChangeId(), r2.getCommit().getName(), newDraft(FILE_NAME, Side.REVISION, 2, "oops"));
    setApiUser(user);

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
    reviewInput.message = "comments";
    gApi.changes().id(r2.getChangeId()).current().review(reviewInput);

    assertThat(gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).drafts())
        .isEmpty();
    Map<String, List<CommentInfo>> ps1Map =
        gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).comments();
    assertThat(ps1Map.keySet()).containsExactly(FILE_NAME);
    List<CommentInfo> ps1List = ps1Map.get(FILE_NAME);
    assertThat(ps1List).hasSize(2);
    assertThat(ps1List.get(0).message).isEqualTo("what happened to this?");
    assertThat(ps1List.get(0).side).isEqualTo(Side.PARENT);
    assertThat(ps1List.get(1).message).isEqualTo("nit: trailing whitespace");
    assertThat(ps1List.get(1).side).isNull();

    assertThat(gApi.changes().id(r2.getChangeId()).revision(r2.getCommit().name()).drafts())
        .isEmpty();
    Map<String, List<CommentInfo>> ps2Map =
        gApi.changes().id(r2.getChangeId()).revision(r2.getCommit().name()).comments();
    assertThat(ps2Map.keySet()).containsExactly(FILE_NAME);
    List<CommentInfo> ps2List = ps2Map.get(FILE_NAME);
    assertThat(ps2List).hasSize(4);
    assertThat(ps2List.get(0).message).isEqualTo("comment 1 on base");
    assertThat(ps2List.get(1).message).isEqualTo("comment 2 on base");
    assertThat(ps2List.get(2).message).isEqualTo("join lines");
    assertThat(ps2List.get(3).message).isEqualTo("typo: content");

    List<Message> messages = email.getMessages(r2.getChangeId(), "comment");
    assertThat(messages).hasSize(1);
    String url = canonicalWebUrl.get();
    int c = r1.getChange().getId().get();
    assertThat(extractComments(messages.get(0).body()))
        .isEqualTo(
            "Patch Set 2:\n"
                + "\n"
                + "(6 comments)\n"
                + "\n"
                + "comments\n"
                + "\n"
                + url
                + "#/c/"
                + c
                + "/1/a.txt\n"
                + "File a.txt:\n"
                + "\n"
                + url
                + "#/c/"
                + c
                + "/1/a.txt@a2\n"
                + "PS1, Line 2: \n"
                + "what happened to this?\n"
                + "\n"
                + "\n"
                + url
                + "#/c/"
                + c
                + "/1/a.txt@1\n"
                + "PS1, Line 1: ew\n"
                + "nit: trailing whitespace\n"
                + "\n"
                + "\n"
                + url
                + "#/c/"
                + c
                + "/2/a.txt\n"
                + "File a.txt:\n"
                + "\n"
                + url
                + "#/c/"
                + c
                + "/2/a.txt@a1\n"
                + "PS2, Line 1: \n"
                + "comment 1 on base\n"
                + "\n"
                + "\n"
                + url
                + "#/c/"
                + c
                + "/2/a.txt@a2\n"
                + "PS2, Line 2: \n"
                + "comment 2 on base\n"
                + "\n"
                + "\n"
                + url
                + "#/c/"
                + c
                + "/2/a.txt@1\n"
                + "PS2, Line 1: ew\n"
                + "join lines\n"
                + "\n"
                + "\n"
                + url
                + "#/c/"
                + c
                + "/2/a.txt@2\n"
                + "PS2, Line 2: nten\n"
                + "typo: content\n"
                + "\n"
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

    List<CommentInfo> comments = gApi.changes().id(r.getChangeId()).current().commentsAsList();
    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).tag).isEqualTo("tag1");

    DraftInput draft = new DraftInput();
    draft.line = 2;
    draft.message = "draft comment";
    draft.path = FILE_NAME;
    draft.tag = "tag2";
    addDraft(r.getChangeId(), r.getCommit().name(), draft);

    List<CommentInfo> drafts = gApi.changes().id(r.getChangeId()).current().draftsAsList();
    assertThat(drafts).hasSize(1);
    assertThat(drafts.get(0).tag).isEqualTo("tag2");
  }

  @Test
  public void queryChangesWithUnresolvedCommentCount() throws Exception {
    // PS1 has three comments in three different threads, PS2 has one comment in one thread.
    PushOneCommit.Result result = createChange("change 1", FILE_NAME, "content 1");
    String changeId1 = result.getChangeId();
    addComment(result, "comment 1", false, true, null);
    addComment(result, "comment 2", false, null, null);
    addComment(result, "comment 3", false, false, null);
    PushOneCommit.Result result2 = amendChange(changeId1);
    addComment(result2, "comment4", false, true, null);

    // Change2 has two comments in one thread, the first is unresolved and the second is resolved.
    result = createChange("change 2", FILE_NAME, "content 2");
    String changeId2 = result.getChangeId();
    addComment(result, "comment 1", false, true, null);
    Map<String, List<CommentInfo>> comments =
        getPublishedComments(changeId2, result.getCommit().name());
    assertThat(comments).hasSize(1);
    assertThat(comments.get(FILE_NAME)).hasSize(1);
    addComment(result, "comment 2", false, false, comments.get(FILE_NAME).get(0).id);

    // Change3 has two comments in one thread, the first is resolved, the second is unresolved.
    result = createChange("change 3", FILE_NAME, "content 3");
    String changeId3 = result.getChangeId();
    addComment(result, "comment 1", false, false, null);
    comments = getPublishedComments(result.getChangeId(), result.getCommit().name());
    assertThat(comments).hasSize(1);
    assertThat(comments.get(FILE_NAME)).hasSize(1);
    addComment(result, "comment 2", false, true, comments.get(FILE_NAME).get(0).id);

    AcceptanceTestRequestScope.Context ctx = disableDb();
    try {
      ChangeInfo changeInfo1 = Iterables.getOnlyElement(query(changeId1));
      ChangeInfo changeInfo2 = Iterables.getOnlyElement(query(changeId2));
      ChangeInfo changeInfo3 = Iterables.getOnlyElement(query(changeId3));
      assertThat(changeInfo1.unresolvedCommentCount).isEqualTo(2);
      assertThat(changeInfo2.unresolvedCommentCount).isEqualTo(0);
      assertThat(changeInfo3.unresolvedCommentCount).isEqualTo(1);
    } finally {
      enableDb(ctx);
    }
  }

  @Test
  public void deleteCommentCannotBeAppliedByUser() throws Exception {
    PushOneCommit.Result result = createChange();
    CommentInput targetComment = addComment(result.getChangeId(), "My password: abc123");

    Map<String, List<CommentInfo>> commentsMap =
        getPublishedComments(result.getChangeId(), result.getCommit().name());

    assertThat(commentsMap).hasSize(1);
    assertThat(commentsMap.get(FILE_NAME)).hasSize(1);

    String uuid = commentsMap.get(targetComment.path).get(0).id;
    DeleteCommentInput input = new DeleteCommentInput("contains confidential information");

    setApiUser(user);
    exception.expect(AuthException.class);
    gApi.changes().id(result.getChangeId()).current().comment(uuid).delete(input);
  }

  @Test
  public void deleteCommentByRewritingCommitHistory() throws Exception {
    // Creates the following commit history on the meta branch of the test change. Then tries to
    // delete the comments one by one, which will rewrite most of the commits on the 'meta' branch.
    // Commits will be rewritten N times for N added comments. After each deletion, the meta branch
    // should keep its previous state except that the target comment's message should be updated.

    // 1st commit: Create PS1.
    PushOneCommit.Result result1 = createChange(SUBJECT, "a.txt", "a");
    Change.Id id = result1.getChange().getId();
    String changeId = result1.getChangeId();
    String ps1 = result1.getCommit().name();

    // 2nd commit: Add (c1) to PS1.
    CommentInput c1 = newComment("a.txt", "comment 1");
    addComments(changeId, ps1, c1);

    // 3rd commit: Add (c2, c3) to PS1.
    CommentInput c2 = newComment("a.txt", "comment 2");
    CommentInput c3 = newComment("a.txt", "comment 3");
    addComments(changeId, ps1, c2, c3);

    // 4th commit: Add (c4) to PS1.
    CommentInput c4 = newComment("a.txt", "comment 4");
    addComments(changeId, ps1, c4);

    // 5th commit: Create PS2.
    PushOneCommit.Result result2 = amendChange(changeId, "refs/for/master", "b.txt", "b");
    String ps2 = result2.getCommit().name();

    // 6th commit: Add (c5) to PS1.
    CommentInput c5 = newComment("a.txt", "comment 5");
    addComments(changeId, ps1, c5);

    // 7th commit: Add (c6) to PS2.
    CommentInput c6 = newComment("b.txt", "comment 6");
    addComments(changeId, ps2, c6);

    // 8th commit: Create PS3.
    PushOneCommit.Result result3 = amendChange(changeId);
    String ps3 = result3.getCommit().name();

    // 9th commit: Create PS4.
    PushOneCommit.Result result4 = amendChange(changeId, "refs/for/master", "c.txt", "c");
    String ps4 = result4.getCommit().name();

    // 10th commit: Add (c7, c8) to PS4.
    CommentInput c7 = newComment("c.txt", "comment 7");
    CommentInput c8 = newComment("b.txt", "comment 8");
    addComments(changeId, ps4, c7, c8);

    // 11th commit: Add (c9) to PS2.
    CommentInput c9 = newComment("b.txt", "comment 9");
    addComments(changeId, ps2, c9);

    List<CommentInfo> commentsBeforeDelete = getChangeSortedComments(changeId);
    assertThat(commentsBeforeDelete).hasSize(9);
    // PS1 has comments [c1, c2, c3, c4, c5].
    assertThat(getRevisionComments(changeId, ps1)).hasSize(5);
    // PS2 has comments [c6, c9].
    assertThat(getRevisionComments(changeId, ps2)).hasSize(2);
    // PS3 has no comment.
    assertThat(getRevisionComments(changeId, ps3)).hasSize(0);
    // PS4 has comments [c7, c8].
    assertThat(getRevisionComments(changeId, ps4)).hasSize(2);

    setApiUser(admin);
    for (int i = 0; i < commentsBeforeDelete.size(); i++) {
      List<RevCommit> commitsBeforeDelete = new ArrayList<>();
      if (notesMigration.commitChangeWrites()) {
        commitsBeforeDelete = getCommits(id);
      }

      CommentInfo comment = commentsBeforeDelete.get(i);
      String uuid = comment.id;
      int patchSet = comment.patchSet;
      // 'oldComment' has some fields unset compared with 'comment'.
      CommentInfo oldComment = gApi.changes().id(changeId).revision(patchSet).comment(uuid).get();

      DeleteCommentInput input = new DeleteCommentInput("delete comment " + uuid);
      CommentInfo updatedComment =
          gApi.changes().id(changeId).revision(patchSet).comment(uuid).delete(input);

      String expectedMsg =
          String.format("Comment removed by: %s; Reason: %s", admin.fullName, input.reason);
      assertThat(updatedComment.message).isEqualTo(expectedMsg);
      oldComment.message = expectedMsg;
      assertThat(updatedComment).isEqualTo(oldComment);

      // Check the NoteDb state after the deletion.
      if (notesMigration.commitChangeWrites()) {
        assertMetaBranchCommitsAfterRewriting(commitsBeforeDelete, id, uuid, expectedMsg);
      }

      comment.message = expectedMsg;
      commentsBeforeDelete.set(i, comment);
      List<CommentInfo> commentsAfterDelete = getChangeSortedComments(changeId);
      assertThat(commentsAfterDelete).isEqualTo(commentsBeforeDelete);
    }

    // Make sure that comments can still be added correctly.
    CommentInput c10 = newComment("a.txt", "comment 10");
    CommentInput c11 = newComment("b.txt", "comment 11");
    CommentInput c12 = newComment("a.txt", "comment 12");
    CommentInput c13 = newComment("c.txt", "comment 13");
    addComments(changeId, ps1, c10);
    addComments(changeId, ps2, c11);
    addComments(changeId, ps3, c12);
    addComments(changeId, ps4, c13);

    assertThat(getChangeSortedComments(changeId)).hasSize(13);
    assertThat(getRevisionComments(changeId, ps1)).hasSize(6);
    assertThat(getRevisionComments(changeId, ps2)).hasSize(3);
    assertThat(getRevisionComments(changeId, ps3)).hasSize(1);
    assertThat(getRevisionComments(changeId, ps4)).hasSize(3);
  }

  @Test
  public void deleteOneCommentMultipleTimes() throws Exception {
    PushOneCommit.Result result = createChange();
    Change.Id id = result.getChange().getId();
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput c1 = newComment(FILE_NAME, "comment 1");
    CommentInput c2 = newComment(FILE_NAME, "comment 2");
    CommentInput c3 = newComment(FILE_NAME, "comment 3");
    addComments(changeId, ps1, c1);
    addComments(changeId, ps1, c2);
    addComments(changeId, ps1, c3);

    List<CommentInfo> commentsBeforeDelete = getChangeSortedComments(changeId);
    assertThat(commentsBeforeDelete).hasSize(3);
    Optional<CommentInfo> targetComment =
        commentsBeforeDelete.stream().filter(c -> c.message.equals("comment 2")).findFirst();
    assertThat(targetComment).isPresent();
    String uuid = targetComment.get().id;
    CommentInfo oldComment = gApi.changes().id(changeId).revision(ps1).comment(uuid).get();

    List<RevCommit> commitsBeforeDelete = new ArrayList<>();
    if (notesMigration.commitChangeWrites()) {
      commitsBeforeDelete = getCommits(id);
    }

    setApiUser(admin);
    for (int i = 0; i < 3; i++) {
      DeleteCommentInput input = new DeleteCommentInput("delete comment 2, iteration: " + i);
      gApi.changes().id(changeId).revision(ps1).comment(uuid).delete(input);
    }

    CommentInfo updatedComment = gApi.changes().id(changeId).revision(ps1).comment(uuid).get();
    String expectedMsg =
        String.format(
            "Comment removed by: %s; Reason: %s", admin.fullName, "delete comment 2, iteration: 2");
    assertThat(updatedComment.message).isEqualTo(expectedMsg);
    oldComment.message = expectedMsg;
    assertThat(updatedComment).isEqualTo(oldComment);

    if (notesMigration.commitChangeWrites()) {
      assertMetaBranchCommitsAfterRewriting(commitsBeforeDelete, id, uuid, expectedMsg);
    }
    assertThat(getChangeSortedComments(changeId)).hasSize(3);
  }

  @Test
  public void jsonCommentHasLegacyFormatFalse() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    assertThat(noteUtil.getWriteJson()).isTrue();

    PushOneCommit.Result result = createChange();
    Change.Id changeId = result.getChange().getId();
    addComment(result.getChangeId(), "comment");

    Collection<com.google.gerrit.reviewdb.client.Comment> comments =
        notesFactory.createChecked(db, project, changeId).getComments().values();
    assertThat(comments).hasSize(1);
    com.google.gerrit.reviewdb.client.Comment comment = comments.iterator().next();
    assertThat(comment.message).isEqualTo("comment");
    assertThat(comment.legacyFormat).isFalse();
  }

  private List<CommentInfo> getChangeSortedComments(String changeId) throws Exception {
    List<CommentInfo> comments = new ArrayList<>();
    Map<String, List<CommentInfo>> commentsMap = getPublishedComments(changeId);
    for (Entry<String, List<CommentInfo>> e : commentsMap.entrySet()) {
      for (CommentInfo c : e.getValue()) {
        c.path = e.getKey(); // Set the comment's path field.
        comments.add(c);
      }
    }
    comments.sort(Comparator.comparing(c -> c.id));
    return comments;
  }

  private List<CommentInfo> getRevisionComments(String changeId, String revId) throws Exception {
    return getPublishedComments(changeId, revId)
        .values()
        .stream()
        .flatMap(List::stream)
        .collect(toList());
  }

  private CommentInput addComment(String changeId, String message) throws Exception {
    ReviewInput input = new ReviewInput();
    CommentInput comment = newComment(FILE_NAME, Side.REVISION, 0, message, false);
    input.comments = ImmutableMap.of(comment.path, Lists.newArrayList(comment));
    gApi.changes().id(changeId).current().review(input);
    return comment;
  }

  private void addComments(String changeId, String revision, CommentInput... commentInputs)
      throws Exception {
    ReviewInput input = new ReviewInput();
    input.comments = Arrays.stream(commentInputs).collect(groupingBy(c -> c.path));
    gApi.changes().id(changeId).revision(revision).review(input);
  }

  private List<RevCommit> getCommits(Change.Id changeId) throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      Ref metaRef = repo.exactRef(RefNames.changeMetaRef(changeId));
      revWalk.markStart(revWalk.parseCommit(metaRef.getObjectId()));
      return Lists.newArrayList(revWalk);
    }
  }

  /**
   * All the commits, which contain the target comment before, should still contain the comment with
   * the updated message. All the other metas of the commits should be exactly the same.
   */
  private void assertMetaBranchCommitsAfterRewriting(
      List<RevCommit> beforeDelete,
      Change.Id changeId,
      String targetCommentUuid,
      String expectedMessage)
      throws Exception {
    List<RevCommit> afterDelete = getCommits(changeId);
    assertThat(afterDelete).hasSize(beforeDelete.size());

    try (Repository repo = repoManager.openRepository(project);
        ObjectReader reader = repo.newObjectReader()) {
      for (int i = 0; i < beforeDelete.size(); i++) {
        RevCommit commitBefore = beforeDelete.get(i);
        RevCommit commitAfter = afterDelete.get(i);

        Map<String, com.google.gerrit.reviewdb.client.Comment> commentMapBefore =
            DeleteCommentRewriter.getPublishedComments(
                noteUtil, changeId, reader, NoteMap.read(reader, commitBefore));
        Map<String, com.google.gerrit.reviewdb.client.Comment> commentMapAfter =
            DeleteCommentRewriter.getPublishedComments(
                noteUtil, changeId, reader, NoteMap.read(reader, commitAfter));

        if (commentMapBefore.containsKey(targetCommentUuid)) {
          assertThat(commentMapAfter).containsKey(targetCommentUuid);
          com.google.gerrit.reviewdb.client.Comment comment =
              commentMapAfter.get(targetCommentUuid);
          assertThat(comment.message).isEqualTo(expectedMessage);
          comment.message = commentMapBefore.get(targetCommentUuid).message;
          commentMapAfter.put(targetCommentUuid, comment);
          assertThat(commentMapAfter).isEqualTo(commentMapBefore);
        } else {
          assertThat(commentMapAfter).doesNotContainKey(targetCommentUuid);
        }

        // Other metas should be exactly the same.
        assertThat(commitAfter.getFullMessage()).isEqualTo(commitBefore.getFullMessage());
        assertThat(commitAfter.getCommitterIdent()).isEqualTo(commitBefore.getCommitterIdent());
        assertThat(commitAfter.getAuthorIdent()).isEqualTo(commitBefore.getAuthorIdent());
        assertThat(commitAfter.getEncoding()).isEqualTo(commitBefore.getEncoding());
        assertThat(commitAfter.getEncodingName()).isEqualTo(commitBefore.getEncodingName());
      }
    }
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

  private void addComment(PushOneCommit.Result r, String message) throws Exception {
    addComment(r, message, false, false, null);
  }

  private void addComment(
      PushOneCommit.Result r,
      String message,
      boolean omitDuplicateComments,
      Boolean unresolved,
      String inReplyTo)
      throws Exception {
    CommentInput c = new CommentInput();
    c.line = 1;
    c.message = message;
    c.path = FILE_NAME;
    c.unresolved = unresolved;
    c.inReplyTo = inReplyTo;
    ReviewInput in = newInput(c);
    in.omitDuplicateComments = omitDuplicateComments;
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(in);
  }

  private CommentInfo addDraft(String changeId, String revId, DraftInput in) throws Exception {
    return gApi.changes().id(changeId).revision(revId).createDraft(in).get();
  }

  private void updateDraft(String changeId, String revId, DraftInput in, String uuid)
      throws Exception {
    gApi.changes().id(changeId).revision(revId).draft(uuid).update(in);
  }

  private void deleteDraft(String changeId, String revId, String uuid) throws Exception {
    gApi.changes().id(changeId).revision(revId).draft(uuid).delete();
  }

  private CommentInfo getPublishedComment(String changeId, String revId, String uuid)
      throws Exception {
    return gApi.changes().id(changeId).revision(revId).comment(uuid).get();
  }

  private Map<String, List<CommentInfo>> getPublishedComments(String changeId, String revId)
      throws Exception {
    return gApi.changes().id(changeId).revision(revId).comments();
  }

  private Map<String, List<CommentInfo>> getDraftComments(String changeId, String revId)
      throws Exception {
    return gApi.changes().id(changeId).revision(revId).drafts();
  }

  private Map<String, List<CommentInfo>> getPublishedComments(String changeId) throws Exception {
    return gApi.changes().id(changeId).comments();
  }

  private CommentInfo getDraftComment(String changeId, String revId, String uuid) throws Exception {
    return gApi.changes().id(changeId).revision(revId).draft(uuid).get();
  }

  private static CommentInput newComment(String file, String message) {
    return newComment(file, Side.REVISION, 0, message, false);
  }

  private static CommentInput newComment(
      String path, Side side, int line, String message, Boolean unresolved) {
    CommentInput c = new CommentInput();
    return populate(c, path, side, null, line, message, unresolved);
  }

  private static CommentInput newCommentOnParent(
      String path, int parent, int line, String message) {
    CommentInput c = new CommentInput();
    return populate(c, path, Side.PARENT, Integer.valueOf(parent), line, message, false);
  }

  private DraftInput newDraft(String path, Side side, int line, String message) {
    DraftInput d = new DraftInput();
    return populate(d, path, side, null, line, message, false);
  }

  private DraftInput newDraftOnParent(String path, int parent, int line, String message) {
    DraftInput d = new DraftInput();
    return populate(d, path, Side.PARENT, Integer.valueOf(parent), line, message, false);
  }

  private static <C extends Comment> C populate(
      C c, String path, Side side, Integer parent, int line, String message, Boolean unresolved) {
    c.path = path;
    c.side = side;
    c.parent = parent;
    c.line = line != 0 ? line : null;
    c.message = message;
    c.unresolved = unresolved;
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

  private static Function<CommentInfo, CommentInput> infoToInput(String path) {
    return infoToInput(path, CommentInput::new);
  }

  private static Function<CommentInfo, DraftInput> infoToDraft(String path) {
    return infoToInput(path, DraftInput::new);
  }

  private static <I extends Comment> Function<CommentInfo, I> infoToInput(
      String path, Supplier<I> supplier) {
    return info -> {
      I i = supplier.get();
      i.path = path;
      copy(info, i);
      return i;
    };
  }

  private static void copy(Comment from, Comment to) {
    to.side = from.side == null ? Side.REVISION : from.side;
    to.parent = from.parent;
    to.line = from.line;
    to.message = from.message;
    to.range = from.range;
    to.unresolved = from.unresolved;
    to.inReplyTo = from.inReplyTo;
  }
}
