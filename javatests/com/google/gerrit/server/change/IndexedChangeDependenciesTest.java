// Copyright (C) 2026 Monty Taylor
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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.IndexedDependenciesInfo;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.CurrentRevisionParents;
import com.google.gerrit.testing.TestChanges;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class IndexedChangeDependenciesTest {
  private static final Project.NameKey PROJECT = Project.nameKey("project");
  private static final String A = "a".repeat(40);
  private static final String B = "b".repeat(40);
  private static final String C = "c".repeat(40);
  private static final String D = "d".repeat(40);

  @Test
  public void resolvesHistoricalPatchSetAndDeduplicatesRepeatedRows() {
    ChangeData parent = change(PROJECT, 1, 2, B, List.of());
    parent.setPatchSets(
        List.of(
            TestChanges.newPatchSet(PatchSet.id(parent.getId(), 1), A, Account.id(1)),
            parent.currentPatchSet()));
    ChangeData child = change(PROJECT, 2, 1, C, List.of(A));

    IndexedDependenciesInfo info = resolve(parent, child, parent).get(child.getId());
    assertThat(info.revision).isEqualTo(C);
    assertThat(info.patchSetNumber).isEqualTo(1);
    assertThat(info.parents).hasSize(1);
    assertThat(info.parents.get(0).changeNumber).isEqualTo(1);
    assertThat(info.parents.get(0).patchSetNumber).isEqualTo(1);
    assertThat(info.parents.get(0).commitId).isEqualTo(A);
  }

  @Test
  public void commitIdentityIsScopedToProject() {
    ChangeData otherProject = change(Project.nameKey("other"), 1, 1, A, List.of());
    ChangeData parent = change(PROJECT, 2, 1, A, List.of());
    ChangeData child = change(PROJECT, 3, 1, B, List.of(A));

    assertThat(resolve(otherProject, child).get(child.getId()).parents).isEmpty();
    assertThat(resolve(otherProject, parent, child).get(child.getId()).parents.get(0).changeNumber)
        .isEqualTo(2);
  }

  @Test
  public void ambiguousChangeOrPatchSetIdentityIsOmitted() {
    ChangeData first = change(PROJECT, 1, 1, A, List.of());
    ChangeData second = change(PROJECT, 2, 1, A, List.of());
    ChangeData child = change(PROJECT, 3, 1, B, List.of(A));
    assertThat(resolve(first, second, child).get(child.getId()).parents).isEmpty();

    first.setPatchSets(
        List.of(
            first.currentPatchSet(),
            TestChanges.newPatchSet(PatchSet.id(first.getId(), 2), A, Account.id(1))));
    assertThat(resolve(first, child).get(child.getId()).parents).isEmpty();
  }

  @Test
  public void missingSnapshotDoesNotLazyLoadEvenWhenOtherOptionsAllowIt() {
    ChangeData missing = change(PROJECT, 1, 1, A, List.of());
    missing.setCurrentRevisionParents(null);
    assertThat(resolve(missing)).isEmpty();
    missing.setStorageConstraint(ChangeData.StorageConstraint.INDEX_PRIMARY_NOTEDB_SECONDARY);
    // createForTest has no repository manager or query provider. A fallback read would fail.
    assertThat(resolve(missing)).isEmpty();
  }

  @Test
  public void inconsistentOrUnsupportedSnapshotIsOmitted() {
    ChangeData change = change(PROJECT, 1, 1, A, List.of());
    for (CurrentRevisionParents snapshot :
        List.of(
            new CurrentRevisionParents(1, 2, A, List.of()),
            new CurrentRevisionParents(1, 1, B, List.of()),
            new CurrentRevisionParents(2, 1, A, List.of()),
            new CurrentRevisionParents(2, 0, null, null),
            new CurrentRevisionParents(1, 1, A, null))) {
      change.setCurrentRevisionParents(snapshot);
      assertThat(resolve(change)).isEmpty();
    }
  }

  @Test
  public void preservesDirectParentOrderAndOmitsOffPageAndSelfReferences() {
    ChangeData first = change(PROJECT, 1, 1, A, List.of());
    ChangeData second = change(PROJECT, 2, 1, B, List.of());
    ChangeData child = change(PROJECT, 3, 1, C, List.of(B, D, A, C));

    IndexedDependenciesInfo info = resolve(first, second, child).get(child.getId());
    assertThat(info.parents.stream().map(p -> p.commitId).toList()).containsExactly(B, A).inOrder();
    assertThat(resolve(child).get(child.getId()).parents).isEmpty();
  }

  @Test
  public void rootCommitSnapshotIsAvailableWithEmptyParents() {
    ChangeData root = change(PROJECT, 1, 1, A, List.of());
    assertThat(resolve(root).get(root.getId()).parents).isEmpty();
  }

  @Test
  public void mergedChangesAreTraversalBoundariesEvenWithStaleSnapshots() {
    ChangeData merged = change(PROJECT, 1, 1, A, List.of(B));
    merged.change().setStatus(Change.Status.MERGED);
    ChangeData abandoned = change(PROJECT, 2, 1, B, List.of(A));
    abandoned.change().setStatus(Change.Status.ABANDONED);
    ChangeData child = change(PROJECT, 3, 1, C, List.of(B));
    child.change().setWorkInProgress(true);

    Map<Change.Id, IndexedDependenciesInfo> result = resolve(merged, abandoned, child);
    assertThat(result).doesNotContainKey(merged.getId());
    assertThat(result.get(abandoned.getId()).parents).isEmpty();
    assertThat(result.get(child.getId()).parents).hasSize(1);
    assertThat(result.get(child.getId()).parents.get(0).changeNumber).isEqualTo(2);
  }

  private static Map<Change.Id, IndexedDependenciesInfo> resolve(ChangeData... changes) {
    return IndexedChangeDependencies.resolve(List.of(changes));
  }

  private static ChangeData change(
      Project.NameKey project, int id, int patchSet, String revision, List<String> parents) {
    // No injected database, repository manager, or query provider: all data comes from index
    // fields.
    ChangeData cd =
        ChangeData.createForTest(project, Change.id(id), patchSet, ObjectId.fromString(revision));
    PatchSet current = cd.currentPatchSet();
    Change change = TestChanges.newChange(project, Account.id(1), id);
    change.setCurrentPatchSet(current.id(), "subject", "subject");
    cd.setChange(change);
    cd.setPatchSets(List.of(current));
    cd.setCurrentRevisionParents(new CurrentRevisionParents(1, patchSet, revision, parents));
    cd.setStorageConstraint(ChangeData.StorageConstraint.INDEX_ONLY);
    return cd;
  }
}
