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

package com.google.gerrit.server.project;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ReceiveCommitControl#canUploadMerge} ref selection. */
public class ReceiveCommitControlTest {
  private ReceiveCommitControl control;
  private RevCommit nonMerge;
  private RevCommit merge;

  @Before
  public void setUp() throws Exception {
    control = new ReceiveCommitControl(new PersonIdent("gerrit", "gerrit@example.com"));
    InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
    try (TestRepository<Repo> repo =
        new TestRepository<>(repoManager.createRepository(Project.nameKey("test")))) {
      RevCommit base = repo.commit().create();
      RevCommit other = repo.commit().create();
      nonMerge = repo.commit().parent(base).create();
      merge = repo.commit().parent(base).parent(other).create();
    }
  }

  @Test
  public void nonMergeIsAllowedWithoutConsultingPermissions() throws Exception {
    PermissionBackend.ForRef destRef = mock(PermissionBackend.ForRef.class);
    PermissionBackend.ForRef reviewRef = mock(PermissionBackend.ForRef.class);

    assertThat(control.canUploadMerge(destRef, reviewRef, /* directPush= */ true, nonMerge))
        .isTrue();

    verify(destRef, never()).test(RefPermission.MERGE);
    verify(reviewRef, never()).test(RefPermission.MERGE);
  }

  @Test
  public void directMergePushChecksDestinationRefOnly() throws Exception {
    PermissionBackend.ForRef destRef = mock(PermissionBackend.ForRef.class);
    PermissionBackend.ForRef reviewRef = mock(PermissionBackend.ForRef.class);
    when(destRef.test(RefPermission.MERGE)).thenReturn(true);

    assertThat(control.canUploadMerge(destRef, reviewRef, /* directPush= */ true, merge)).isTrue();

    verify(destRef).test(RefPermission.MERGE);
    verify(reviewRef, never()).test(RefPermission.MERGE);
  }

  @Test
  public void directMergePushDeniedWhenDestinationRefLacksPermission() throws Exception {
    PermissionBackend.ForRef destRef = mock(PermissionBackend.ForRef.class);
    PermissionBackend.ForRef reviewRef = mock(PermissionBackend.ForRef.class);
    when(destRef.test(RefPermission.MERGE)).thenReturn(false);

    assertThat(control.canUploadMerge(destRef, reviewRef, /* directPush= */ true, merge)).isFalse();

    verify(reviewRef, never()).test(RefPermission.MERGE);
  }

  @Test
  public void reviewMergeUploadChecksReviewRefOnly() throws Exception {
    PermissionBackend.ForRef destRef = mock(PermissionBackend.ForRef.class);
    PermissionBackend.ForRef reviewRef = mock(PermissionBackend.ForRef.class);
    when(reviewRef.test(RefPermission.MERGE)).thenReturn(true);

    assertThat(control.canUploadMerge(destRef, reviewRef, /* directPush= */ false, merge)).isTrue();

    verify(reviewRef).test(RefPermission.MERGE);
    verify(destRef, never()).test(RefPermission.MERGE);
  }
}
