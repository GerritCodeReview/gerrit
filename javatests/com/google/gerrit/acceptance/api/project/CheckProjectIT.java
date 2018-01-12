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
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.projects.CheckProjectInput;
import com.google.gerrit.extensions.api.projects.CheckProjectInput.AutoClosableChangesCheckInput;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
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
        gApi.projects().name(project.get()).check(checkProjectInputForAutoClosableCheck(branch));
    assertThat(checkResult.autoClosableChangesCheckResult.autoClosableChanges).isEmpty();

    info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void detectAutoCloseableChangeByCommit() throws Exception {
    PushOneCommit.Result r = createChange("refs/for/master");
    String branch = r.getChange().change().getDest().get();

    serverSideTestRepo.branch(branch).update(testRepo.getRevWalk().parseCommit(r.getCommit()));

    ChangeInfo info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);

    CheckProjectResultInfo checkResult =
        gApi.projects().name(project.get()).check(checkProjectInputForAutoClosableCheck(branch));
    assertThat(checkResult.autoClosableChangesCheckResult.autoClosableChanges.keySet())
        .containsExactly(branch);
    assertThat(
            checkResult
                .autoClosableChangesCheckResult
                .autoClosableChanges
                .get(branch)
                .stream()
                .map(i -> i._number)
                .collect(toSet()))
        .containsExactly(r.getChange().getId().get());

    info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void fixAutoCloseableChangeByCommit() throws Exception {
    PushOneCommit.Result r = createChange("refs/for/master");
    String branch = r.getChange().change().getDest().get();

    serverSideTestRepo.branch(branch).update(testRepo.getRevWalk().parseCommit(r.getCommit()));

    ChangeInfo info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);

    CheckProjectInput input = checkProjectInputForAutoClosableCheck(branch);
    input.autoClosableChangesCheck.fix = true;
    CheckProjectResultInfo checkResult = gApi.projects().name(project.get()).check(input);
    assertThat(checkResult.autoClosableChangesCheckResult.autoClosableChanges.keySet())
        .containsExactly(branch);
    assertThat(
            checkResult
                .autoClosableChangesCheckResult
                .autoClosableChanges
                .get(branch)
                .stream()
                .map(i -> i._number)
                .collect(toSet()))
        .containsExactly(r.getChange().getId().get());

    info = gApi.changes().id(r.getChange().getId().get()).info();
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
        gApi.projects().name(project.get()).check(checkProjectInputForAutoClosableCheck(branch));
    assertThat(checkResult.autoClosableChangesCheckResult.autoClosableChanges.keySet())
        .containsExactly(branch);
    assertThat(
            checkResult
                .autoClosableChangesCheckResult
                .autoClosableChanges
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

    CheckProjectInput input = checkProjectInputForAutoClosableCheck(branch);
    input.autoClosableChangesCheck.fix = true;
    CheckProjectResultInfo checkResult = gApi.projects().name(project.get()).check(input);
    assertThat(checkResult.autoClosableChangesCheckResult.autoClosableChanges.keySet())
        .containsExactly(branch);
    assertThat(
            checkResult
                .autoClosableChangesCheckResult
                .autoClosableChanges
                .get(branch)
                .stream()
                .map(i -> i._number)
                .collect(toSet()))
        .containsExactly(r.getChange().getId().get());

    info = gApi.changes().id(r.getChange().getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
  }

  private static CheckProjectInput checkProjectInputForAutoClosableCheck(String branch) {
    CheckProjectInput input = new CheckProjectInput();
    input.autoClosableChangesCheck = new AutoClosableChangesCheckInput();
    input.autoClosableChangesCheck.branches = ImmutableList.of(branch);
    return input;
  }
}
