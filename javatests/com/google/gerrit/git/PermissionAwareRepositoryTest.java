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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;

import com.google.gerrit.server.git.PermissionAwareRepository;
import java.io.File;
import java.lang.reflect.Method;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Ignore;
import org.powermock.api.easymock.PowerMock;

@Ignore
public class PermissionAwareRepositoryTest extends PermissionAwareRepositoryTestBase {

  private Repository repository;

  public PermissionAwareRepositoryTest(Method repositoryMethod, String repositoryMethodName) {
    super(repositoryMethod, repositoryMethodName);
  }

  @Override
  @Before
  public void setUp() {
    super.setUp();
    repository = PowerMock.createMock(Repository.class);
  }

  @Override
  protected Repository buildPermissionAwareRepo() {
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

  @Override
  protected Repository repository() {
    return repository;
  }
}
