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

package com.google.gerrit.server.query.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.testing.FakeStoredValue;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.testing.TestChanges;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class CurrentRevisionParentsTest {
  private static final String A = "a".repeat(40);
  private static final String B = "b".repeat(40);

  @Test
  public void missingSnapshotNeverReadsGit() {
    ChangeData change = change();
    assertThat(
            ChangeField.CURRENT_REVISION_PARENTS_SPEC.setIfPossible(
                change, new FakeStoredValue((byte[]) null)))
        .isTrue();
    assertThat(change.indexedCurrentRevisionParents()).isNull();
    assertThat(ChangeField.CURRENT_REVISION_PARENTS_SPEC.get(change)).isNull();
    assertThat(ChangeField.CURRENT_PARENT_COMMIT_SPEC.get(change)).isEmpty();
    change.setStorageConstraint(ChangeData.StorageConstraint.INDEX_PRIMARY_NOTEDB_SECONDARY);
    // The strict stored getter must not use the instance's absent repository manager.
    assertThat(change.indexedCurrentRevisionParents()).isNull();
  }

  @Test
  public void storedRoundTripPreservesRootAndMergeParentOrder() {
    ChangeData source = change();
    ChangeData restored = change();
    for (List<String> parents : List.of(List.<String>of(), List.of(B, A))) {
      var expected = new CurrentRevisionParents(1, 1, ObjectId.zeroId().name(), parents);
      source.setCurrentRevisionParents(expected);
      byte[] stored = ChangeField.CURRENT_REVISION_PARENTS_SPEC.get(source);
      assertThat(
              ChangeField.CURRENT_REVISION_PARENTS_SPEC.setIfPossible(
                  restored, new FakeStoredValue(stored)))
          .isTrue();
      assertThat(restored.indexedCurrentRevisionParents()).isEqualTo(expected);
      assertThat(ChangeField.CURRENT_PARENT_COMMIT_SPEC.get(source))
          .containsExactlyElementsIn(parents)
          .inOrder();
    }
  }

  @Test
  public void onlyMergedChangesOmitStoredSnapshotAndParentPostings() {
    ChangeData change = change();
    change.setCurrentRevisionParents(new CurrentRevisionParents(1, 1, A, List.of(B)));
    change.change().setStatus(Change.Status.MERGED);
    assertThat(ChangeField.CURRENT_REVISION_PARENTS_SPEC.get(change)).isNull();
    assertThat(ChangeField.CURRENT_PARENT_COMMIT_SPEC.get(change)).isEmpty();
    change.change().setStatus(Change.Status.ABANDONED);
    assertThat(ChangeField.CURRENT_REVISION_PARENTS_SPEC.get(change)).isNotNull();
    assertThat(ChangeField.CURRENT_PARENT_COMMIT_SPEC.get(change)).containsExactly(B);
    change.change().setStatus(Change.Status.NEW);
    assertThat(ChangeField.CURRENT_PARENT_COMMIT_SPEC.get(change)).containsExactly(B);
  }

  @Test
  public void parentPredicateNormalizesCompleteCommitIds() {
    assertThat(ChangePredicates.parentCommit(A.toUpperCase()).toString())
        .isEqualTo(ChangePredicates.parentCommit(A).toString());
    assertThrows(IllegalArgumentException.class, () -> ChangePredicates.parentCommit("abcd"));
    assertThrows(
        IllegalArgumentException.class, () -> ChangePredicates.parentCommit("x".repeat(40)));
  }

  private static ChangeData change() {
    Project.NameKey project = Project.nameKey("project");
    ChangeData data = ChangeData.createForTest(project, Change.id(1), 1, ObjectId.zeroId());
    data.setChange(TestChanges.newChange(project, Account.id(1), 1));
    data.setStorageConstraint(ChangeData.StorageConstraint.INDEX_ONLY);
    return data;
  }
}
