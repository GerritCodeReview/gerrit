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

import com.google.common.base.Defaults;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.git.PermissionAwareDfsRepository;
import com.google.gerrit.server.git.PermissionAwareReadOnlyRefDatabase;
import com.google.gerrit.server.git.PermissionAwareRepository;
import com.google.gerrit.server.permissions.PermissionBackend;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.easymock.EasyMockSupport;
import org.easymock.IExpectationSetters;
import org.easymock.internal.AssertionErrorWrapper;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Test;

public class PermissionAwareRepositoryTest extends EasyMockSupport {
  Repository repository;
  PermissionBackend.ForProject forProject;

  private final Set<String> methodsWithSpecificTest =
      ImmutableSet.of(
          "exactRef", // final method, problems using EasyMock to test it
          "findRef", // final method, problems using EasyMock to test it
          "getRefDatabase", // returns a different object, has a special test
          "toString" // ignored
          );

  @Before
  public void setUp() {
    forProject = createMock(PermissionBackend.ForProject.class);
  }

  @Test
  public void permissionAwareRepositoryShouldNotDelegateGetRefDatabase() {
    PermissionAwareRepository permissionAwareRepository = buildPermissionAwareRepo();
    replay(repository, forProject);
    assertThat(permissionAwareRepository.getRefDatabase())
        .isInstanceOf(PermissionAwareReadOnlyRefDatabase.class);
    verify(repository, forProject);
  }

  @Test
  public void permissionAwareDfsRepositoryShouldNotDelegateGetRefDatabase() {
    PermissionAwareRepository permissionAwareRepository = buildPermissionAwareRepo();
    replay(repository, forProject);
    assertThat(permissionAwareRepository.getRefDatabase())
        .isInstanceOf(PermissionAwareReadOnlyRefDatabase.class);
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
    shouldDelegateAllPublicMethods(this::buildPermissionAwareRepo);
  }

  @Test
  public void permissionAwareDfsRepositoryShouldDelegateAllPublicMethods() {
    shouldDelegateAllPublicMethods(this::buildPermissionAwareDfsRepo);
  }

  protected void shouldDelegateAllPublicMethods(Supplier<Repository> testObjectBuilder) {
    final List<String> notDelegatedMethods =
        Arrays.stream(Repository.class.getMethods())
            .filter(
                method ->
                    !(method.getDeclaringClass() != Repository.class
                        || Modifier.isStatic(method.getModifiers())
                        || hasSpecialTest(method)))
            .filter(method -> !isDelegated(testObjectBuilder, method))
            .map(Method::getName)
            .collect(Collectors.toList());

    assertThat(notDelegatedMethods).isEqualTo(ImmutableList.of());
  }

  private boolean isDelegated(Supplier<Repository> testObjectBuilder, Method repositoryMethod) {
    try {
      Repository toTest = testObjectBuilder.get();

      reset(repository, forProject);

      List<Object> parametersValue =
          Arrays.stream(repositoryMethod.getParameters())
              .map(parameter -> defaultValueFor(parameter.getType()))
              .collect(Collectors.toList());

      Object expected = defaultValueFor(repositoryMethod.getReturnType());

      IExpectationSetters<Object> expectDelegateCalled =
          expect(repositoryMethod.invoke(repository, parametersValue.toArray()));
      if (repositoryMethod.getReturnType() != Void.TYPE) {
        expectDelegateCalled.andReturn(expected);
      }

      replay(repository, forProject);

      if (repositoryMethod.getReturnType() != Void.TYPE) {
        assertThat(repositoryMethod.invoke(toTest, parametersValue.toArray())).isEqualTo(expected);
      } else {
        repositoryMethod.invoke(toTest, parametersValue.toArray());
      }

      verify(repository, forProject);

      return true;
    } catch (AssertionError | IllegalStateException | AssertionErrorWrapper methodNotDelegated) {
      return false;
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new RuntimeException(
          "Cannot invoke method " + repositoryMethod.getName() + " via reflection", e);
    }
  }

  private boolean hasSpecialTest(Method repositoryMethod) {
    return methodsWithSpecificTest.contains(repositoryMethod.getName());
  }

  private Object defaultValueFor(Class<?> type) {
    if (type == String.class) return "";

    if (type == File.class) return new File("");

    return Defaults.defaultValue(type);
  }

  public static class SimpleRef implements Ref {
    private final String name;

    SimpleRef(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean isSymbolic() {
      return false;
    }

    @Override
    public Ref getLeaf() {
      return null;
    }

    @Override
    public Ref getTarget() {
      return null;
    }

    @Override
    public ObjectId getObjectId() {
      return ObjectId.zeroId();
    }

    @Override
    public ObjectId getPeeledObjectId() {
      return ObjectId.zeroId();
    }

    @Override
    public boolean isPeeled() {
      return false;
    }

    @Override
    public Storage getStorage() {
      return Storage.NETWORK;
    }
  }
}
