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
import static com.google.gerrit.git.DelegatedMethodTestingSupport.assertMethodIsDelegated;
import static com.google.gerrit.git.DelegatedMethodTestingSupport.methodsForDelegationTest;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.git.PermissionAwareReadOnlyRefDatabase;
import com.google.gerrit.server.permissions.PermissionBackend;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.powermock.api.easymock.PowerMock;

@RunWith(Parameterized.class)
public abstract class PermissionAwareRepositoryTestBase {

  protected final Method repositoryMethod;
  protected final String repositoryMethodName;

  protected PermissionBackend.ForProject forProject;

  public PermissionAwareRepositoryTestBase(Method repositoryMethod, String repositoryMethodName) {
    this.repositoryMethod = repositoryMethod;
    this.repositoryMethodName = repositoryMethodName;
  }

  @Before
  public void setUp() {
    forProject = PowerMock.createMock(PermissionBackend.ForProject.class);
  }

  private static Set<String> methodsWithDedicatedTest =
      ImmutableSet.of(
          "exactRef", // final method, problems using EasyMock to test it
          "findRef", // final method, problems using EasyMock to test it
          "getRefDatabase", // returns a different object, has a special test
          "toString" // ignored
          );

  @Parameterized.Parameters(name = "{1} is delegated")
  public static Collection<Object[]> data() {
    return methodsForDelegationTest(Repository.class)
        .map(method -> new Object[] {method, method.getName()})
        .collect(Collectors.toList());
  }

  @Test
  public void doesNotDelegateWhenAskedToGetGetRefDatabase() {
    assumeThat(repositoryMethodName, is("getRefDatabase"));

    Repository permissionAwareRepository = buildPermissionAwareRepo();
    replay(repository(), forProject);
    assertThat(permissionAwareRepository.getRefDatabase())
        .isInstanceOf(PermissionAwareReadOnlyRefDatabase.class);
    verify(repository(), forProject);
  }

  @Test
  public void isDelegated() throws Exception {
    assumeFalse(methodsWithDedicatedTest.contains(repositoryMethodName));

    assertMethodIsDelegated(
        repositoryMethod, buildPermissionAwareRepo(), repository(), new Object[] {forProject});
  }

  protected abstract Repository buildPermissionAwareRepo();

  protected abstract Repository repository();
}
