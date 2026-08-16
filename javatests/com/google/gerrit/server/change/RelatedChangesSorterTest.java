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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.change.RelatedChangesSorter.PatchSetData;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import com.google.gerrit.testing.TestChanges;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class RelatedChangesSorterTest {
  private Account.Id userId;
  private InMemoryRepositoryManager repoManager;
  private PermissionBackend permissionBackend;
  private ProjectCache projectCache;
  private RelatedChangesSorter sorter;

  @Before
  public void setUp() throws Exception {
    userId = Account.id(1);
    repoManager = new InMemoryRepositoryManager();
    permissionBackend = mock(PermissionBackend.class);
    PermissionBackend.WithUser withUser = mock(PermissionBackend.WithUser.class);
    PermissionBackend.ForChange forChange = mock(PermissionBackend.ForChange.class);
    when(permissionBackend.currentUser()).thenReturn(withUser);
    when(withUser.change(any(ChangeData.class))).thenReturn(forChange);
    when(forChange.test(ChangePermission.READ)).thenReturn(true);

    projectCache = mock(ProjectCache.class);
    ProjectState projectState = mock(ProjectState.class);
    when(projectState.statePermitsRead()).thenReturn(true);
    when(projectCache.get(any(Project.NameKey.class))).thenReturn(Optional.of(projectState));

    sorter = new RelatedChangesSorter(repoManager, permissionBackend, projectCache);
  }

  @Test
  public void linearChain() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c1 = p.commit().message("Commit 1").create();
    RevCommit c2 = p.commit().parent(c1).message("Commit 2").create();
    RevCommit c3 = p.commit().parent(c2).message("Commit 3").create();

    ChangeData cd1 = newChange(p, c1);
    ChangeData cd2 = newChange(p, c2);
    ChangeData cd3 = newChange(p, c3);

    ImmutableList<ChangeData> changes = ImmutableList.of(cd1, cd2, cd3);

    List<PatchSetData> sortedFrom3 = sorter.sort(changes, cd3.currentPatchSet());
    assertThat(sortedFrom3)
        .containsExactly(
            patchSetData(cd3, c3), patchSetData(cd2, c2), patchSetData(cd1, c1))
        .inOrder();

    List<PatchSetData> sortedFrom1 = sorter.sort(changes, cd1.currentPatchSet());
    assertThat(sortedFrom1)
        .containsExactly(
            patchSetData(cd3, c3), patchSetData(cd2, c2), patchSetData(cd1, c1))
        .inOrder();
  }

  @Test
  public void sortAncestorsLinear() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c1 = p.commit().message("Commit 1").create();
    RevCommit c2 = p.commit().parent(c1).message("Commit 2").create();
    RevCommit c3 = p.commit().parent(c2).message("Commit 3").create();

    ChangeData cd1 = newChange(p, c1);
    ChangeData cd2 = newChange(p, c2);
    ChangeData cd3 = newChange(p, c3);

    ImmutableList<ChangeData> changes = ImmutableList.of(cd1, cd2, cd3);

    List<PatchSetData> ancestorsOf3 = sorter.sortAncestors(changes, cd3.currentPatchSet());
    assertThat(ancestorsOf3)
        .containsExactly(
            patchSetData(cd3, c3), patchSetData(cd2, c2), patchSetData(cd1, c1))
        .inOrder();

    List<PatchSetData> ancestorsOf2 = sorter.sortAncestors(changes, cd2.currentPatchSet());
    assertThat(ancestorsOf2)
        .containsExactly(patchSetData(cd2, c2), patchSetData(cd1, c1))
        .inOrder();

    List<PatchSetData> ancestorsOf1 = sorter.sortAncestors(changes, cd1.currentPatchSet());
    assertThat(ancestorsOf1).containsExactly(patchSetData(cd1, c1)).inOrder();
  }

  @Test
  public void retainsCommitBodyForResults() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit c1 = p.commit().message("Subject 1\n\nBody line 1").create();
    RevCommit c2 = p.commit().parent(c1).message("Subject 2\n\nBody line 2").create();

    ChangeData cd1 = newChange(p, c1);
    ChangeData cd2 = newChange(p, c2);

    ImmutableList<ChangeData> changes = ImmutableList.of(cd1, cd2);
    List<PatchSetData> result = sorter.sort(changes, cd2.currentPatchSet());

    assertThat(result).hasSize(2);
    for (PatchSetData psd : result) {
      assertThat(psd.commit().getShortMessage()).isNotEmpty();
      assertThat(psd.commit().getAuthorIdent()).isNotNull();
    }
  }

  @Test
  public void benchmarkChangeChainSortingLatency() throws Exception {
    int[] chainSizes = {10, 50, 100};
    for (int chainLength : chainSizes) {
      TestRepository<Repo> p = newRepo("bench_repo_" + chainLength);
      List<ChangeData> changes = new ArrayList<>(chainLength);
      RevCommit parent = p.commit().message("Root commit").create();

      for (int i = 1; i <= chainLength; i++) {
        RevCommit cPs1 = p.commit().parent(parent).message("Commit " + i + " ps 1\n\nChange details").create();
        RevCommit cPs2 = p.commit().parent(parent).message("Commit " + i + " ps 2\n\nUpdated change details").create();
        ChangeData cd = newChange(p, cPs1);
        addPatchSet(cd, cPs2);
        changes.add(cd);
        parent = cPs2;
      }

      PatchSet startPs = changes.get(chainLength - 1).currentPatchSet();

      // Warm up JIT
      for (int i = 0; i < 30; i++) {
        var unused1 = sorter.sort(changes, startPs);
        var unused2 = sorter.sortAncestors(changes, startPs);
      }

      // Benchmark measurement
      int iterations = 100;
      long startSortNanos = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        List<PatchSetData> sorted = sorter.sort(changes, startPs);
        assertThat(sorted).hasSize(chainLength);
      }
      long totalSortNanos = System.nanoTime() - startSortNanos;

      long startAncestorsNanos = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        List<PatchSetData> ancestors = sorter.sortAncestors(changes, startPs);
        assertThat(ancestors).hasSize(chainLength);
      }
      long totalAncestorsNanos = System.nanoTime() - startAncestorsNanos;

      double avgSortMicros = (double) totalSortNanos / (iterations * 1000.0);
      double avgAncestorsMicros = (double) totalAncestorsNanos / (iterations * 1000.0);

      System.out.printf(
          "RelatedChangesSorter Benchmark [Chain=%d]: sort() avg = %.2f us, sortAncestors() avg = %.2f us%n",
          chainLength, avgSortMicros, avgAncestorsMicros);

      assertThat(avgSortMicros).isGreaterThan(0.0);
      assertThat(avgAncestorsMicros).isGreaterThan(0.0);
    }
  }

  private ChangeData newChange(TestRepository<Repo> tr, ObjectId id) throws Exception {
    Project.NameKey project = tr.getRepository().getDescription().getProject();
    Change c = TestChanges.newChange(project, userId);
    ChangeData cd = ChangeData.createForTest(project, c.getId(), 1, id);
    cd.setChange(c);
    cd.setPatchSets(ImmutableList.of(cd.currentPatchSet()));
    return cd;
  }

  @CanIgnoreReturnValue
  private PatchSet addPatchSet(ChangeData cd, ObjectId id) throws Exception {
    TestChanges.incrementPatchSet(cd.change());
    PatchSet ps = TestChanges.newPatchSet(cd.change().currentPatchSetId(), id.name(), userId);
    List<PatchSet> patchSets = new ArrayList<>(cd.patchSets());
    patchSets.add(ps);
    cd.setPatchSets(patchSets);
    return ps;
  }

  private TestRepository<Repo> newRepo(String name) throws Exception {
    return new TestRepository<>(repoManager.createRepository(Project.nameKey(name)));
  }

  private static PatchSetData patchSetData(ChangeData cd, RevCommit commit) {
    return PatchSetData.create(cd, cd.currentPatchSet(), commit);
  }
}
