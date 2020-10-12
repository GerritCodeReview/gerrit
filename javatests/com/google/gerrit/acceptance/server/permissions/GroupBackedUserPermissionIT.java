// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.permissions;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.group.testing.TestGroupBackend;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.query.change.GroupBackedUser;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import javax.inject.Inject;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/** Tests that permission logic used by {@link GroupBackedUser} works as expected. */
public class GroupBackedUserPermissionIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private PermissionBackend permissionBackend;
  @Inject private ChangeNotes.Factory changeNotesFactory;

  private final TestGroupBackend testGroupBackend = new TestGroupBackend();
  private final AccountGroup.UUID externalGroup = AccountGroup.uuid("testbackend:test");

  @Before
  public void setUp() {
    // Allow only read on refs/heads/master by default
    projectOperations
        .project(allProjects)
        .forUpdate()
        .remove(permissionKey(Permission.READ).ref("refs/*").group(ANONYMOUS_USERS))
        .add(allow(Permission.READ).ref("refs/heads/master").group(ANONYMOUS_USERS))
        .update();
  }

  @Override
  public Module createModule() {
    /** Binding a {@link TestGroupBackend} to test adding external groups * */
    return new AbstractModule() {
      @Override
      protected void configure() {
        DynamicSet.bind(binder(), GroupBackend.class).toInstance(testGroupBackend);
      }
    };
  }

  @Test
  public void defaultRefFilter_changeVisibilityIsAgnosticOfProvidedGroups() throws Exception {
    GroupBackedUser user =
        new GroupBackedUser(ImmutableSet.of(ANONYMOUS_USERS, REGISTERED_USERS, externalGroup));
    Change.Id changeOnMaster = changeOperations.newChange().project(project).create();
    Change.Id changeOnRefsMetaConfig =
        changeOperations.newChange().project(project).branch("refs/meta/config").create();
    // Check that only the change on the default branch is visible
    assertThat(getVisibleRefNames(user))
        .containsExactly(
            "HEAD",
            "refs/heads/master",
            RefNames.changeMetaRef(changeOnMaster),
            RefNames.patchSetRef(PatchSet.id(changeOnMaster, 1)));
    // Grant access
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/meta/config").group(externalGroup))
        .update();
    // Check that both changes are visible now
    assertThat(getVisibleRefNames(user))
        .containsExactly(
            "HEAD",
            "refs/heads/master",
            "refs/meta/config",
            RefNames.changeMetaRef(changeOnMaster),
            RefNames.patchSetRef(PatchSet.id(changeOnMaster, 1)),
            RefNames.changeMetaRef(changeOnRefsMetaConfig),
            RefNames.patchSetRef(PatchSet.id(changeOnRefsMetaConfig, 1)));
  }

  @Test
  public void defaultRefFilter_refVisibilityIsAgnosticOfProvidedGroups() throws Exception {
    GroupBackedUser user =
        new GroupBackedUser(ImmutableSet.of(ANONYMOUS_USERS, REGISTERED_USERS, externalGroup));
    // Check that refs/meta/config isn't visible by default
    assertThat(getVisibleRefNames(user)).containsExactly("HEAD", "refs/heads/master");
    // Grant access
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/meta/config").group(externalGroup))
        .update();
    // Check that refs/meta/config became visible
    assertThat(getVisibleRefNames(user))
        .containsExactly("HEAD", "refs/heads/master", "refs/meta/config");
  }

  @Test
  public void changeVisibility_changeOnInvisibleBranchNotVisible() throws Exception {
    // Create a change that is not visible to members of 'externalGroup'
    Change.Id invisibleChange =
        changeOperations.newChange().project(project).branch("refs/meta/config").create();
    GroupBackedUser user =
        new GroupBackedUser(ImmutableSet.of(ANONYMOUS_USERS, REGISTERED_USERS, externalGroup));
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                permissionBackend
                    .user(user)
                    .change(changeNotesFactory.create(project, invisibleChange))
                    .check(ChangePermission.READ));
    assertThat(thrown).hasMessageThat().isEqualTo("read not permitted");
  }

  @Test
  public void changeVisibility_changeOnBranchVisibleToAnonymousIsVisible() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    GroupBackedUser user =
        new GroupBackedUser(ImmutableSet.of(ANONYMOUS_USERS, REGISTERED_USERS, externalGroup));
    permissionBackend
        .user(user)
        .change(changeNotesFactory.create(project, changeId))
        .check(ChangePermission.READ);
  }

  @Test
  public void changeVisibility_changeOnBranchVisibleToRegisteredUsersIsVisible() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    GroupBackedUser user =
        new GroupBackedUser(ImmutableSet.of(ANONYMOUS_USERS, REGISTERED_USERS, externalGroup));
    blockAnonymousRead();
    permissionBackend
        .user(user)
        .change(changeNotesFactory.create(project, changeId))
        .check(ChangePermission.READ);
  }

  private ImmutableList<String> getVisibleRefNames(CurrentUser user) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      return permissionBackend.user(user).project(project)
          .filter(
              repo.getRefDatabase().getRefs(), repo, PermissionBackend.RefFilterOptions.defaults())
          .stream()
          .map(Ref::getName)
          .collect(toImmutableList());
    }
  }
}
