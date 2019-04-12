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
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
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

  @Test
  public void submitOnPush() throws Exception {
    grant(project, "refs/for/refs/heads/master", Permission.SUBMIT);
    PushOneCommit.Result r = pushTo("refs/for/master%submit");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/heads/master");
  }

  @Test
  public void submitOnPushToRefsMetaConfig() throws Exception {
    grant(project, "refs/for/refs/meta/config", Permission.SUBMIT);

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

    grant(project, "refs/for/refs/heads/master", Permission.SUBMIT);
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

    grant(project, "refs/for/refs/heads/master", Permission.SUBMIT);
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

    grant(project, "refs/for/refs/heads/master", Permission.SUBMIT);
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
    grant(project, "refs/heads/master", Permission.PUSH);
    PushOneCommit.Result r =
        push("refs/for/master", PushOneCommit.SUBJECT, "a.txt", "some content");
    r.assertOkStatus();

    git().push().setRefSpecs(new RefSpec(r.getCommit().name() + ":refs/heads/master")).call();
    assertCommit(project, "refs/heads/master");

    ChangeData cd =
        Iterables.getOnlyElement(queryProvider.get().byKey(new Change.Key(r.getChangeId())));
    RevCommit c = r.getCommit();
    PatchSet.Id psId = cd.currentPatchSet().getId();
    assertThat(psId.get()).isEqualTo(1);
    assertThat(cd.change().isMerged()).isTrue();
    assertSubmitApproval(psId);

    assertThat(cd.patchSets()).hasSize(1);
    assertThat(cd.patchSet(psId).getRevision().get()).isEqualTo(c.name());
  }

  @Test
  public void mergeOnPushToBranchWithChangeMergedInOther() throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    String master = "refs/heads/master";
    String other = "refs/heads/other";
    grant(project, master, Permission.PUSH);
    grant(project, other, Permission.CREATE);
    grant(project, other, Permission.PUSH);
    RevCommit masterRev = getRemoteHead();
    pushCommitTo(masterRev, other);
    PushOneCommit.Result r = createChange();
    r.assertOkStatus();
    RevCommit commit = r.getCommit();
    pushCommitTo(commit, master);
    assertCommit(project, master);
    ChangeData cd =
        Iterables.getOnlyElement(queryProvider.get().byKey(new Change.Key(r.getChangeId())));
    assertThat(cd.change().isMerged()).isTrue();

    RemoteRefUpdate.Status status = pushCommitTo(commit, "refs/for/other");
    assertThat(status).isEqualTo(RemoteRefUpdate.Status.OK);

    pushCommitTo(commit, other);
    assertCommit(project, other);

    for (ChangeData c : queryProvider.get().byKey(new Change.Key(r.getChangeId()))) {
      if (c.change().getDest().get().equals(other)) {
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
    grant(project, "refs/heads/master", Permission.PUSH);
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
    assertThat(cd.patchSet(psId1).getRevision().get()).isEqualTo(c1.name());
    assertThat(cd.patchSet(psId2).getRevision().get()).isEqualTo(c2.name());
  }

  @Test
  public void mergeOnPushToBranchWithOldPatchset() throws Exception {
    grant(project, "refs/heads/master", Permission.PUSH);
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    RevCommit c1 = r.getCommit();
    PatchSet.Id psId1 = r.getPatchSetId();
    String changeId = r.getChangeId();
    assertThat(psId1.get()).isEqualTo(1);

    r = amendChange(changeId);
    ChangeData cd = r.getChange();
    PatchSet.Id psId2 = cd.change().currentPatchSetId();
    assertThat(psId2.getParentKey()).isEqualTo(psId1.getParentKey());
    assertThat(psId2.get()).isEqualTo(2);

    testRepo.reset(c1);
    assertPushOk(pushHead(testRepo, "refs/heads/master", false), "refs/heads/master");

    cd = changeDataFactory.create(project, psId1.getParentKey());
    Change c = cd.change();
    assertThat(c.isMerged()).isTrue();
    assertThat(c.currentPatchSetId()).isEqualTo(psId1);
    assertThat(cd.patchSets().stream().map(PatchSet::getId).collect(toList()))
        .containsExactly(psId1, psId2);
  }

  @Test
  public void mergeMultipleOnPushToBranchWithNewPatchset() throws Exception {
    grant(project, "refs/heads/master", Permission.PUSH);

    // Create 2 changes.
    ObjectId initialHead = getRemoteHead();
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
    assertThat(cd2.patchSet(psId2_1).getRevision().get()).isEqualTo(c2_1.name());
    assertThat(cd2.patchSet(psId2_2).getRevision().get()).isEqualTo(c2_2.name());

    ChangeData cd1 = r1.getChange();
    assertThat(cd1.change().isMerged()).isTrue();
    PatchSet.Id psId1_2 = cd1.change().currentPatchSetId();
    assertThat(psId1_2.get()).isEqualTo(2);
    assertThat(cd1.patchSet(psId1_1).getRevision().get()).isEqualTo(c1_1.name());
    assertThat(cd1.patchSet(psId1_2).getRevision().get()).isEqualTo(c1_2.name());
  }

  private PatchSetApproval getSubmitter(PatchSet.Id patchSetId) throws Exception {
    ChangeNotes notes = notesFactory.createChecked(project, patchSetId.getParentKey()).load();
    return approvalsUtil.getSubmitter(notes, patchSetId);
  }

  private void assertSubmitApproval(PatchSet.Id patchSetId) throws Exception {
    PatchSetApproval a = getSubmitter(patchSetId);
    assertThat(a.isLegacySubmit()).isTrue();
    assertThat(a.getValue()).isEqualTo((short) 1);
    assertThat(a.getAccountId()).isEqualTo(admin.id());
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
