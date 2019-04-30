// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.git;

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.git.PermissionAwareDfsRepository;
import com.google.gerrit.server.git.PermissionAwareRefDatabase;
import com.google.gerrit.server.git.PermissionAwareRepository;
import com.google.gerrit.server.permissions.PermissionBackend;
import java.io.File;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Test;

public class PermissionAwareRepositoryTest extends PermissionAwareDelegateTestBase<Repository> {
  Repository repository;
  PermissionBackend.ForProject forProject;

  @Override
  protected Set<String> methodsWithSpecificTest() {
    return ImmutableSet.of(
        "getRefDatabase", // returns a different object, has a special test
        "toString" // ignored
        );
  }

  @Override
  protected Object[] additionalMockObjectsForDelegateCheck() {
    return new Object[] {forProject};
  }

  @Override
  protected void setExpectationsForAdditionalMockObjects() {}

  @Override
  protected Repository delegate() {
    return repository;
  }

  @Before
  public void setUp() {
    forProject = createMock(PermissionBackend.ForProject.class);
  }

  @Test
  public void permissionAwareRepositoryShouldNotDelegateGetRefDatabase() {
    PermissionAwareRepository permissionAwareRepository = buildPermissionAwareRepo();
    replay(repository, forProject);
    assertThat(permissionAwareRepository.getRefDatabase())
        .isInstanceOf(PermissionAwareRefDatabase.class);
    verify(repository, forProject);
  }

  @Test
  public void permissionAwareDfsRepositoryShouldNotDelegateGetRefDatabase() {
    PermissionAwareRepository permissionAwareRepository = buildPermissionAwareRepo();
    replay(repository, forProject);
    assertThat(permissionAwareRepository.getRefDatabase())
        .isInstanceOf(PermissionAwareRefDatabase.class);
    verify(repository, forProject);
  }

  protected PermissionAwareRepository buildPermissionAwareRepo() {
    repository = createMock(Repository.class);

    expect(repository.getFS()).andReturn(FS.DETECTED);
    expect(repository.getDirectory()).andReturn(new File(""));
    expect(repository.getIndexFile()).andReturn(new File(""));
    expect(repository.getWorkTree()).andReturn(new File(""));
    expect(repository.isBare()).andReturn(false);

    replay(repository);

    PermissionAwareRepository permissionAwareRepository =
        new PermissionAwareRepository(repository, forProject);

    reset(repository);

    return permissionAwareRepository;
  }

  protected PermissionAwareDfsRepository buildPermissionAwareDfsRepo() {
    repository = createMock(DfsRepository.class);

    expect(repository.getFS()).andReturn(FS.DETECTED);
    // DfsRepo requires null file system references
    expect(repository.getDirectory()).andReturn(null);
    expect(repository.getIndexFile()).andReturn(null);
    expect(repository.getWorkTree()).andReturn(null);
    expect(repository.isBare()).andReturn(false);

    replay(repository);

    PermissionAwareDfsRepository permissionAwareRepository =
        new PermissionAwareDfsRepository((DfsRepository) repository, forProject);

    reset(repository);

    return permissionAwareRepository;
  }

  @Test
  public void permissionAwareRepositoryShouldDelegateAllPublicMethods() {
    repository = createMock(DfsRepository.class);
    shouldDelegateAllPublicMethods(this::buildPermissionAwareRepo);
  }

  @Test
  public void permissionAwareDfsRepositoryShouldDelegateAllPublicMethods() {
    repository = createMock(DfsRepository.class);
    shouldDelegateAllPublicMethods(this::buildPermissionAwareDfsRepo);
  }
}
