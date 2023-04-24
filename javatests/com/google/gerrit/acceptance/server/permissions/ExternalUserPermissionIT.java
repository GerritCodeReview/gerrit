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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.ExternalUser;
import com.google.gerrit.server.PropertyMap;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.util.Collection;
import java.util.Set;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/** Tests that permission logic used by {@link ExternalUser} works as expected. */
public class ExternalUserPermissionIT extends AbstractDaemonTest {
  private static final AccountGroup.UUID EXTERNAL_GROUP =
      AccountGroup.uuid("company-auth:it-department");

  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private PermissionBackend permissionBackend;
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private ExternalUser.Factory externalUserFactory;
  @Inject private GroupOperations groupOperations;
  @Inject private ExternalIdKeyFactory externalIdKeyFactory;

  @Before
  public void setUp() {
    // Allow only read on refs/heads/master by default
    projectOperations
        .project(allProjects)
        .forUpdate()
        .remove(permissionKey(Permission.READ).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .add(allow(Permission.READ).ref("refs/heads/master").group(ANONYMOUS_USERS))
        .update();
  }

  @Override
  public Module createModule() {
    /**
     * Binding a {@link GroupBackend} that pretends a user is part of a group if the external ID
     * starts with the group UUID.
     *
     * <p>Example: Users "company-auth:it-department-1" and "company-auth:it-department-2" are a
     * member of the group "company-auth:it-department"
     */
    return new AbstractModule() {
      @Override
      protected void configure() {
        DynamicSet.bind(binder(), GroupBackend.class)
            .toInstance(
                new GroupBackend() {
                  @Override
                  public boolean handles(AccountGroup.UUID uuid) {
                    return uuid.get().startsWith("company-auth:");
                  }

                  @Override
                  public GroupDescription.Basic get(AccountGroup.UUID uuid) {
                    return new GroupDescription.Basic() {
                      @Override
                      public AccountGroup.UUID getGroupUUID() {
                        return uuid;
                      }

                      @Override
                      public String getName() {
                        return uuid.get();
                      }

                      @Override
                      public String getEmailAddress() {
                        return uuid.get() + "@example.com";
                      }

                      @Override
                      @Nullable
                      public String getUrl() {
                        return null;
                      }
                    };
                  }

                  @Override
                  public Collection<GroupReference> suggest(String name, ProjectState project) {
                    throw new UnsupportedOperationException("not implemented");
                  }

                  @Override
                  public GroupMembership membershipsOf(CurrentUser user) {
                    return new GroupMembership() {
                      @Override
                      public boolean contains(AccountGroup.UUID groupId) {
                        return user.getExternalIdKeys().stream()
                            .anyMatch(e -> e.get().startsWith(groupId.get()));
                      }

                      @Override
                      public boolean containsAnyOf(Iterable<AccountGroup.UUID> groupIds) {
                        return StreamSupport.stream(groupIds.spliterator(), /* parallel= */ false)
                            .anyMatch(g -> contains(g));
                      }

                      @Override
                      public Set<AccountGroup.UUID> intersection(
                          Iterable<AccountGroup.UUID> groupIds) {
                        return StreamSupport.stream(groupIds.spliterator(), /* parallel= */ false)
                            .filter(g -> contains(g))
                            .collect(toImmutableSet());
                      }

                      @Override
                      public Set<AccountGroup.UUID> getKnownGroups() {
                        return ImmutableSet.of();
                      }
                    };
                  }

                  @Override
                  public boolean isVisibleToAll(AccountGroup.UUID uuid) {
                    return false;
                  }
                });
      }
    };
  }

  @Test
  public void defaultRefFilter_changeVisibilityIsAgnosticOfProvidedGroups() throws Exception {
    ExternalUser user = createUserInGroup("1", "it-department");

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
        .add(allow(Permission.READ).ref("refs/meta/config").group(EXTERNAL_GROUP))
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
    ExternalUser user = createUserInGroup("1", "it-department");
    // Check that refs/meta/config isn't visible by default
    assertThat(getVisibleRefNames(user)).containsExactly("HEAD", "refs/heads/master");
    // Grant access
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/meta/config").group(EXTERNAL_GROUP))
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
    ExternalUser user = createUserInGroup("1", "it-department");
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
    ExternalUser user = createUserInGroup("1", "it-department");
    permissionBackend
        .user(user)
        .change(changeNotesFactory.create(project, changeId))
        .check(ChangePermission.READ);
  }

  @Test
  public void changeVisibility_changeOnBranchVisibleToRegisteredUsersIsVisible() throws Exception {
    Change.Id changeId = changeOperations.newChange().project(project).create();
    ExternalUser user = createUserInGroup("1", "it-department");
    blockAnonymousRead();
    permissionBackend
        .user(user)
        .change(changeNotesFactory.create(project, changeId))
        .check(ChangePermission.READ);
  }

  @Test
  public void externalUser_isContainedInternalGroupThatContainsExternalGroup() {
    AccountGroup.UUID internalGroup =
        groupOperations.newGroup().addSubgroup(EXTERNAL_GROUP).create();
    ExternalUser user = createUserInGroup("1", "it-department");
    assertThat(user.getEffectiveGroups().contains(internalGroup)).isTrue();
    assertThat(user.getEffectiveGroups().contains(EXTERNAL_GROUP)).isTrue();
    assertThat(user.getEffectiveGroups().contains(REGISTERED_USERS)).isTrue();
    assertThat(user.getEffectiveGroups().contains(ANONYMOUS_USERS)).isTrue();
  }

  @GerritConfig(name = "groups.includeExternalUsersInRegisteredUsersGroup", value = "true")
  @Test
  public void externalUser_isContainedInRegisteredUsersIfConfigured() {
    ExternalUser user = createUserInGroup("1", "it-department");
    assertThat(user.getEffectiveGroups().contains(REGISTERED_USERS)).isTrue();
  }

  @GerritConfig(name = "groups.includeExternalUsersInRegisteredUsersGroup", value = "false")
  @Test
  public void externalUser_isNotContainedInRegisteredUsersIfNotConfigured() {
    ExternalUser user = createUserInGroup("1", "it-department");
    assertThat(user.getEffectiveGroups().contains(REGISTERED_USERS)).isFalse();
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

  ExternalUser createUserInGroup(String userId, String groupId) {
    return externalUserFactory.create(
        ImmutableSet.of(),
        ImmutableSet.of(externalIdKeyFactory.parse("company-auth:" + groupId + "-" + userId)),
        PropertyMap.EMPTY);
  }
}
