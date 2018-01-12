// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.projects.CheckProjectInput;
import com.google.gerrit.extensions.api.projects.CheckProjectInput.AutoCloseableChangesCheckInput;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class CheckProjectIT extends AbstractDaemonTest {
  private TestRepository<InMemoryRepository> serverSideTestRepo;

  @Before
  public void setUp() throws Exception {
    serverSideTestRepo =
        new TestRepository<>((InMemoryRepository) repoManager.openRepository(project));
  }

  @Test
  public void noProblem() throws Exception {
    PushOneCommit.Result r = createChange("refs/for/master");
    String branch = r.getChange().change().getDest().get();

    ChangeInfo info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);

    CheckProjectResultInfo checkResult =
        gApi.projects().name(project.get()).check(checkProjectInputForAutoCloseableCheck(branch));
    assertThat(checkResult.autoCloseableChangesCheckResult.autoCloseableChanges).isEmpty();

    info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void detectAutoCloseableChangeByCommit() throws Exception {
    RevCommit commit = pushCommitWithoutChangeIdForReview();
    ChangeInfo change =
        Iterables.getOnlyElement(gApi.changes().query("commit:" + commit.name()).get());

    String branch = "refs/heads/master";
    serverSideTestRepo.branch(branch).update(testRepo.getRevWalk().parseCommit(commit));

    ChangeInfo info = gApi.changes().id(change._number).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);

    CheckProjectResultInfo checkResult =
        gApi.projects().name(project.get()).check(checkProjectInputForAutoCloseableCheck(branch));
    assertThat(checkResult.autoCloseableChangesCheckResult.autoCloseableChanges.keySet())
        .containsExactly(branch);
    assertThat(
            checkResult
                .autoCloseableChangesCheckResult
                .autoCloseableChanges
                .get(branch)
                .stream()
                .map(i -> i._number)
                .collect(toList()))
        .containsExactly(change._number);

    info = gApi.changes().id(change._number).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void fixAutoCloseableChangeByCommit() throws Exception {
    RevCommit commit = pushCommitWithoutChangeIdForReview();
    ChangeInfo change =
        Iterables.getOnlyElement(gApi.changes().query("commit:" + commit.name()).get());

    String branch = "refs/heads/master";
    serverSideTestRepo.branch(branch).update(commit);

    ChangeInfo info = gApi.changes().id(change._number).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);

    CheckProjectInput input = checkProjectInputForAutoCloseableCheck(branch);
    input.autoCloseableChangesCheck.fix = true;
    CheckProjectResultInfo checkResult = gApi.projects().name(project.get()).check(input);
    assertThat(checkResult.autoCloseableChangesCheckResult.autoCloseableChanges.keySet())
        .containsExactly(branch);
    assertThat(
            checkResult
                .autoCloseableChangesCheckResult
                .autoCloseableChanges
                .get(branch)
                .stream()
                .map(i -> i._number)
                .collect(toSet()))
        .containsExactly(change._number);

    info = gApi.changes().id(change._number).info();
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void detectAutoCloseableChangeByChangeId() throws Exception {
    PushOneCommit.Result r = createChange("refs/for/master");
    String branch = r.getChange().change().getDest().get();

    RevCommit amendedCommit = serverSideTestRepo.amend(r.getCommit()).create();
    serverSideTestRepo.branch(branch).update(amendedCommit);

    ChangeInfo info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);

    CheckProjectResultInfo checkResult =
        gApi.projects().name(project.get()).check(checkProjectInputForAutoCloseableCheck(branch));
    assertThat(checkResult.autoCloseableChangesCheckResult.autoCloseableChanges.keySet())
        .containsExactly(branch);
    assertThat(
            checkResult
                .autoCloseableChangesCheckResult
                .autoCloseableChanges
                .get(branch)
                .stream()
                .map(i -> i._number)
                .collect(toSet()))
        .containsExactly(r.getChange().getId().get());

    info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void fixAutoCloseableChangeByChangeId() throws Exception {
    PushOneCommit.Result r = createChange("refs/for/master");
    String branch = r.getChange().change().getDest().get();

    RevCommit amendedCommit = serverSideTestRepo.amend(r.getCommit()).create();
    serverSideTestRepo.branch(branch).update(amendedCommit);

    ChangeInfo info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);

    CheckProjectInput input = checkProjectInputForAutoCloseableCheck(branch);
    input.autoCloseableChangesCheck.fix = true;
    CheckProjectResultInfo checkResult = gApi.projects().name(project.get()).check(input);
    assertThat(checkResult.autoCloseableChangesCheckResult.autoCloseableChanges.keySet())
        .containsExactly(branch);
    assertThat(
            checkResult
                .autoCloseableChangesCheckResult
                .autoCloseableChanges
                .get(branch)
                .stream()
                .map(i -> i._number)
                .collect(toSet()))
        .containsExactly(r.getChange().getId().get());

    info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
  }

  private RevCommit pushCommitWithoutChangeIdForReview() throws Exception {
    setRequireChangeId(InheritableBoolean.FALSE);
    RevCommit commit =
        testRepo
            .branch("HEAD")
            .commit()
            .message("A change")
            .author(admin.getIdent())
            .committer(new PersonIdent(admin.getIdent(), testRepo.getDate()))
            .create();
    pushHead(testRepo, "refs/for/master");
    return commit;
  }

  private static CheckProjectInput checkProjectInputForAutoCloseableCheck(String branch) {
    CheckProjectInput input = new CheckProjectInput();
    input.autoCloseableChangesCheck = new AutoCloseableChangesCheckInput();
    input.autoCloseableChangesCheck.branches = ImmutableList.of(branch);
    return input;
  }
}
