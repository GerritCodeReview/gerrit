// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ParentCommitData;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import com.google.gerrit.testing.TestChanges;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class ParentDataProviderTest {
  private static final String TARGET_BRANCH = "refs/heads/main";

  private Account.Id userId;
  private Project.NameKey project;
  private InMemoryRepositoryManager repoManager;
  private TestRepository<Repo> testRepo;

  @Before
  public void setUp() throws Exception {
    userId = Account.id(1);
    project = Project.nameKey("platform/cts");
    repoManager = new InMemoryRepositoryManager();
    testRepo = new TestRepository<>(repoManager.createRepository(project));
  }

  @Test
  public void get_memoizesRepeatedCallsForSameParentAndBranch() throws Exception {
    RevCommit baseCommit = testRepo.branch(TARGET_BRANCH).commit().create();
    InternalChangeQuery query = mockQuery();
    when(query.byProjectCommit(project, baseCommit.getId())).thenReturn(ImmutableList.of());

    AtomicInteger queryAllocations = new AtomicInteger();
    ParentDataProvider provider =
        new ParentDataProvider(
            () -> {
              queryAllocations.incrementAndGet();
              return query;
            });

    Repo spyRepo = spy(testRepo.getRepository());
    try (RevWalk rw = new RevWalk(spyRepo)) {
      for (int ps = 1; ps <= 9; ps++) {
        ParentCommitData data =
            provider.get(project, spyRepo, rw, baseCommit.getId(), TARGET_BRANCH);
        assertThat(data.commitId()).hasValue(baseCommit.getId());
        assertThat(data.branchName()).hasValue(TARGET_BRANCH);
        assertThat(data.isMergedInTargetBranch()).isTrue();
        assertThat(data.changeNumber()).isEmpty();
      }
    }

    assertThat(queryAllocations.get()).isEqualTo(1);
    verify(query).setRequestedFields(ChangeField.CHANGE_SPEC, ChangeField.PATCH_SET_SPEC);
    verify(query).byProjectCommit(project, baseCommit.getId());
    verify(spyRepo, times(1)).exactRef(TARGET_BRANCH);
  }

  @Test
  public void prefetch_batchesDistinctParentCommitsIntoSingleProjectScopedQuery() throws Exception {
    // Model the 21-patchset / 5-unique-parent scenario from b/572808865:
    // - Group A (PS 1..4): parent is PS 2 of parentChange (42179854)
    // - Group B (PS 5..7): parent is PS 4 of parentChange (42179854)
    // - Group C (PS 8..16): parent is merged baseCommit1 on refs/heads/main
    // - Group D (PS 17..19): parent is merged baseCommit2 on refs/heads/main
    // - Group E (PS 20..21): parent is merged baseCommit3 on refs/heads/main
    RevCommit baseCommit1 = testRepo.branch(TARGET_BRANCH).commit().create();
    RevCommit baseCommit2 = testRepo.branch(TARGET_BRANCH).commit().parent(baseCommit1).create();
    RevCommit baseCommit3 = testRepo.branch(TARGET_BRANCH).commit().parent(baseCommit2).create();

    RevCommit parentChangePs1 = testRepo.commit().parent(baseCommit1).create();
    RevCommit parentChangePs2 = testRepo.commit().parent(baseCommit1).create();
    RevCommit parentChangePs3 = testRepo.commit().parent(baseCommit1).create();
    RevCommit parentChangePs4 = testRepo.commit().parent(baseCommit1).create();

    ChangeData parentChange = newChange(parentChangePs1);
    addPatchSet(parentChange, parentChangePs2);
    addPatchSet(parentChange, parentChangePs3);
    addPatchSet(parentChange, parentChangePs4);

    List<ObjectId> patchSetParents = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      patchSetParents.add(parentChangePs2.getId());
    }
    for (int i = 0; i < 3; i++) {
      patchSetParents.add(parentChangePs4.getId());
    }
    for (int i = 0; i < 9; i++) {
      patchSetParents.add(baseCommit1.getId());
    }
    for (int i = 0; i < 3; i++) {
      patchSetParents.add(baseCommit2.getId());
    }
    for (int i = 0; i < 2; i++) {
      patchSetParents.add(baseCommit3.getId());
    }
    assertThat(patchSetParents).hasSize(21);

    InternalChangeQuery query = mockQuery();
    when(query.byProjectCommits(eq(project), anyCollection()))
        .thenReturn(ImmutableList.of(parentChange));

    AtomicInteger queryAllocations = new AtomicInteger();
    ParentDataProvider provider =
        new ParentDataProvider(
            () -> {
              queryAllocations.incrementAndGet();
              return query;
            });

    Repo spyRepo = spy(testRepo.getRepository());
    try (RevWalk rw = new RevWalk(spyRepo)) {
      provider.prefetch(project, patchSetParents);
      assertThat(queryAllocations.get()).isEqualTo(1);
      verify(query).setRequestedFields(ChangeField.CHANGE_SPEC, ChangeField.PATCH_SET_SPEC);
      verify(query)
          .byProjectCommits(
              project,
              ImmutableList.of(
                  parentChangePs2.name(),
                  parentChangePs4.name(),
                  baseCommit1.name(),
                  baseCommit2.name(),
                  baseCommit3.name()));

      List<ParentCommitData> results = new ArrayList<>(21);
      for (ObjectId parentId : patchSetParents) {
        results.add(provider.get(project, spyRepo, rw, parentId, TARGET_BRANCH));
      }

      // No additional index queries should be executed during the 21 get() calls.
      assertThat(queryAllocations.get()).isEqualTo(1);
      verify(query, never()).byCommit(any(String.class));
      verify(query, never()).byProjectCommit(any(), any(ObjectId.class));
      // Target branch ref is resolved once across all 14 base-commit patchsets (3 unique bases).
      verify(spyRepo, times(1)).exactRef(TARGET_BRANCH);

      // Verify Group A (PS 1..4 -> parentChange PS 2)
      for (int i = 0; i < 4; i++) {
        ParentCommitData d = results.get(i);
        assertThat(d.changeNumber()).hasValue(parentChange.getId().get());
        assertThat(d.patchSetNumber()).hasValue(2);
        assertThat(d.isMergedInTargetBranch()).isFalse();
      }
      // Verify Group B (PS 5..7 -> parentChange PS 4)
      for (int i = 4; i < 7; i++) {
        ParentCommitData d = results.get(i);
        assertThat(d.changeNumber()).hasValue(parentChange.getId().get());
        assertThat(d.patchSetNumber()).hasValue(4);
        assertThat(d.isMergedInTargetBranch()).isFalse();
      }
      // Verify Groups C, D, E (PS 8..21 -> merged base commits on refs/heads/main)
      for (int i = 7; i < 21; i++) {
        ParentCommitData d = results.get(i);
        assertThat(d.changeNumber()).isEmpty();
        assertThat(d.branchName()).hasValue(TARGET_BRANCH);
        assertThat(d.isMergedInTargetBranch()).isTrue();
      }
    }
  }

  @Test
  public void get_cachesPerTargetBranchWhileReusingChangeLookup() throws Exception {
    RevCommit mainCommit = testRepo.branch(TARGET_BRANCH).commit().create();
    RevCommit otherBranchCommit = testRepo.branch("refs/heads/other").commit().create();
    assertThat(otherBranchCommit).isNotEqualTo(mainCommit);

    InternalChangeQuery query = mockQuery();
    when(query.byProjectCommit(project, mainCommit.getId())).thenReturn(ImmutableList.of());

    AtomicInteger queryAllocations = new AtomicInteger();
    ParentDataProvider provider =
        new ParentDataProvider(
            () -> {
              queryAllocations.incrementAndGet();
              return query;
            });

    try (RevWalk rw = new RevWalk(testRepo.getRepository())) {
      ParentCommitData onMain =
          provider.get(project, testRepo.getRepository(), rw, mainCommit.getId(), TARGET_BRANCH);
      ParentCommitData onOther =
          provider.get(
              project, testRepo.getRepository(), rw, mainCommit.getId(), "refs/heads/other");

      assertThat(onMain.branchName()).hasValue(TARGET_BRANCH);
      assertThat(onMain.isMergedInTargetBranch()).isTrue();

      assertThat(onOther.branchName()).hasValue("refs/heads/other");
      assertThat(onOther.isMergedInTargetBranch()).isFalse();
    }

    // Index lookup for mainCommit was reused across both target branches.
    assertThat(queryAllocations.get()).isEqualTo(1);
  }

  @Test
  public void prefetch_multipleChangesForSameCommit_fallsBackToTargetBranch() throws Exception {
    RevCommit sharedCommit = testRepo.branch(TARGET_BRANCH).commit().create();
    ChangeData change1 = newChange(sharedCommit);
    ChangeData change2 = newChange(sharedCommit);

    InternalChangeQuery query = mockQuery();
    when(query.byProjectCommit(project, sharedCommit.getId()))
        .thenReturn(ImmutableList.of(change1, change2));

    ParentDataProvider provider = new ParentDataProvider(() -> query);
    try (RevWalk rw = new RevWalk(testRepo.getRepository())) {
      provider.prefetch(project, ImmutableList.of(sharedCommit.getId()));
      ParentCommitData data =
          provider.get(project, testRepo.getRepository(), rw, sharedCommit.getId(), TARGET_BRANCH);
      assertThat(data.changeNumber()).isEmpty();
      assertThat(data.isMergedInTargetBranch()).isTrue();
    }
  }

  private InternalChangeQuery mockQuery() {
    InternalChangeQuery query = mock(InternalChangeQuery.class);
    when(query.setRequestedFields(any(), any())).thenReturn(query);
    return query;
  }

  private ChangeData newChange(ObjectId commitId) {
    Change c = TestChanges.newChange(project, userId);
    ChangeData cd = ChangeData.createForTest(project, c.getId(), 1, commitId);
    cd.setChange(c);
    cd.setPatchSets(ImmutableList.of(cd.currentPatchSet()));
    return cd;
  }

  private void addPatchSet(ChangeData cd, ObjectId commitId) {
    TestChanges.incrementPatchSet(cd.change());
    PatchSet ps = TestChanges.newPatchSet(cd.change().currentPatchSetId(), commitId.name(), userId);
    List<PatchSet> patchSets = new ArrayList<>(cd.patchSets());
    patchSets.add(ps);
    cd.setPatchSets(patchSets);
  }
}
