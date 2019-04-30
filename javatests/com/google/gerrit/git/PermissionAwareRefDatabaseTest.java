// Copyright (C) 2017 The Android Open Source Project
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.git.PermissionAwareRefDatabase;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PermissionAwareRefDatabaseTest {
  @Mock private Repository mockRepository;
  @Mock private RefDatabase mockRefDb;
  @Mock private PermissionBackend.ForProject mockForProjectFilter;

  private PermissionAwareRefDatabase refDatabase;

  @Before
  public void setUp() {
    doReturn(mockRefDb).when(mockRepository).getRefDatabase();
    refDatabase = new PermissionAwareRefDatabase(mockRepository, mockForProjectFilter);
  }

  @Test
  public void shouldDelegateClose() {
    refDatabase.close();
    verify(mockRefDb).close();
  }

  @Test
  public void shouldDelegateCreate() throws IOException {
    refDatabase.create();
    verify(mockRefDb).create();
  }

  @Test
  public void shouldDelegateIsNameConflicting() throws IOException {
    final String name = "testRefName";
    doReturn(true).when(mockRefDb).isNameConflicting(name);
    assertThat(refDatabase.isNameConflicting(name)).isTrue();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateANewUpdateIfRefIsAccessible() throws Exception {
    final String name = "testRefName";
    final RefUpdate aRefUpdate = Mockito.mock(RefUpdate.class);
    final Ref aRef = new SimpleRef(name);

    doReturn(aRef).when(mockRefDb).getRef(name);

    doReturn(ImmutableMap.of(name, aRef))
        .when(mockForProjectFilter)
        .filter(
            Mockito.any(Map.class),
            Mockito.same(mockRepository),
            Mockito.any(RefFilterOptions.class));

    doReturn(aRefUpdate).when(mockRefDb).newUpdate(name, true);
    assertThat(refDatabase.newUpdate(name, true)).isSameInstanceAs(aRefUpdate);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateAFailOnlyNewUpdateIfRefIsNotAccessible() throws Exception {
    final String name = "testRefName";

    doReturn(new SimpleRef(name)).when(mockRefDb).getRef(name);

    doReturn(ImmutableMap.of())
        .when(mockForProjectFilter)
        .filter(
            Mockito.any(Map.class),
            Mockito.same(mockRepository),
            Mockito.any(RefFilterOptions.class));

    RefUpdate refUpdate = refDatabase.newUpdate(name, true);

    assertThat(refUpdate.forceUpdate()).isEqualTo(Result.REJECTED_OTHER_REASON);
    assertThat(refUpdate.update()).isEqualTo(Result.REJECTED_OTHER_REASON);
    assertThat(refUpdate.delete()).isEqualTo(Result.REJECTED_OTHER_REASON);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateANewRenameIfRefIsAccessible() throws Exception {
    final String fromName = "fromName";
    final String toName = "toName";
    final RefRename aRefRename = Mockito.mock(RefRename.class);
    final Ref fromRef = new SimpleRef(fromName);
    final Ref toRef = new SimpleRef(toName);

    doReturn(fromRef).when(mockRefDb).getRef(fromName);
    doReturn(toRef).when(mockRefDb).getRef(toName);

    // Bit of a hack to avoid to create a matcher on the parameter of the filter method
    // exploiting the fact that post filtering we collect the entry in the map with the
    // given ref name to handle the result of filtering returning symlinks resolutions
    doReturn(ImmutableMap.of(fromName, fromRef, toName, toRef))
        .when(mockForProjectFilter)
        .filter(
            Mockito.any(Map.class),
            Mockito.same(mockRepository),
            Mockito.any(RefFilterOptions.class));

    doReturn(aRefRename).when(mockRefDb).newRename(fromName, toName);
    assertThat(refDatabase.newRename(fromName, toName)).isSameInstanceAs(aRefRename);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateAFailOnlyNewRenameIfRefIsNotAccessible() throws Exception {
    final String fromName = "fromName";
    final String toName = "toName";
    final Ref fromRef = new SimpleRef(fromName);
    final Ref toRef = new SimpleRef(toName);

    doReturn(fromRef).when(mockRefDb).getRef(fromName);
    doReturn(toRef).when(mockRefDb).getRef(toName);

    doReturn(ImmutableMap.of())
        .when(mockForProjectFilter)
        .filter(
            Mockito.any(Map.class),
            Mockito.same(mockRepository),
            Mockito.any(RefFilterOptions.class));

    RefRename refRename = refDatabase.newRename(fromName, toName);

    assertThat(refRename.rename()).isEqualTo(Result.REJECTED_OTHER_REASON);
  }

  static class SimpleRef implements Ref {
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
