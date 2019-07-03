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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.assertPushOk;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

@NoHttpd
public class SubmitOnPushIT extends AbstractDaemonTest {
  @Inject private ApprovalsUtil approvalsUtil;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void submitOnPush() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/master").group(adminGroupUuid()))
        .update();
    PushOneCommit.Result r = pushTo("refs/for/master%submit");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/heads/master");
  }

  @Test
  public void submitOnPushToRefsMetaConfig() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/meta/config").group(adminGroupUuid()))
        .update();

    git().fetch().setRefSpecs(new RefSpec("refs/meta/config:refs/meta/config")).call();
    testRepo.reset(RefNames.REFS_CONFIG);

    PushOneCommit.Result r = pushTo("refs/for/refs/meta/config%submit");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, RefNames.REFS_CONFIG);
  }

  @Test
  public void submitOnPushMergeConflict() throws Exception {
    ObjectId objectId = repo().exactRef("HEAD").getObjectId();
    push("refs/heads/master", "one change", "a.txt", "some content");
    testRepo.reset(objectId);

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/master").group(adminGroupUuid()))
        .update();
    PushOneCommit.Result r =
        push("refs/for/master%submit", "other change", "a.txt", "other content");
    r.assertErrorStatus();
    r.assertChange(Change.Status.NEW, null);
    r.assertMessage(
        "Change " + r.getChange().getId() + ": change could not be merged due to a path conflict.");
  }

  @Test
  public void submitOnPushSuccessfulMerge() throws Exception {
    String master = "refs/heads/master";
    ObjectId objectId = repo().exactRef("HEAD").getObjectId();
    push(master, "one change", "a.txt", "some content");
    testRepo.reset(objectId);

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/master").group(adminGroupUuid()))
        .update();
    PushOneCommit.Result r =
        push("refs/for/master%submit", "other change", "b.txt", "other content");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertMergeCommit(master, "other change");
  }

  @Test
  public void submitOnPushNewPatchSet() throws Exception {
    PushOneCommit.Result r =
        push("refs/for/master", PushOneCommit.SUBJECT, "a.txt", "some content");

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/master").group(adminGroupUuid()))
        .update();
    r =
        push(
            "refs/for/master%submit",
            PushOneCommit.SUBJECT, "a.txt", "other content", r.getChangeId());
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    ChangeData cd = Iterables.getOnlyElement(queryProvider.get().byKeyPrefix(r.getChangeId()));
    assertThat(cd.patchSets()).hasSize(2);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/heads/master");
  }

  @Test
  public void submitOnPushNotAllowed_Error() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master%submit");
    r.assertErrorStatus("not permitted: update by submit");
  }

  @Test
  public void submitOnPushNewPatchSetNotAllowed_Error() throws Exception {
    PushOneCommit.Result r =
        push("refs/for/master", PushOneCommit.SUBJECT, "a.txt", "some content");

    r =
        push(
            "refs/for/master%submit",
            PushOneCommit.SUBJECT, "a.txt", "other content", r.getChangeId());
    r.assertErrorStatus("not permitted: update by submit ");
  }

  @Test
  public void submitOnPushToNonExistingBranch_Error() throws Exception {
    String branchName = "non-existing";
    PushOneCommit.Result r = pushTo("refs/for/" + branchName + "%submit");
    r.assertErrorStatus("branch " + branchName + " not found");
  }

  @Test
  public void mergeOnPushToBranch() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/master").group(adminGroupUuid()))
        .update();
    PushOneCommit.Result r =
        push("refs/for/master", PushOneCommit.SUBJECT, "a.txt", "some content");
    r.assertOkStatus();

    git().push().setRefSpecs(new RefSpec(r.getCommit().name() + ":refs/heads/master")).call();
    assertCommit(project, "refs/heads/master");

    ChangeData cd =
        Iterables.getOnlyElement(queryProvider.get().byKey(Change.key(r.getChangeId())));
    RevCommit c = r.getCommit();
    PatchSet.Id psId = cd.currentPatchSet().id();
    assertThat(psId.get()).isEqualTo(1);
    assertThat(cd.change().isMerged()).isTrue();
    assertSubmitApproval(psId);

    assertThat(cd.patchSets()).hasSize(1);
    assertThat(cd.patchSet(psId).commitId()).isEqualTo(c);
  }

  @Test
  public void mergeOnPushToBranchWithChangeMergedInOther() throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    String master = "refs/heads/master";
    String other = "refs/heads/other";
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref(master).group(adminGroupUuid()))
        .add(allow(Permission.CREATE).ref(other).group(adminGroupUuid()))
        .add(allow(Permission.PUSH).ref(other).group(adminGroupUuid()))
        .update();
    RevCommit masterRev = projectOperations.project(project).getHead("master");
    pushCommitTo(masterRev, other);
    PushOneCommit.Result r = createChange();
    r.assertOkStatus();
    RevCommit commit = r.getCommit();
    pushCommitTo(commit, master);
    assertCommit(project, master);
    ChangeData cd =
        Iterables.getOnlyElement(queryProvider.get().byKey(Change.key(r.getChangeId())));
    assertThat(cd.change().isMerged()).isTrue();

    RemoteRefUpdate.Status status = pushCommitTo(commit, "refs/for/other");
    assertThat(status).isEqualTo(RemoteRefUpdate.Status.OK);

    pushCommitTo(commit, other);
    assertCommit(project, other);

    for (ChangeData c : queryProvider.get().byKey(Change.key(r.getChangeId()))) {
      if (c.change().getDest().branch().equals(other)) {
        assertThat(c.change().isMerged()).isTrue();
      }
    }
  }

  private RemoteRefUpdate.Status pushCommitTo(RevCommit commit, String ref)
      throws GitAPIException, InvalidRemoteException, TransportException {
    return Iterables.getOnlyElement(
            git().push().setRefSpecs(new RefSpec(commit.name() + ":" + ref)).call())
        .getRemoteUpdate(ref)
        .getStatus();
  }

  @Test
  public void mergeOnPushToBranchWithNewPatchset() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/master").group(adminGroupUuid()))
        .update();
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    RevCommit c1 = r.getCommit();
    PatchSet.Id psId1 = r.getPatchSetId();
    assertThat(psId1.get()).isEqualTo(1);

    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "b.txt",
            "anotherContent",
            r.getChangeId());

    r = push.to("refs/heads/master");
    r.assertOkStatus();

    ChangeData cd = r.getChange();
    RevCommit c2 = r.getCommit();
    assertThat(cd.change().isMerged()).isTrue();
    PatchSet.Id psId2 = cd.change().currentPatchSetId();
    assertThat(psId2.get()).isEqualTo(2);
    assertCommit(project, "refs/heads/master");
    assertSubmitApproval(psId2);

    assertThat(cd.patchSets()).hasSize(2);
    assertThat(cd.patchSet(psId1).commitId()).isEqualTo(c1);
    assertThat(cd.patchSet(psId2).commitId()).isEqualTo(c2);
  }

  @Test
  public void mergeOnPushToBranchWithOldPatchset() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/master").group(adminGroupUuid()))
        .update();
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    RevCommit c1 = r.getCommit();
    PatchSet.Id psId1 = r.getPatchSetId();
    String changeId = r.getChangeId();
    assertThat(psId1.get()).isEqualTo(1);

    r = amendChange(changeId);
    ChangeData cd = r.getChange();
    PatchSet.Id psId2 = cd.change().currentPatchSetId();
    assertThat(psId2.changeId()).isEqualTo(psId1.changeId());
    assertThat(psId2.get()).isEqualTo(2);

    testRepo.reset(c1);
    assertPushOk(pushHead(testRepo, "refs/heads/master", false), "refs/heads/master");

    cd = changeDataFactory.create(project, psId1.changeId());
    Change c = cd.change();
    assertThat(c.isMerged()).isTrue();
    assertThat(c.currentPatchSetId()).isEqualTo(psId1);
    assertThat(cd.patchSets().stream().map(PatchSet::id).collect(toList()))
        .containsExactly(psId1, psId2);
  }

  @Test
  public void mergeMultipleOnPushToBranchWithNewPatchset() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/master").group(adminGroupUuid()))
        .update();

    // Create 2 changes.
    ObjectId initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result r1 = createChange("Change 1", "a", "a");
    r1.assertOkStatus();
    PushOneCommit.Result r2 = createChange("Change 2", "b", "b");
    r2.assertOkStatus();

    RevCommit c1_1 = r1.getCommit();
    RevCommit c2_1 = r2.getCommit();
    PatchSet.Id psId1_1 = r1.getPatchSetId();
    PatchSet.Id psId2_1 = r2.getPatchSetId();
    assertThat(c1_1.getParent(0)).isEqualTo(initialHead);
    assertThat(c2_1.getParent(0)).isEqualTo(c1_1);

    // Amend both changes.
    testRepo.reset(initialHead);
    RevCommit c1_2 =
        testRepo
            .branch("HEAD")
            .commit()
            .message(c1_1.getShortMessage() + "v2")
            .insertChangeId(r1.getChangeId().substring(1))
            .create();
    RevCommit c2_2 = testRepo.cherryPick(c2_1);

    // Push directly to branch.
    assertPushOk(pushHead(testRepo, "refs/heads/master", false), "refs/heads/master");

    ChangeData cd2 = r2.getChange();
    assertThat(cd2.change().isMerged()).isTrue();
    PatchSet.Id psId2_2 = cd2.change().currentPatchSetId();
    assertThat(psId2_2.get()).isEqualTo(2);
    assertThat(cd2.patchSet(psId2_1).commitId()).isEqualTo(c2_1);
    assertThat(cd2.patchSet(psId2_2).commitId()).isEqualTo(c2_2);

    ChangeData cd1 = r1.getChange();
    assertThat(cd1.change().isMerged()).isTrue();
    PatchSet.Id psId1_2 = cd1.change().currentPatchSetId();
    assertThat(psId1_2.get()).isEqualTo(2);
    assertThat(cd1.patchSet(psId1_1).commitId()).isEqualTo(c1_1);
    assertThat(cd1.patchSet(psId1_2).commitId()).isEqualTo(c1_2);
  }

  @Test
  public void submitNoReviewNotifications() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/master").group(adminGroupUuid()))
        .update();

    TestAccount user = accountCreator.user();
    String pushSpec = "refs/for/master%reviewer=" + user.email() + ",cc=" + user.email();
    sender.clear();

    PushOneCommit.Result result = pushTo(pushSpec + ",submit,notify=" + NotifyHandling.NONE);
    result.assertOkStatus();
    assertThat(sender.getMessages()).isEmpty();

    sender.clear();
    result = pushTo(pushSpec + ",submit,notify=" + NotifyHandling.OWNER);
    result.assertOkStatus();
    assertThat(sender.getMessages()).isEmpty();

    sender.clear();
    result = pushTo(pushSpec + ",submit,notify=" + NotifyHandling.OWNER_REVIEWERS);
    result.assertOkStatus();
    assertThat(sender.getMessages()).hasSize(2);

    sender.clear();
    result = pushTo(pushSpec + ",submit,notify=" + NotifyHandling.ALL);
    result.assertOkStatus();
    assertThat(sender.getMessages()).hasSize(2);
  }

  @Test
  public void submitNoReviewNotificationsToCcBcc() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/master").group(adminGroupUuid()))
        .update();

    TestAccount user = accountCreator.user();
    String pushSpec = "refs/for/master%reviewer=" + user.email() + ",cc=" + user.email();g

    TestAccount user2 = accountCreator.user2();

    sender.clear();
    PushOneCommit.Result result =
        pushTo(pushSpec + ",submit,notify=" + NotifyHandling.NONE + ",notify-to=" + user2.email());
    result.assertOkStatus();
    assertNotifyTo(user2);

    sender.clear();
    result =
        pushTo(pushSpec + ",submit,notify=" + NotifyHandling.NONE + ",notify-cc=" + user2.email());
    result.assertOkStatus();
    assertNotifyCc(user2);

    sender.clear();
    result =
        pushTo(pushSpec + ",submit,notify=" + NotifyHandling.NONE + ",notify-bcc=" + user2.email());
    result.assertOkStatus();
    assertNotifyBcc(user2);
  }

  private PatchSetApproval getSubmitter(PatchSet.Id patchSetId) throws Exception {
    ChangeNotes notes = notesFactory.createChecked(project, patchSetId.changeId()).load();
    return approvalsUtil.getSubmitter(notes, patchSetId);
  }

  private void assertSubmitApproval(PatchSet.Id patchSetId) throws Exception {
    PatchSetApproval a = getSubmitter(patchSetId);
    assertThat(a.isLegacySubmit()).isTrue();
    assertThat(a.value()).isEqualTo((short) 1);
    assertThat(a.accountId()).isEqualTo(admin.id());
  }

  private void assertCommit(Project.NameKey project, String branch) throws Exception {
    try (Repository r = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(r)) {
      RevCommit c = rw.parseCommit(r.exactRef(branch).getObjectId());
      assertThat(c.getShortMessage()).isEqualTo(PushOneCommit.SUBJECT);
      assertThat(c.getAuthorIdent().getEmailAddress()).isEqualTo(admin.email());
      assertThat(c.getCommitterIdent().getEmailAddress()).isEqualTo(admin.email());
    }
  }

  private void assertMergeCommit(String branch, String subject) throws Exception {
    try (Repository r = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(r)) {
      RevCommit c = rw.parseCommit(r.exactRef(branch).getObjectId());
      assertThat(c.getParentCount()).isEqualTo(2);
      assertThat(c.getShortMessage()).isEqualTo("Merge \"" + subject + "\"");
      assertThat(c.getAuthorIdent().getEmailAddress()).isEqualTo(admin.email());
      assertThat(c.getCommitterIdent().getEmailAddress())
          .isEqualTo(serverIdent.get().getEmailAddress());
    }
  }

  private PushOneCommit.Result push(String ref, String subject, String fileName, String content)
      throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, subject, fileName, content);
    return push.to(ref);
  }

  private PushOneCommit.Result push(
      String ref, String subject, String fileName, String content, String changeId)
      throws Exception {
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, subject, fileName, content, changeId);
    return push.to(ref);
  }
}
