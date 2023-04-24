// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.group;

import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.group.db.GroupDelta;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Rule;
import org.junit.Test;

public class GroupsUpdateIT {
  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();
  @Inject @ServerInitiated private Provider<GroupsUpdate> groupsUpdateProvider;
  @Inject private Groups groups;

  @Test
  public void groupCreationIsRetriedWhenFailedDueToConcurrentNameModification() throws Exception {
    InternalGroupCreation groupCreation = getGroupCreation("users", "users-UUID");
    GroupDelta groupDelta =
        GroupDelta.builder()
            .setMemberModification(
                new CreateAnotherGroupOnceAsSideEffectOfMemberModification("verifiers"))
            .build();
    createGroup(groupCreation, groupDelta);

    Stream<String> allGroupNames = getAllGroupNames();
    assertThat(allGroupNames).containsAtLeast("users", "verifiers");
  }

  @Test
  public void groupRenameIsRetriedWhenFailedDueToConcurrentNameModification() throws Exception {
    createGroup("users", "users-UUID");

    GroupDelta groupDelta =
        GroupDelta.builder()
            .setName(AccountGroup.nameKey("contributors"))
            .setMemberModification(
                new CreateAnotherGroupOnceAsSideEffectOfMemberModification("verifiers"))
            .build();
    updateGroup(AccountGroup.uuid("users-UUID"), groupDelta);

    Stream<String> allGroupNames = getAllGroupNames();
    assertThat(allGroupNames).containsAtLeast("contributors", "verifiers");
  }

  @Test
  public void groupUpdateFailsWithExceptionForNotExistingGroup() throws Exception {
    GroupDelta groupDelta =
        GroupDelta.builder().setDescription("A description for the group").build();
    assertThrows(
        NoSuchGroupException.class,
        () -> updateGroup(AccountGroup.uuid("nonexistent-group-UUID"), groupDelta));
  }

  private void createGroup(String groupName, String groupUuid) throws Exception {
    InternalGroupCreation groupCreation = getGroupCreation(groupName, groupUuid);
    GroupDelta groupDelta = GroupDelta.builder().build();

    createGroup(groupCreation, groupDelta);
  }

  private void createGroup(InternalGroupCreation groupCreation, GroupDelta groupDelta)
      throws IOException, ConfigInvalidException {
    groupsUpdateProvider.get().createGroup(groupCreation, groupDelta);
  }

  private void updateGroup(AccountGroup.UUID groupUuid, GroupDelta groupDelta) throws Exception {
    groupsUpdateProvider.get().updateGroup(groupUuid, groupDelta);
  }

  private Stream<String> getAllGroupNames() throws IOException, ConfigInvalidException {
    return groups.getAllGroupReferences().map(GroupReference::getName);
  }

  @SuppressWarnings("MathAbsoluteNegative")
  private static InternalGroupCreation getGroupCreation(String groupName, String groupUuid) {
    return InternalGroupCreation.builder()
        .setGroupUUID(AccountGroup.uuid(groupUuid))
        .setNameKey(AccountGroup.nameKey(groupName))
        .setId(AccountGroup.id(Math.abs(groupName.hashCode())))
        .build();
  }

  private class CreateAnotherGroupOnceAsSideEffectOfMemberModification
      implements GroupDelta.MemberModification {

    private boolean groupCreated = false;
    private String groupName;

    public CreateAnotherGroupOnceAsSideEffectOfMemberModification(String groupName) {
      this.groupName = groupName;
    }

    @Override
    public Set<Account.Id> apply(ImmutableSet<Account.Id> members) {
      if (!groupCreated) {
        createGroup();
        groupCreated = true;
      }

      return members;
    }

    private void createGroup() {
      InternalGroupCreation groupCreation = getGroupCreation(groupName, groupName + "-UUID");
      GroupDelta groupDelta = GroupDelta.builder().build();
      try {
        groupsUpdateProvider.get().createGroup(groupCreation, groupDelta);
      } catch (StorageException | IOException | ConfigInvalidException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
