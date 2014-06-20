// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.revision;

import static com.google.gerrit.acceptance.PushOneCommit.FILE_CONTENT;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.DraftApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Patch;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NoHttpd
public class RevisionIT extends AbstractDaemonTest {

  private TestAccount admin2;

  @Before
  public void setUp() throws Exception {
    admin2 = accounts.admin2();
  }

  @Test
  public void reviewTriplet() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
  }

  @Test
  public void reviewCurrent() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(ReviewInput.approve());
  }

  @Test
  public void reviewNumber() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(1)
        .review(ReviewInput.approve());

    r = updateChange(r, "new content");
    gApi.changes()
        .id(r.getChangeId())
        .revision(2)
        .review(ReviewInput.approve());
  }

  @Test
  public void submit() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .current()
        .review(ReviewInput.approve());
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .current()
        .submit();
  }

  @Test(expected = AuthException.class)
  public void submitOnBehalfOf() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .current()
        .review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = admin2.email;
    in.waitForMerge = true;
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .current()
        .submit(in);
  }

  @Test
  public void deleteDraft() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createDraft();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .delete();
  }

  @Test
  public void cherryPick() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects()
        .name(project.get())
        .branch(in.destination)
        .create(new BranchInput());
    ChangeApi cApi = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .cherryPick(in);
    cApi.current()
        .review(ReviewInput.approve());
    cApi.current()
        .submit();
  }

  @Test
  public void canRebase()
      throws GitAPIException, IOException, RestApiException, Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    PushOneCommit.Result r1 = push.to(git, "refs/for/master");
    merge(r1);

    push = pushFactory.create(db, admin.getIdent());
    PushOneCommit.Result r2 = push.to(git, "refs/for/master");
    assertFalse(gApi.changes()
        .id(r2.getChangeId())
        .revision(r2.getCommit().name())
        .canRebase());
    merge(r2);

    git.checkout().setName(r1.getCommit().name()).call();
    push = pushFactory.create(db, admin.getIdent());
    PushOneCommit.Result r3 = push.to(git, "refs/for/master");

    assertTrue(gApi.changes()
        .id(r3.getChangeId())
        .revision(r3.getCommit().name())
        .canRebase());
  }

  @Test
  public void files() throws Exception {
    PushOneCommit.Result r = createChange();
    assertTrue(Iterables.all(gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .files()
        .keySet(), new Predicate<String>() {
            @Override
            public boolean apply(String file) {
              return file.matches(FILE_NAME + '|' + Patch.COMMIT_MSG);
            }
         }));
  }

  @Test
  public void diff() throws Exception {
    PushOneCommit.Result r = createChange();
    DiffInfo diff = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .file(FILE_NAME)
        .diff();
    assertNull(diff.metaA);
    assertEquals(1, diff.metaB.lines.intValue());
  }

  @Test
  public void content() throws Exception {
    PushOneCommit.Result r = createChange();
    assertEquals(FILE_CONTENT, gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .file(FILE_NAME)
        .content());
  }

  @Test
  public void drafts() throws Exception {
    PushOneCommit.Result r = createChange();
    DraftInput in = new DraftInput();
    in.line = 1;
    in.message = "nit: trailing hitespace";
    in.path = FILE_NAME;
    DraftApi draftApi = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .createDraft(in);
    assertEquals(in.message, draftApi.get().message);
    assertEquals(in.message, gApi.changes()
       .id(r.getChangeId())
       .revision(r.getCommit().name())
       .draft(draftApi.get().id)
       .get()
       .message);
    assertEquals(1, gApi.changes()
       .id(r.getChangeId())
       .revision(r.getCommit().name())
       .drafts().size());

    draftApi.delete();
    assertEquals(0, gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .drafts().size());
  }

  @Test
  public void comments() throws Exception {
    PushOneCommit.Result r = createChange();
    CommentInput in = new CommentInput();
    in.line = 1;
    in.message = "nit: trailing hitespace";
    in.path = FILE_NAME;
    ReviewInput reviewInput = new ReviewInput();
    Map<String, List<CommentInput>> comments = new HashMap<>();
    comments.put(FILE_NAME, Collections.singletonList(in));
    reviewInput.comments = comments;
    reviewInput.message = "comment test";
    gApi.changes()
       .id(r.getChangeId())
       .current()
       .review(reviewInput);

    Map<String, List<CommentInfo>> out = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .comments();
    assertEquals(1, out.size());
    assertEquals(in.message, Iterables.getOnlyElement(
        out.get(FILE_NAME)).message);
  }

  protected RevisionApi revision(PushOneCommit.Result r) throws Exception {
    return gApi.changes()
        .id(r.getChangeId())
        .current();
  }

  private void merge(PushOneCommit.Result r) throws Exception {
    revision(r).review(ReviewInput.approve());
    revision(r).submit();
  }

  private PushOneCommit.Result updateChange(PushOneCommit.Result r,
      String content) throws GitAPIException, IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(),
        "test commit", "a.txt", content, r.getChangeId());
    return push.to(git, "refs/for/master");
  }

  private PushOneCommit.Result createDraft() throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, "refs/drafts/master");
  }
}
