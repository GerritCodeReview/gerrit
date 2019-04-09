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

package com.google.gerrit.acceptance.testsuite.group;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Correspondence.BinaryPredicate;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import org.junit.Test;

public class GroupOperationsImplTest extends AbstractDaemonTest {

  @Inject private AccountOperations accountOperations;

  @Inject private GroupOperationsImpl groupOperations;

  private int uniqueGroupNameIndex;

  @Test
  public void groupCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().create();

    GroupInfo foundGroup = getGroupFromServer(groupUuid);
    assertThat(foundGroup.id).isEqualTo(groupUuid.get());
    assertThat(foundGroup.name).isNotEmpty();
  }

  @Test
  public void twoGroupsWithoutAnyParametersDoNotClash() throws Exception {
    AccountGroup.UUID groupUuid1 = groupOperations.newGroup().create();
    AccountGroup.UUID groupUuid2 = groupOperations.newGroup().create();

    TestGroup group1 = groupOperations.group(groupUuid1).get();
    TestGroup group2 = groupOperations.group(groupUuid2).get();
    assertThat(group1.groupUuid()).isNotEqualTo(group2.groupUuid());
  }

  @Test
  public void groupCreatedByTestApiCanBeRetrievedViaOfficialApi() throws Exception {
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().name("unique group created via test API").create();

    GroupInfo foundGroup = getGroupFromServer(groupUuid);
    assertThat(foundGroup.id).isEqualTo(groupUuid.get());
    assertThat(foundGroup.name).isEqualTo("unique group created via test API");
  }

  @Test
  public void specifiedNameIsRespectedForGroupCreation() throws Exception {
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().name("XYZ-123-this-name-must-be-unique").create();

    GroupInfo group = getGroupFromServer(groupUuid);
    assertThat(group.name).isEqualTo("XYZ-123-this-name-must-be-unique");
  }

  @Test
  public void specifiedDescriptionIsRespectedForGroupCreation() throws Exception {
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().description("All authenticated users").create();

    GroupInfo group = getGroupFromServer(groupUuid);
    assertThat(group.description).isEqualTo("All authenticated users");
  }

  @Test
  public void requestingNoDescriptionIsPossibleForGroupCreation() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().clearDescription().create();

    GroupInfo group = getGroupFromServer(groupUuid);
    assertThat(group.description).isNull();
  }

  @Test
  public void specifiedOwnerIsRespectedForGroupCreation() throws Exception {
    AccountGroup.UUID ownerGroupUuid = groupOperations.newGroup().create();

    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().ownerGroupUuid(ownerGroupUuid).create();

    GroupInfo foundGroup = getGroupFromServer(groupUuid);
    assertThat(foundGroup.ownerId).isEqualTo(ownerGroupUuid.get());
  }

  @Test
  public void specifiedVisibilityIsRespectedForGroupCreation() throws Exception {
    AccountGroup.UUID group1Uuid = groupOperations.newGroup().visibleToAll(true).create();
    AccountGroup.UUID group2Uuid = groupOperations.newGroup().visibleToAll(false).create();

    GroupInfo foundGroup1 = getGroupFromServer(group1Uuid);
    GroupInfo foundGroup2 = getGroupFromServer(group2Uuid);
    assertThat(foundGroup1.options.visibleToAll).isTrue();
    // False == null
    assertThat(foundGroup2.options.visibleToAll).isNull();
  }

  @Test
  public void specifiedMembersAreRespectedForGroupCreation() throws Exception {
    Account.Id account1Id = accountOperations.newAccount().create();
    Account.Id account2Id = accountOperations.newAccount().create();
    Account.Id account3Id = accountOperations.newAccount().create();
    Account.Id account4Id = accountOperations.newAccount().create();

    AccountGroup.UUID groupUuid =
        groupOperations
            .newGroup()
            .members(account1Id, account2Id)
            .addMember(account3Id)
            .addMember(account4Id)
            .create();

    GroupInfo foundGroup = getGroupFromServer(groupUuid);
    assertThat(foundGroup.members)
        .comparingElementsUsing(getAccountToIdCorrespondence())
        .containsExactly(account1Id, account2Id, account3Id, account4Id);
  }

  @Test
  public void directlyAddingMembersIsPossibleForGroupCreation() throws Exception {
    Account.Id account1Id = accountOperations.newAccount().create();
    Account.Id account2Id = accountOperations.newAccount().create();

    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().addMember(account1Id).addMember(account2Id).create();

    GroupInfo foundGroup = getGroupFromServer(groupUuid);
    assertThat(foundGroup.members)
        .comparingElementsUsing(getAccountToIdCorrespondence())
        .containsExactly(account1Id, account2Id);
  }

  @Test
  public void requestingNoMembersIsPossibleForGroupCreation() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().clearMembers().create();

    GroupInfo foundGroup = getGroupFromServer(groupUuid);
    assertThat(foundGroup.members).isEmpty();
  }

  @Test
  public void specifiedSubgroupsAreRespectedForGroupCreation() throws Exception {
    AccountGroup.UUID group1Uuid = groupOperations.newGroup().create();
    AccountGroup.UUID group2Uuid = groupOperations.newGroup().create();
    AccountGroup.UUID group3Uuid = groupOperations.newGroup().create();
    AccountGroup.UUID group4Uuid = groupOperations.newGroup().create();

    AccountGroup.UUID groupUuid =
        groupOperations
            .newGroup()
            .subgroups(group1Uuid, group2Uuid)
            .addSubgroup(group3Uuid)
            .addSubgroup(group4Uuid)
            .create();

    GroupInfo foundGroup = getGroupFromServer(groupUuid);
    assertThat(foundGroup.includes)
        .comparingElementsUsing(getGroupToUuidCorrespondence())
        .containsExactly(group1Uuid, group2Uuid, group3Uuid, group4Uuid);
  }

  @Test
  public void directlyAddingSubgroupsIsPossibleForGroupCreation() throws Exception {
    AccountGroup.UUID group1Uuid = groupOperations.newGroup().create();
    AccountGroup.UUID group2Uuid = groupOperations.newGroup().create();

    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().addSubgroup(group1Uuid).addSubgroup(group2Uuid).create();

    GroupInfo foundGroup = getGroupFromServer(groupUuid);
    assertThat(foundGroup.includes)
        .comparingElementsUsing(getGroupToUuidCorrespondence())
        .containsExactly(group1Uuid, group2Uuid);
  }

  @Test
  public void requestingNoSubgroupsIsPossibleForGroupCreation() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().clearSubgroups().create();

    GroupInfo foundGroup = getGroupFromServer(groupUuid);
    assertThat(foundGroup.includes).isEmpty();
  }

  @Test
  public void existingGroupCanBeCheckedForExistence() throws Exception {
    AccountGroup.UUID groupUuid = createGroupInServer(createArbitraryGroupInput());

    boolean exists = groupOperations.group(groupUuid).exists();

    assertThat(exists).isTrue();
  }

  @Test
  public void notExistingGroupCanBeCheckedForExistence() throws Exception {
    AccountGroup.UUID notExistingGroupUuid = new AccountGroup.UUID("not-existing-group");

    boolean exists = groupOperations.group(notExistingGroupUuid).exists();

    assertThat(exists).isFalse();
  }

  @Test
  public void retrievingNotExistingGroupFails() throws Exception {
    AccountGroup.UUID notExistingGroupUuid = new AccountGroup.UUID("not-existing-group");

    exception.expect(IllegalStateException.class);
    groupOperations.group(notExistingGroupUuid).get();
  }

  @Test
  public void groupNotCreatedByTestApiCanBeRetrieved() throws Exception {
    GroupInput input = createArbitraryGroupInput();
    input.name = "unique group not created via test API";
    AccountGroup.UUID groupUuid = createGroupInServer(input);

    TestGroup foundGroup = groupOperations.group(groupUuid).get();

    assertThat(foundGroup.groupUuid()).isEqualTo(groupUuid);
    assertThat(foundGroup.name()).isEqualTo("unique group not created via test API");
  }

  @Test
  public void uuidOfExistingGroupCanBeRetrieved() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().create();

    AccountGroup.UUID foundGroupUuid = groupOperations.group(groupUuid).get().groupUuid();

    assertThat(foundGroupUuid).isEqualTo(groupUuid);
  }

  @Test
  public void nameOfExistingGroupCanBeRetrieved() throws Exception {
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().name("ABC-789-this-name-must-be-unique").create();

    String groupName = groupOperations.group(groupUuid).get().name();

    assertThat(groupName).isEqualTo("ABC-789-this-name-must-be-unique");
  }

  @Test
  public void nameKeyOfExistingGroupCanBeRetrieved() throws Exception {
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().name("ABC-789-this-name-must-be-unique").create();

    AccountGroup.NameKey groupName = groupOperations.group(groupUuid).get().nameKey();

    assertThat(groupName).isEqualTo(new AccountGroup.NameKey("ABC-789-this-name-must-be-unique"));
  }

  @Test
  public void descriptionOfExistingGroupCanBeRetrieved() throws Exception {
    AccountGroup.UUID groupUuid =
        groupOperations
            .newGroup()
            .description("This is a very detailed description of this group.")
            .create();

    Optional<String> description = groupOperations.group(groupUuid).get().description();

    assertThat(description).hasValue("This is a very detailed description of this group.");
  }

  @Test
  public void emptyDescriptionOfExistingGroupCanBeRetrieved() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().clearDescription().create();

    Optional<String> description = groupOperations.group(groupUuid).get().description();

    assertThat(description).isEmpty();
  }

  @Test
  public void ownerGroupUuidOfExistingGroupCanBeRetrieved() throws Exception {
    AccountGroup.UUID originalOwnerGroupUuid = new AccountGroup.UUID("owner group");
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().ownerGroupUuid(originalOwnerGroupUuid).create();

    AccountGroup.UUID ownerGroupUuid = groupOperations.group(groupUuid).get().ownerGroupUuid();

    assertThat(ownerGroupUuid).isEqualTo(originalOwnerGroupUuid);
  }

  @Test
  public void visibilityOfExistingGroupCanBeRetrieved() throws Exception {
    AccountGroup.UUID visibleGroupUuid = groupOperations.newGroup().visibleToAll(true).create();
    AccountGroup.UUID invisibleGroupUuid = groupOperations.newGroup().visibleToAll(false).create();

    TestGroup visibleGroup = groupOperations.group(visibleGroupUuid).get();
    TestGroup invisibleGroup = groupOperations.group(invisibleGroupUuid).get();

    assertThat(visibleGroup.visibleToAll()).named("visibility of visible group").isTrue();
    assertThat(invisibleGroup.visibleToAll()).named("visibility of invisible group").isFalse();
  }

  @Test
  public void createdOnOfExistingGroupCanBeRetrieved() throws Exception {
    GroupInfo group = gApi.groups().create(createArbitraryGroupInput()).detail();
    AccountGroup.UUID groupUuid = new AccountGroup.UUID(group.id);

    Timestamp createdOn = groupOperations.group(groupUuid).get().createdOn();

    assertThat(createdOn).isEqualTo(group.createdOn);
  }

  @Test
  public void membersOfExistingGroupCanBeRetrieved() throws Exception {
    Account.Id memberId1 = new Account.Id(1000);
    Account.Id memberId2 = new Account.Id(2000);
    Account.Id memberId3 = new Account.Id(3000);
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().members(memberId1, memberId2, memberId3).create();

    ImmutableSet<Account.Id> members = groupOperations.group(groupUuid).get().members();

    assertThat(members).containsExactly(memberId1, memberId2, memberId3);
  }

  @Test
  public void emptyMembersOfExistingGroupCanBeRetrieved() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().clearMembers().create();

    ImmutableSet<Account.Id> members = groupOperations.group(groupUuid).get().members();

    assertThat(members).isEmpty();
  }

  @Test
  public void subgroupsOfExistingGroupCanBeRetrieved() throws Exception {
    AccountGroup.UUID subgroupUuid1 = new AccountGroup.UUID("subgroup 1");
    AccountGroup.UUID subgroupUuid2 = new AccountGroup.UUID("subgroup 2");
    AccountGroup.UUID subgroupUuid3 = new AccountGroup.UUID("subgroup 3");
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().subgroups(subgroupUuid1, subgroupUuid2, subgroupUuid3).create();

    ImmutableSet<AccountGroup.UUID> subgroups = groupOperations.group(groupUuid).get().subgroups();

    assertThat(subgroups).containsExactly(subgroupUuid1, subgroupUuid2, subgroupUuid3);
  }

  @Test
  public void emptySubgroupsOfExistingGroupCanBeRetrieved() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().clearSubgroups().create();

    ImmutableSet<AccountGroup.UUID> subgroups = groupOperations.group(groupUuid).get().subgroups();

    assertThat(subgroups).isEmpty();
  }

  @Test
  public void updateWithoutAnyParametersIsANoop() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().create();
    TestGroup originalGroup = groupOperations.group(groupUuid).get();

    groupOperations.group(groupUuid).forUpdate().update();

    TestGroup updatedGroup = groupOperations.group(groupUuid).get();
    assertThat(updatedGroup).isEqualTo(originalGroup);
  }

  @Test
  public void updateWritesToInternalGroupSystem() throws Exception {
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().description("original description").create();

    groupOperations.group(groupUuid).forUpdate().description("updated description").update();

    String currentDescription = getGroupFromServer(groupUuid).description;
    assertThat(currentDescription).isEqualTo("updated description");
  }

  @Test
  public void nameCanBeUpdated() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().name("original name").create();

    groupOperations.group(groupUuid).forUpdate().name("updated name").update();

    String currentName = groupOperations.group(groupUuid).get().name();
    assertThat(currentName).isEqualTo("updated name");
  }

  @Test
  public void descriptionCanBeUpdated() throws Exception {
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().description("original description").create();

    groupOperations.group(groupUuid).forUpdate().description("updated description").update();

    Optional<String> currentDescription = groupOperations.group(groupUuid).get().description();
    assertThat(currentDescription).hasValue("updated description");
  }

  @Test
  public void descriptionCanBeCleared() throws Exception {
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().description("original description").create();

    groupOperations.group(groupUuid).forUpdate().clearDescription().update();

    Optional<String> currentDescription = groupOperations.group(groupUuid).get().description();
    assertThat(currentDescription).isEmpty();
  }

  @Test
  public void ownerGroupUuidCanBeUpdated() throws Exception {
    AccountGroup.UUID originalOwnerGroupUuid = new AccountGroup.UUID("original owner");
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().ownerGroupUuid(originalOwnerGroupUuid).create();

    AccountGroup.UUID updatedOwnerGroupUuid = new AccountGroup.UUID("updated owner");
    groupOperations.group(groupUuid).forUpdate().ownerGroupUuid(updatedOwnerGroupUuid).update();

    AccountGroup.UUID currentOwnerGroupUuid =
        groupOperations.group(groupUuid).get().ownerGroupUuid();
    assertThat(currentOwnerGroupUuid).isEqualTo(updatedOwnerGroupUuid);
  }

  @Test
  public void visibilityCanBeUpdated() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().visibleToAll(true).create();

    groupOperations.group(groupUuid).forUpdate().visibleToAll(false).update();

    boolean visibleToAll = groupOperations.group(groupUuid).get().visibleToAll();
    assertThat(visibleToAll).isFalse();
  }

  @Test
  public void membersCanBeAdded() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().clearMembers().create();

    Account.Id memberId1 = new Account.Id(1000);
    Account.Id memberId2 = new Account.Id(2000);
    groupOperations.group(groupUuid).forUpdate().addMember(memberId1).addMember(memberId2).update();

    ImmutableSet<Account.Id> members = groupOperations.group(groupUuid).get().members();
    assertThat(members).containsExactly(memberId1, memberId2);
  }

  @Test
  public void membersCanBeRemoved() throws Exception {
    Account.Id memberId1 = new Account.Id(1000);
    Account.Id memberId2 = new Account.Id(2000);
    AccountGroup.UUID groupUuid = groupOperations.newGroup().members(memberId1, memberId2).create();

    groupOperations.group(groupUuid).forUpdate().removeMember(memberId2).update();

    ImmutableSet<Account.Id> members = groupOperations.group(groupUuid).get().members();
    assertThat(members).containsExactly(memberId1);
  }

  @Test
  public void memberAdditionAndRemovalCanBeMixed() throws Exception {
    Account.Id memberId1 = new Account.Id(1000);
    Account.Id memberId2 = new Account.Id(2000);
    AccountGroup.UUID groupUuid = groupOperations.newGroup().members(memberId1, memberId2).create();

    Account.Id memberId3 = new Account.Id(3000);
    groupOperations
        .group(groupUuid)
        .forUpdate()
        .removeMember(memberId1)
        .addMember(memberId3)
        .update();

    ImmutableSet<Account.Id> members = groupOperations.group(groupUuid).get().members();
    assertThat(members).containsExactly(memberId2, memberId3);
  }

  @Test
  public void membersCanBeCleared() throws Exception {
    Account.Id memberId1 = new Account.Id(1000);
    Account.Id memberId2 = new Account.Id(2000);
    AccountGroup.UUID groupUuid = groupOperations.newGroup().members(memberId1, memberId2).create();

    groupOperations.group(groupUuid).forUpdate().clearMembers().update();

    ImmutableSet<Account.Id> members = groupOperations.group(groupUuid).get().members();
    assertThat(members).isEmpty();
  }

  @Test
  public void furtherMembersCanBeAddedAfterClearingAll() throws Exception {
    Account.Id memberId1 = new Account.Id(1000);
    Account.Id memberId2 = new Account.Id(2000);
    AccountGroup.UUID groupUuid = groupOperations.newGroup().members(memberId1, memberId2).create();

    Account.Id memberId3 = new Account.Id(3000);
    groupOperations.group(groupUuid).forUpdate().clearMembers().addMember(memberId3).update();

    ImmutableSet<Account.Id> members = groupOperations.group(groupUuid).get().members();
    assertThat(members).containsExactly(memberId3);
  }

  @Test
  public void subgroupsCanBeAdded() throws Exception {
    AccountGroup.UUID groupUuid = groupOperations.newGroup().clearSubgroups().create();

    AccountGroup.UUID subgroupUuid1 = new AccountGroup.UUID("subgroup 1");
    AccountGroup.UUID subgroupUuid2 = new AccountGroup.UUID("subgroup 2");
    groupOperations
        .group(groupUuid)
        .forUpdate()
        .addSubgroup(subgroupUuid1)
        .addSubgroup(subgroupUuid2)
        .update();

    ImmutableSet<AccountGroup.UUID> subgroups = groupOperations.group(groupUuid).get().subgroups();
    assertThat(subgroups).containsExactly(subgroupUuid1, subgroupUuid2);
  }

  @Test
  public void subgroupsCanBeRemoved() throws Exception {
    AccountGroup.UUID subgroupUuid1 = new AccountGroup.UUID("subgroup 1");
    AccountGroup.UUID subgroupUuid2 = new AccountGroup.UUID("subgroup 2");
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().subgroups(subgroupUuid1, subgroupUuid2).create();

    groupOperations.group(groupUuid).forUpdate().removeSubgroup(subgroupUuid2).update();

    ImmutableSet<AccountGroup.UUID> subgroups = groupOperations.group(groupUuid).get().subgroups();
    assertThat(subgroups).containsExactly(subgroupUuid1);
  }

  @Test
  public void subgroupAdditionAndRemovalCanBeMixed() throws Exception {
    AccountGroup.UUID subgroupUuid1 = new AccountGroup.UUID("subgroup 1");
    AccountGroup.UUID subgroupUuid2 = new AccountGroup.UUID("subgroup 2");
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().subgroups(subgroupUuid1, subgroupUuid2).create();

    AccountGroup.UUID subgroupUuid3 = new AccountGroup.UUID("subgroup 3");
    groupOperations
        .group(groupUuid)
        .forUpdate()
        .removeSubgroup(subgroupUuid1)
        .addSubgroup(subgroupUuid3)
        .update();

    ImmutableSet<AccountGroup.UUID> subgroups = groupOperations.group(groupUuid).get().subgroups();
    assertThat(subgroups).containsExactly(subgroupUuid2, subgroupUuid3);
  }

  @Test
  public void subgroupsCanBeCleared() throws Exception {
    AccountGroup.UUID subgroupUuid1 = new AccountGroup.UUID("subgroup 1");
    AccountGroup.UUID subgroupUuid2 = new AccountGroup.UUID("subgroup 2");
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().subgroups(subgroupUuid1, subgroupUuid2).create();

    groupOperations.group(groupUuid).forUpdate().clearSubgroups().update();

    ImmutableSet<AccountGroup.UUID> subgroups = groupOperations.group(groupUuid).get().subgroups();
    assertThat(subgroups).isEmpty();
  }

  @Test
  public void furtherSubgroupsCanBeAddedAfterClearingAll() throws Exception {
    AccountGroup.UUID subgroupUuid1 = new AccountGroup.UUID("subgroup 1");
    AccountGroup.UUID subgroupUuid2 = new AccountGroup.UUID("subgroup 2");
    AccountGroup.UUID groupUuid =
        groupOperations.newGroup().subgroups(subgroupUuid1, subgroupUuid2).create();

    AccountGroup.UUID subgroupUuid3 = new AccountGroup.UUID("subgroup 3");
    groupOperations
        .group(groupUuid)
        .forUpdate()
        .clearSubgroups()
        .addSubgroup(subgroupUuid3)
        .update();

    ImmutableSet<AccountGroup.UUID> subgroups = groupOperations.group(groupUuid).get().subgroups();
    assertThat(subgroups).containsExactly(subgroupUuid3);
  }

  private GroupInput createArbitraryGroupInput() {
    GroupInput groupInput = new GroupInput();
    groupInput.name = name("verifiers-" + uniqueGroupNameIndex++);
    return groupInput;
  }

  private GroupInfo getGroupFromServer(AccountGroup.UUID groupUuid) throws RestApiException {
    return gApi.groups().id(groupUuid.get()).detail();
  }

  private AccountGroup.UUID createGroupInServer(GroupInput input) throws RestApiException {
    GroupInfo group = gApi.groups().create(input).detail();
    return new AccountGroup.UUID(group.id);
  }

  private static Correspondence<AccountInfo, Account.Id> getAccountToIdCorrespondence() {
    return Correspondence.from(
        new BinaryPredicate<AccountInfo, Account.Id>() {
          @Override
          public boolean apply(AccountInfo actualAccount, Account.Id expectedId) {
            Account.Id accountId =
                Optional.ofNullable(actualAccount)
                    .map(account -> account._accountId)
                    .map(Account.Id::new)
                    .orElse(null);
            return Objects.equals(accountId, expectedId);
          }
        },
        "has ID");
  }

  private static Correspondence<GroupInfo, AccountGroup.UUID> getGroupToUuidCorrespondence() {
    return Correspondence.from(
        new BinaryPredicate<GroupInfo, AccountGroup.UUID>() {
          @Override
          public boolean apply(GroupInfo actualGroup, AccountGroup.UUID expectedUuid) {
            AccountGroup.UUID groupUuid =
                Optional.ofNullable(actualGroup)
                    .map(group -> group.id)
                    .map(AccountGroup.UUID::new)
                    .orElse(null);
            return Objects.equals(groupUuid, expectedUuid);
          }
        },
        "has UUID");
  }
}
