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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

@NoHttpd
public class RevisionIT extends AbstractDaemonTest {

  private TestAccount admin2;

  @Before
  public void setUp() throws Exception {
    admin2 = accounts.admin2();
  }

  @Test
  public void reviewTriplet() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
  }

  @Test
  public void reviewCurrent() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(ReviewInput.approve());
  }

  @Test
  public void reviewNumber() throws Exception {
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
  public void submit() throws Exception {
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
  public void submitOnBehalfOf() throws Exception {
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
  public void deleteDraft() throws Exception {
    PushOneCommit.Result r = createDraft();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .delete();
  }

  @Test
  public void cherryPick() throws Exception {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects()
        .name(project.get())
        .branch(in.destination)
        .create(new BranchInput());
    ChangeApi orig = gApi.changes()
        .id("p~master~" + r.getChangeId());

    assertEquals(1, orig.get().messages.size());
    ChangeApi cherry = orig.revision(r.getCommit().name())
        .cherryPick(in);
    assertEquals(2, orig.get().messages.size());

    assertTrue(cherry.get().subject.contains(in.message));
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();
  }

  @Test
  public void cherryPickIdenticalTree() throws Exception {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects()
        .name(project.get())
        .branch(in.destination)
        .create(new BranchInput());
    ChangeApi orig = gApi.changes()
        .id("p~master~" + r.getChangeId());

    assertEquals(1, orig.get().messages.size());
    ChangeApi cherry = orig.revision(r.getCommit().name())
        .cherryPick(in);
    assertEquals(2, orig.get().messages.size());

    assertTrue(cherry.get().subject.contains(in.message));
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();

    try {
      orig.revision(r.getCommit().name()).cherryPick(in);
      fail("Cherry-pick identical tree error expected");
    } catch (RestApiException e) {
      assertEquals("Cherry pick failed: identical tree", e.getMessage());
    }
  }

  @Test
  public void cherryPickConflict() throws Exception {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects()
        .name(project.get())
        .branch(in.destination)
        .create(new BranchInput());

    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME, "another content");
    push.to(git, "refs/heads/foo");

    ChangeApi orig = gApi.changes().id("p~master~" + r.getChangeId());
    assertEquals(1, orig.get().messages.size());

    try {
      orig.revision(r.getCommit().name()).cherryPick(in);
      fail("Cherry-pick merge conflict error expected");
    } catch (RestApiException e) {
      assertEquals("Cherry pick failed: merge conflict", e.getMessage());
    }
  }

  @Test
  public void canRebase() throws Exception {
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
  public void setUnsetReviewedFlag() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    PushOneCommit.Result r = push.to(git, "refs/for/master");

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .setReviewed(PushOneCommit.FILE_NAME, true);

    assertEquals(PushOneCommit.FILE_NAME,
        Iterables.getOnlyElement(
            gApi.changes()
                .id(r.getChangeId())
                .current()
                .reviewed()));

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .setReviewed(PushOneCommit.FILE_NAME, false);

    assertTrue(
        gApi.changes()
            .id(r.getChangeId())
            .current()
            .reviewed()
            .isEmpty());
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
