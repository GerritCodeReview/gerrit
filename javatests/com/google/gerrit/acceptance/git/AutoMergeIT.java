// Copyright (C) 2021 The Android Open Source Project
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
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Before;
import org.junit.Test;

/** Ensures that auto merge commits are created when a new patch set or change is uploaded. */
public class AutoMergeIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  private RevCommit parent1;
  private RevCommit parent2;

  @Before
  public void setup() throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();

    PushOneCommit.Result p1 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "parent 1",
                ImmutableMap.of("foo", "foo-1.2", "bar", "bar-1.2"))
            .to("refs/for/master");
    parent1 = p1.getCommit();

    // reset HEAD in order to create a sibling of the first change
    testRepo.reset(initial);

    PushOneCommit.Result p2 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "parent 2",
                ImmutableMap.of("foo", "foo-2.2", "bar", "bar-2.2"))
            .to("refs/for/master");
    parent2 = p2.getCommit();
  }

  @Test
  public void autoMergeCreatedWhenPushingNewChange() throws Exception {
    PushOneCommit m =
        pushFactory.create(
            admin.newIdent(), testRepo, "merge", ImmutableMap.of("foo", "foo-1", "bar", "bar-2"));
    m.setParents(ImmutableList.of(parent1, parent2));
    PushOneCommit.Result result = m.to("refs/for/master");
    result.assertOkStatus();
    assertAutoMergeCreated(result.getCommit());
  }

  @Test
  public void autoMergeCreatedWhenPushingNewPatchSet() throws Exception {
    PushOneCommit m =
        pushFactory.create(
            admin.newIdent(), testRepo, "merge", ImmutableMap.of("foo", "foo-1", "bar", "bar-2"));
    m.setParents(ImmutableList.of(parent1, parent2));
    PushOneCommit.Result ps1 = m.to("refs/for/master");
    RevCommit ps2 =
        testRepo
            .amend(ps1.getCommit())
            .message("PS2")
            .insertChangeId(ps1.getChangeId().substring(1))
            .create();
    testRepo.reset(ps2);
    GitUtil.pushHead(testRepo, "refs/for/master");
    // Make sure we have two patch sets
    assertThat(ps2.getParents().length).isEqualTo(2);
    assertThat(gApi.changes().id(ps1.getChangeId()).get().revisions.size()).isEqualTo(2);
    assertAutoMergeCreated(ps2);
  }

  @Test
  public void autoMergeCreatedWhenPushingMergeBetweenTwoInitialCommits() throws Exception {
    Project.NameKey projectWithoutInitialCommit =
        projectOperations.newProject().createEmptyCommit(false).create();

    TestRepository<InMemoryRepository> testRepo =
        cloneProject(projectWithoutInitialCommit, getCloneAsAccount(configRule.description()));

    RevCommit initialCommit1 =
        testRepo.parseBody(
            testRepo
                .commit()
                .message("Initial Change 1")
                .insertChangeId()
                .add("file1", "contents1")
                .create());
    RevCommit initialCommit2 =
        testRepo.parseBody(
            testRepo
                .commit()
                .message("Initial Change 2")
                .insertChangeId()
                .add("file1", "contents2")
                .create());
    RevCommit mergeCommit =
        testRepo
            .branch("master")
            .commit()
            .message("Merge Change")
            .parent(initialCommit1)
            .parent(initialCommit2)
            .insertChangeId()
            .create();
    testRepo.reset(mergeCommit);
    PushResult r = pushHead(testRepo, "refs/for/master");
    assertThat(r.getRemoteUpdate("refs/for/master").getStatus()).isEqualTo(Status.OK);

    assertAutoMergeCreated(projectWithoutInitialCommit, mergeCommit);
  }

  @Test
  public void autoMergeCreatedWhenChangeCreatedOnApi() throws Exception {
    ChangeInput ci = new ChangeInput(project.get(), "master", "Merge commit");
    ci.merge = new MergeInput();
    ci.merge.source = parent1.name();

    String newChangePatchSetSha1 = gApi.changes().create(ci).get().currentRevision;
    assertAutoMergeCreated(ObjectId.fromString(newChangePatchSetSha1));
  }

  @Test
  public void autoMergeCreatedWhenNewPatchSetCreatedOnApi() throws Exception {
    ChangeInput ci = new ChangeInput(project.get(), "master", "Merge commit");
    ci.merge = new MergeInput();
    ci.merge.source = parent1.name();

    String changeId = gApi.changes().create(ci).get().changeId;
    gApi.changes().id(changeId).setMessage("New Commit Message\n\nChange-Id: " + changeId);
    assertThat(gApi.changes().id(changeId).get().revisions.size()).isEqualTo(2);
    String newChangePatchSetSha1 = gApi.changes().id(changeId).get().currentRevision;
    assertAutoMergeCreated(ObjectId.fromString(newChangePatchSetSha1));
  }

  @Test
  public void autoMergeCreatedWhenChangeEditIsPublished() throws Exception {
    PushOneCommit m =
        pushFactory.create(
            admin.newIdent(), testRepo, "merge", ImmutableMap.of("foo", "foo-1", "bar", "bar-2"));
    m.setParents(ImmutableList.of(parent1, parent2));
    PushOneCommit.Result result = m.to("refs/for/master");
    result.assertOkStatus();
    assertAutoMergeCreated(result.getCommit());

    gApi.changes()
        .id(result.getChangeId())
        .edit()
        .modifyFile("new-file", RawInputUtil.create("content"));
    gApi.changes().id(result.getChangeId()).edit().publish();
    assertThat(gApi.changes().id(result.getChangeId()).get().revisions.size()).isEqualTo(2);
    String newChangePatchSetSha1 = gApi.changes().id(result.getChangeId()).get().currentRevision;
    assertAutoMergeCreated(ObjectId.fromString(newChangePatchSetSha1));
  }

  @Test
  public void noAutoMergeCreatedWhenPushingNonMergeCommit() throws Exception {
    PushOneCommit.Result change = createChange();
    change.assertOkStatus();
    assertNoAutoMergeCreated(change.getCommit());
  }

  @Test
  public void autoMergeComputedInMemoryWhenMissing() throws Exception {
    PushOneCommit m =
        pushFactory.create(
            admin.newIdent(), testRepo, "merge", ImmutableMap.of("foo", "foo-1", "bar", "bar-2"));
    m.setParents(ImmutableList.of(parent1, parent2));
    PushOneCommit.Result result = m.to("refs/for/master");
    result.assertOkStatus();
    assertAutoMergeCreated(result.getCommit());

    // Delete auto merge branch
    deleteAutoMergeBranch(result.getCommit());
    // Trigger AutoMerge computation
    assertThat(gApi.changes().id(result.getChangeId()).revision(1).file("foo").blameRequest().get())
        .isNotEmpty();
    assertNoAutoMergeCreated(result.getCommit());
  }

  @Test
  public void pushWorksIfAutoMergeExists() throws Exception {
    PushOneCommit m =
        pushFactory.create(
            admin.newIdent(), testRepo, "merge", ImmutableMap.of("foo", "foo-1", "bar", "bar-2"));
    m.setParents(ImmutableList.of(parent1, parent2));
    PushOneCommit.Result result = m.to("refs/for/master");
    result.assertOkStatus();
    assertAutoMergeCreated(result.getCommit());

    // Delete change and push commit again.
    gApi.changes().id(result.getChangeId()).delete();

    // Push again successfully and check that AutoMerge commit is still there
    result = m.to("refs/for/master");
    result.assertOkStatus();
    assertAutoMergeCreated(result.getCommit());
  }

  private void assertAutoMergeCreated(ObjectId mergeCommit) throws Exception {
    assertAutoMergeCreated(project, mergeCommit);
  }

  private void assertAutoMergeCreated(Project.NameKey project, ObjectId mergeCommit)
      throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef(RefNames.refsCacheAutomerge(mergeCommit.name()))).isNotNull();
    }
  }

  private void assertNoAutoMergeCreated(ObjectId mergeCommit) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef(RefNames.refsCacheAutomerge(mergeCommit.name()))).isNull();
    }
  }

  private void deleteAutoMergeBranch(ObjectId mergeCommit) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      RefUpdate ru = repo.updateRef(RefNames.refsCacheAutomerge(mergeCommit.name()));
      ru.setForceUpdate(true);
      testRefAction(() -> assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED));
    }
    assertNoAutoMergeCreated(mergeCommit);
  }
}
