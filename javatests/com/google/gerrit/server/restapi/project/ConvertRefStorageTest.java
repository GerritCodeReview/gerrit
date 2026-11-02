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

package com.google.gerrit.server.restapi.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.RefStorageInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RefStorageNotSupportedException;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectResource;
import java.io.IOException;
import java.text.MessageFormat;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefStorageFormat;
import org.junit.Before;
import org.junit.Test;

public class ConvertRefStorageTest {
  private final Project.NameKey name = Project.nameKey("test");
  private final GitRepositoryManager repoManager = mock(GitRepositoryManager.class);
  private final PermissionBackend permissions = mock(PermissionBackend.class);
  private final PermissionBackend.WithUser userPermissions = mock(PermissionBackend.WithUser.class);
  private final ProjectResource resource = mock(ProjectResource.class);
  private ConvertRefStorage view;

  @Before
  public void setUp() {
    CurrentUser user = mock(CurrentUser.class);
    when(resource.getNameKey()).thenReturn(name);
    when(resource.getUser()).thenReturn(user);
    when(permissions.user(user)).thenReturn(userPermissions);
    view = new ConvertRefStorage(repoManager, permissions);
  }

  @Test
  public void conversionKeepsBackupByDefault() throws Exception {
    RefStorageInput input = new RefStorageInput();
    input.refStorage = "REFTABLE";
    when(repoManager.convertRefStorage(name, RefStorageFormat.REFTABLE, true, true))
        .thenReturn(true);
    assertThat(view.apply(resource, input).value()).isEqualTo("REFTABLE");
    verify(repoManager).convertRefStorage(name, RefStorageFormat.REFTABLE, true, true);
  }

  @Test
  public void conversionHonorsBackupFalse() throws Exception {
    RefStorageInput input = new RefStorageInput();
    input.refStorage = "FILES";
    input.backup = false;
    when(repoManager.convertRefStorage(name, RefStorageFormat.FILES, true, false)).thenReturn(true);
    assertThat(view.apply(resource, input).value()).isEqualTo("FILES");
    verify(repoManager).convertRefStorage(name, RefStorageFormat.FILES, true, false);
  }

  @Test
  public void noOpIsConflict() {
    RefStorageInput input = new RefStorageInput();
    input.refStorage = "FILES";
    assertThrows(ResourceConflictException.class, () -> view.apply(resource, input));
  }

  @Test
  public void leftoverReftableDirIsConflict() throws Exception {
    RefStorageInput input = new RefStorageInput();
    input.refStorage = "REFTABLE";
    when(repoManager.convertRefStorage(name, RefStorageFormat.REFTABLE, true, true))
        .thenThrow(new IOException(JGitText.get().reftableDirExists));
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> view.apply(resource, input));
    assertThat(thrown).hasMessageThat().contains("Remove it before converting");
  }

  @Test
  public void leftoverPackedRefsIsConflict() throws Exception {
    RefStorageInput input = new RefStorageInput();
    input.refStorage = "FILES";
    when(repoManager.convertRefStorage(name, RefStorageFormat.FILES, true, true))
        .thenThrow(
            new IOException(
                MessageFormat.format(JGitText.get().fileAlreadyExists, Constants.PACKED_REFS)));
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> view.apply(resource, input));
    assertThat(thrown).hasMessageThat().contains("Remove it before converting");
  }

  @Test
  public void unrelatedIoErrorPropagates() throws Exception {
    RefStorageInput input = new RefStorageInput();
    input.refStorage = "REFTABLE";
    when(repoManager.convertRefStorage(name, RefStorageFormat.REFTABLE, true, true))
        .thenThrow(new IOException("disk failure"));
    assertThrows(IOException.class, () -> view.apply(resource, input));
  }

  @Test
  public void unsupportedManagerIsConflict() throws Exception {
    RefStorageInput input = new RefStorageInput();
    input.refStorage = "REFTABLE";
    when(repoManager.convertRefStorage(name, RefStorageFormat.REFTABLE, true, true))
        .thenThrow(new RefStorageNotSupportedException("Unsupported manager"));
    assertThrows(ResourceConflictException.class, () -> view.apply(resource, input));
  }

  @Test
  public void missingFormatIsBadRequest() {
    assertThrows(BadRequestException.class, () -> view.apply(resource, new RefStorageInput()));
    verifyNoInteractions(repoManager);
  }

  @Test
  public void requiresServerAdministrator() throws Exception {
    RefStorageInput input = new RefStorageInput();
    input.refStorage = "REFTABLE";
    doThrow(new AuthException("administrateServer required"))
        .when(userPermissions)
        .check(GlobalPermission.ADMINISTRATE_SERVER);
    assertThrows(AuthException.class, () -> view.apply(resource, input));
    verifyNoInteractions(repoManager);
  }
}
