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

import static com.google.common.truth.Truth.assertThat;
import static org.eclipse.jgit.lib.Constants.HEAD;
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
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.extensions.common.SubmitType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
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
    PushOneCommit.Result r = pushTo("refs/for/master%topic=someTopic");
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects()
        .name(project.get())
        .branch(in.destination)
        .create(new BranchInput());
    ChangeApi orig = gApi.changes()
        .id("p~master~" + r.getChangeId());

    assertThat(orig.get().messages).hasSize(1);
    ChangeApi cherry = orig.revision(r.getCommit().name())
        .cherryPick(in);
    assertThat(orig.get().messages).hasSize(2);

    assertThat(cherry.get().subject).contains(in.message);
    assertThat(cherry.get().topic).isEqualTo("someTopic");
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

    assertThat(orig.get().messages).hasSize(1);
    ChangeApi cherry = orig.revision(r.getCommit().name())
        .cherryPick(in);
    assertThat(orig.get().messages).hasSize(2);

    assertThat(cherry.get().subject).contains(in.message);
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();

    try {
      orig.revision(r.getCommit().name()).cherryPick(in);
      fail("Cherry-pick identical tree error expected");
    } catch (RestApiException e) {
      assertThat(e.getMessage()).isEqualTo("Cherry pick failed: identical tree");
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
    assertThat(orig.get().messages).hasSize(1);

    try {
      orig.revision(r.getCommit().name()).cherryPick(in);
      fail("Cherry-pick merge conflict error expected");
    } catch (RestApiException e) {
      assertThat(e.getMessage()).isEqualTo("Cherry pick failed: merge conflict");
    }
  }

  @Test
  public void canRebase() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    PushOneCommit.Result r1 = push.to(git, "refs/for/master");
    merge(r1);

    push = pushFactory.create(db, admin.getIdent());
    PushOneCommit.Result r2 = push.to(git, "refs/for/master");
    boolean canRebase = gApi.changes()
        .id(r2.getChangeId())
        .revision(r2.getCommit().name())
        .canRebase();
    assertThat(canRebase).isFalse();
    merge(r2);

    git.checkout().setName(r1.getCommit().name()).call();
    push = pushFactory.create(db, admin.getIdent());
    PushOneCommit.Result r3 = push.to(git, "refs/for/master");

    canRebase = gApi.changes()
        .id(r3.getChangeId())
        .revision(r3.getCommit().name())
        .canRebase();
    assertThat(canRebase).isTrue();
  }

  @Test
  public void setUnsetReviewedFlag() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    PushOneCommit.Result r = push.to(git, "refs/for/master");

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .setReviewed(PushOneCommit.FILE_NAME, true);

    assertThat(Iterables.getOnlyElement(
            gApi.changes()
                .id(r.getChangeId())
                .current()
                .reviewed())).isEqualTo(PushOneCommit.FILE_NAME);

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .setReviewed(PushOneCommit.FILE_NAME, false);

    assertThat(gApi.changes().id(r.getChangeId()).current().reviewed())
        .isEmpty();
  }

  @Test
  public void mergeable() throws Exception {
    ObjectId initial = git.getRepository().getRef(HEAD).getLeaf().getObjectId();

    PushOneCommit push1 =
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME, "push 1 content");

    PushOneCommit.Result r1 = push1.to(git, "refs/for/master");
    assertMergeable(r1.getChangeId(), true);
    merge(r1);

    // Reset HEAD to initial so the new change is a merge conflict.
    RefUpdate ru = git.getRepository().updateRef(HEAD);
    ru.setNewObjectId(initial);
    assertThat(ru.forceUpdate()).isEqualTo(RefUpdate.Result.FORCED);

    PushOneCommit push2 =
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME, "push 2 content");
    PushOneCommit.Result r2 = push2.to(git, "refs/for/master");
    assertMergeable(r2.getChangeId(), false);
  }

  private void assertMergeable(String id, boolean expected) throws Exception {
    MergeableInfo m = gApi.changes().id(id).current().mergeable();
    assertThat(m.mergeable).isEqualTo(expected);
    assertThat(m.submitType).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    assertThat(m.mergeableInto).isNull();
    ChangeInfo c = gApi.changes().id(id).info();
    assertThat(c.mergeable).isEqualTo(expected);
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
