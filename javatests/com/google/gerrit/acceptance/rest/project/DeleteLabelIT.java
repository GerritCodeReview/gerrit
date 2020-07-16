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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.inject.Inject;
import org.junit.Test;

public class DeleteLabelIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void anonymous() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(allProjects.get()).label("Code-Review").delete());
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void notAllowed() throws Exception {
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(allProjects.get()).label("Code-Review").delete());
    assertThat(thrown).hasMessageThat().contains("write refs/meta/config not permitted");
  }

  @Test
  public void nonExisting() throws Exception {
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(allProjects.get()).label("Non-Existing-Review").delete());
    assertThat(thrown).hasMessageThat().contains("Not found: Non-Existing-Review");
  }

  @Test
  public void delete() throws Exception {
    gApi.projects().name(allProjects.get()).label("Code-Review").delete();

    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).label("Code-Review").get());
    assertThat(thrown).hasMessageThat().contains("Not found: Code-Review");
  }

  @Test
  public void defaultCommitMessage() throws Exception {
    gApi.projects().name(allProjects.get()).label("Code-Review").delete();
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Delete label");
  }

  @Test
  public void withCommitMessage() throws Exception {
    gApi.projects().name(allProjects.get()).label("Code-Review").delete("Delete Code-Review label");
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Delete Code-Review label");
  }

  @Test
  public void commitMessageIsTrimmed() throws Exception {
    gApi.projects()
        .name(allProjects.get())
        .label("Code-Review")
        .delete(" Delete Code-Review label ");
    assertThat(
            projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG).getShortMessage())
        .isEqualTo("Delete Code-Review label");
  }
}
