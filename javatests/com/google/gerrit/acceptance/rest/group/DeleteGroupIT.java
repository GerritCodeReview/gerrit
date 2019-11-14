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

package com.google.gerrit.acceptance.rest.group;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.common.data.Permission.OWNER;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.group.testing.TestGroupBackend;
import com.google.inject.Inject;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class DeleteGroupIT extends AbstractDaemonTest {
  @Inject private DynamicSet<GroupBackend> groupBackends;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  private final TestGroupBackend testGroupBackend = new TestGroupBackend();

  @Test
  public void nonAdminCannotDeleteGroup() throws Exception {
    String g = createGroup("test", true);
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown = assertThrows(AuthException.class, () -> gApi.groups().id(g).delete());
    assertThat(thrown).hasMessageThat().contains("cannot delete group");
  }

  @Test
  public void cannotDeleteGroupThatOwnsOtherGroup() throws Exception {
    String parent = createGroup("parent");
    createGroup("child", parent);
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> gApi.groups().id(parent).delete());
    assertThat(thrown)
        .hasMessageThat()
        .contains("cannot delete group that is owner of other groups");
  }

  @Test
  public void deleteGroup() throws Exception {
    String group = createGroup("group");
    assertThat(gApi.groups().id(group).get()).isNotNull();
    GroupReference groupReference;

    try (Repository repo = repoManager.openRepository(Project.nameKey("All-Users"))) {
      // Check existence in GroupNameNotes.
      groupReference = GroupNameNotes.loadGroup(repo, AccountGroup.nameKey(group)).get();
      assertThat(groupReference).isNotNull();
      // Check existence in the database.
      assertThat(
              repo.getRefDatabase()
                  .getRefsByPrefix(
                      String.format(
                          "refs/groups/%s/%s",
                          groupReference.getUUID().toString().substring(0, 2),
                          groupReference.getUUID())))
          .isNotEmpty();
    }
    gApi.groups().id(group).delete();

    try (Repository repo = repoManager.openRepository(Project.nameKey("All-Users"))) {
      // Check deletion from GroupNameNotes
      assertThat(GroupNameNotes.loadGroup(repo, AccountGroup.nameKey(group)))
          .isEqualTo(Optional.empty());
      // Check deletion from the database.
      assertThat(
              repo.getRefDatabase()
                  .getRefsByPrefix(
                      String.format(
                          "refs/groups/%s/%s",
                          groupReference.getUUID().toString().substring(0, 2),
                          groupReference.getUUID())))
          .isEmpty();
    }
    assertThrows(ResourceNotFoundException.class, () -> gApi.groups().id(group));
  }

  @Test
  public void cannotDeleteSystemGroup() throws Exception {
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.groups().id(SystemGroupBackend.REGISTERED_USERS.get()).delete());
    assertThat(thrown).hasMessageThat().contains("cannot delete system group");
  }

  @Test
  public void cannotDeleteExternalGroup() throws Exception {
    groupBackends.add("test", testGroupBackend);
    GroupDescription.Basic group = testGroupBackend.create("test");
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.groups().id(group.getGroupUUID().get()).delete());
    assertThat(thrown).hasMessageThat().contains("cannot delete external group");
  }

  @Test
  public void cannotDeleteGroupUsedInRefPermissions() throws Exception {
    String group = createGroup("group");
    assertThat(gApi.groups().id(group).get()).isNotNull();
    Optional<GroupReference> groupRef = Optional.empty();
    try (Repository repo = repoManager.openRepository(Project.nameKey("All-Users"))) {
      groupRef = GroupNameNotes.loadGroup(repo, AccountGroup.nameKey(group));
    }
    assertThat(groupRef).isPresent();
    projectOperations
        .project(Project.nameKey("All-Users"))
        .forUpdate()
        .add(allow(OWNER).ref("refs/*").group(groupRef.get().getUUID()))
        .update();
    assertThrows(ResourceConflictException.class, () -> gApi.groups().id(group).delete());
  }
}
