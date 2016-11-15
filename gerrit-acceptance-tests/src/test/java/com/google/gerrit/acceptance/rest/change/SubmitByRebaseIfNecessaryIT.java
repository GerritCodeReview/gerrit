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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.getChangeId;
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class SubmitByRebaseIfNecessaryIT extends AbstractSubmit {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.REBASE_IF_NECESSARY;
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithFastForward() throws Exception {
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getId()).isEqualTo(change.getCommitId());
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertApproved(change.getChangeId());
    assertCurrentRevision(change.getChangeId(), 1, head);
    assertSubmitter(change.getChangeId(), 1);
    assertPersonEquals(admin.getIdent(), head.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), head.getCommitterIdent());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithRebase() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 =
        createChange("Change 2", "b.txt", "other content");
    submit(change2.getChangeId());
    assertRebase(testRepo, false);
    RevCommit head = getRemoteHead();
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertApproved(change2.getChangeId());
    assertCurrentRevision(change2.getChangeId(), 2, head);
    assertSubmitter(change2.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 2);
    assertPersonEquals(admin.getIdent(), head.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), head.getCommitterIdent());
  }

  @Test
  public void submitWithRebaseMergeCommit() throws Exception {
    /*
        *  (HEAD, origin/master, origin/HEAD) Merge changes X,Y
        |\
        | *   Merge branch 'master' into origin/master
        | |\
        | | * SHA Added a
        | |/
        * | Before
        |/
        * Initial empty repository
     */
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change1 = createChange("Added a", "a.txt", "");

    PushOneCommit change2Push = pushFactory.create(db, admin.getIdent(), testRepo,
        "Merge to master", "m.txt", "");
    change2Push.setParents(ImmutableList.of(initialHead, change1.getCommit()));
    PushOneCommit.Result change2 = change2Push.to("refs/for/master");

    testRepo.reset(initialHead);
    PushOneCommit.Result change3 = createChange("Before", "b.txt", "");

    approve(change3.getChangeId());
    submit(change3.getChangeId());

    approve(change1.getChangeId());
    approve(change2.getChangeId());
    submit(change2.getChangeId());

    RevCommit newHead = getRemoteHead();
    assertThat(newHead.getParentCount()).isEqualTo(2);

    RevCommit headParent1 = parse(newHead.getParent(0).getId());
    RevCommit headParent2 = parse(newHead.getParent(1).getId());

    assertThat(headParent1.getId()).isEqualTo(change3.getCommit().getId());
    assertThat(headParent1.getParentCount()).isEqualTo(1);
    assertThat(headParent1.getParent(0)).isEqualTo(initialHead);

    assertThat(headParent2.getId()).isEqualTo(change2.getCommit().getId());
    assertThat(headParent2.getParentCount()).isEqualTo(2);

    RevCommit headGrandparent1 = parse(headParent2.getParent(0).getId());
    RevCommit headGrandparent2 = parse(headParent2.getParent(1).getId());

    assertThat(headGrandparent1.getId()).isEqualTo(initialHead.getId());
    assertThat(headGrandparent2.getId()).isEqualTo(change1.getCommit().getId());
  }

  @Test
  public void submitMergeOfNonChangeBranchTip() throws Exception {
    RevCommit master = getRemoteHead(project, "master");
    PushOneCommit stableTip = pushFactory.create(db, admin.getIdent(), testRepo,
        "Tip of branch stable", "stable.txt", "");
    PushOneCommit.Result stable = stableTip.to("refs/heads/stable");
    PushOneCommit mergeCommit = pushFactory.create(db, admin.getIdent(),
        testRepo, "The merge commit", "merge.txt", "");
    mergeCommit.setParents(ImmutableList.of(master, stable.getCommit()));
    PushOneCommit.Result mergeReview = mergeCommit.to("refs/for/master");
    approve(mergeReview.getChangeId());
    submit(mergeReview.getChangeId());
  }

  private RevCommit parse(ObjectId id) throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit c = rw.parseCommit(id);
      rw.parseBody(c);
      return c;
    }
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge() throws Exception {
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "aaa\nbbb\nccc\n");
    submit(change.getChangeId());
    PushOneCommit.Result change2 =
        createChange("Change 2", "a.txt", "aaa\nbbb\nccc\nddd\n");
    submit(change2.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(change.getCommitId());
    PushOneCommit.Result change3 =
        createChange("Change 3", "a.txt", "bbb\nccc\n");
    submit(change3.getChangeId());
    assertRebase(testRepo, true);
    RevCommit head = getRemoteHead();
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertApproved(change3.getChangeId());
    assertCurrentRevision(change3.getChangeId(), 2, head);
    assertSubmitter(change3.getChangeId(), 1);
    assertSubmitter(change3.getChangeId(), 2);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge_Conflict() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 =
        createChange("Change 2", "a.txt", "other content");
    submitWithConflict(change2.getChangeId(), "Cannot rebase " +
        change2.getCommit().name() +
        ": The change could not be rebased due to a conflict during merge.");
    RevCommit head = getRemoteHead();
    assertThat(head).isEqualTo(oldHead);
    assertCurrentRevision(change2.getChangeId(), 1, change2.getCommitId());
    assertNoSubmitter(change2.getChangeId(), 1);
  }

  @Test
  public void submitAfterReorderOfCommits() throws Exception {
    // Create two commits and push.
    RevCommit c1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    RevCommit c2 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    pushHead(testRepo, "refs/for/master", false);

    String id1 = getChangeId(testRepo, c1).get();
    String id2 = getChangeId(testRepo, c2).get();

    // Swap the order of commits and push again.
    testRepo.reset("HEAD~2");
    testRepo.cherryPick(c2);
    testRepo.cherryPick(c1);
    pushHead(testRepo, "refs/for/master", false);

    approve(id1);
    approve(id2);
    submit(id1);
  }

  @Test
  public void submitChangesAfterBranchOnSecond() throws Exception {
    PushOneCommit.Result change = createChange();
    approve(change.getChangeId());

    PushOneCommit.Result change2nd = createChange();
    approve(change2nd.getChangeId());
    Project.NameKey project = change2nd.getChange().change().getProject();
    Branch.NameKey branch = new Branch.NameKey(project, "branch");
    createBranchWithRevision(branch, change2nd.getCommit().getName());
    gApi.changes().id(change2nd.getChangeId()).current().submit();
    assertMerged(change2nd);
    assertMerged(change);
  }
}
