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

import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class GroupOperationsImplTest extends AbstractDaemonTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Inject private AccountOperations accountOperations;
  @Inject private GerritApi gApi;

  @Inject private GroupOperationsImpl groupOperations;

  @Test
  public void groupCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    TestGroup group = groupOperations.newGroup().create();

    GroupInfo foundGroup = getGroupFromServer(group.groupUuid());
    assertThat(foundGroup.id).isEqualTo(group.groupUuid().get());
    assertThat(foundGroup.name).isNotEmpty();
  }

  @Test
  public void twoGroupsWithoutAnyParametersDoNotClash() throws Exception {
    TestGroup group1 = groupOperations.newGroup().create();
    TestGroup group2 = groupOperations.newGroup().create();

    GroupInfo foundGroup1 = getGroupFromServer(group1.groupUuid());
    GroupInfo foundGroup2 = getGroupFromServer(group2.groupUuid());
    assertThat(foundGroup1.id).isNotEqualTo(foundGroup2.id);
  }

  @Test
  public void specifiedNameIsRespectedForGroupCreation() throws Exception {
    TestGroup group = groupOperations.newGroup().name("XYZ-123-this-name-must-be-unique").create();

    GroupInfo foundGroup = getGroupFromServer(group.groupUuid());
    assertThat(foundGroup.name).isEqualTo("XYZ-123-this-name-must-be-unique");
  }

  @Test
  public void specifiedDescriptionIsRespectedForGroupCreation() throws Exception {
    TestGroup group = groupOperations.newGroup().description("All authenticated users").create();

    GroupInfo foundGroup = getGroupFromServer(group.groupUuid());
    assertThat(foundGroup.description).isEqualTo("All authenticated users");
  }

  @Test
  public void requestingNoDescriptionIsPossibleForGroupCreation() throws Exception {
    TestGroup group = groupOperations.newGroup().clearDescription().create();

    GroupInfo foundGroup = getGroupFromServer(group.groupUuid());
    assertThat(foundGroup.description).isNull();
  }

  @Test
  public void specifiedOwnerIsRespectedForGroupCreation() throws Exception {
    TestGroup ownerGroup = groupOperations.newGroup().create();

    TestGroup group =
        groupOperations.newGroup().ownerGroupUuid(ownerGroup.ownerGroupUuid()).create();

    GroupInfo foundGroup = getGroupFromServer(group.groupUuid());
    assertThat(foundGroup.ownerId).isEqualTo(ownerGroup.ownerGroupUuid().get());
  }

  @Test
  public void specifiedVisibilityIsRespectedForGroupCreation() throws Exception {
    TestGroup group1 = groupOperations.newGroup().visibleToAll(true).create();
    TestGroup group2 = groupOperations.newGroup().visibleToAll(false).create();

    GroupInfo foundGroup1 = getGroupFromServer(group1.groupUuid());
    GroupInfo foundGroup2 = getGroupFromServer(group2.groupUuid());
    assertThat(foundGroup1.options.visibleToAll).isTrue();
    // False == null
    assertThat(foundGroup2.options.visibleToAll).isNull();
  }

  @Test
  public void specifiedMembersAreRespectedForGroupCreation() throws Exception {
    TestAccount account1 = accountOperations.newAccount().create();
    TestAccount account2 = accountOperations.newAccount().create();
    TestAccount account3 = accountOperations.newAccount().create();
    TestAccount account4 = accountOperations.newAccount().create();

    TestGroup group =
        groupOperations
            .newGroup()
            .members(account1.accountId(), account2.accountId())
            .addMember(account3.accountId())
            .addMember(account4.accountId())
            .create();

    GroupInfo foundGroup = getGroupFromServer(group.groupUuid());
    assertThat(foundGroup.members)
        .comparingElementsUsing(getAccountToIdCorrespondence())
        .containsExactly(
            account1.accountId(), account2.accountId(), account3.accountId(), account4.accountId());
  }

  @Test
  public void directlyAddingMembersIsPossibleForGroupCreation() throws Exception {
    TestAccount account1 = accountOperations.newAccount().create();
    TestAccount account2 = accountOperations.newAccount().create();

    TestGroup group =
        groupOperations
            .newGroup()
            .addMember(account1.accountId())
            .addMember(account2.accountId())
            .create();

    GroupInfo foundGroup = getGroupFromServer(group.groupUuid());
    assertThat(foundGroup.members)
        .comparingElementsUsing(getAccountToIdCorrespondence())
        .containsExactly(account1.accountId(), account2.accountId());
  }

  @Test
  public void specifiedSubgroupsAreRespectedForGroupCreation() throws Exception {
    TestGroup group1 = groupOperations.newGroup().create();
    TestGroup group2 = groupOperations.newGroup().create();
    TestGroup group3 = groupOperations.newGroup().create();
    TestGroup group4 = groupOperations.newGroup().create();

    TestGroup group =
        groupOperations
            .newGroup()
            .subgroups(group1.groupUuid(), group2.groupUuid())
            .addSubgroup(group3.groupUuid())
            .addSubgroup(group4.groupUuid())
            .create();

    GroupInfo foundGroup = getGroupFromServer(group.groupUuid());
    assertThat(foundGroup.includes)
        .comparingElementsUsing(getGroupToUuidCorrespondence())
        .containsExactly(
            group1.groupUuid(), group2.groupUuid(), group3.groupUuid(), group4.groupUuid());
  }

  @Test
  public void directlyAddingSubgroupsIsPossibleForGroupCreation() throws Exception {
    TestGroup group1 = groupOperations.newGroup().create();
    TestGroup group2 = groupOperations.newGroup().create();

    TestGroup group =
        groupOperations
            .newGroup()
            .addSubgroup(group1.groupUuid())
            .addSubgroup(group2.groupUuid())
            .create();

    GroupInfo foundGroup = getGroupFromServer(group.groupUuid());
    assertThat(foundGroup.includes)
        .comparingElementsUsing(getGroupToUuidCorrespondence())
        .containsExactly(group1.groupUuid(), group2.groupUuid());
  }

  @Test
  public void internalGroupCanBeCheckedForExistence() throws Exception {
    GroupInfo group = gApi.groups().create(createArbitraryGroupInput()).detail();
    AccountGroup.UUID groupUuid = new AccountGroup.UUID(group.id);

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
  public void retrievingNotExistingInternalGroupFails() throws Exception {
    AccountGroup.UUID notExistingGroupUuid = new AccountGroup.UUID("not-existing-group");

    expectedException.expect(IllegalStateException.class);
    groupOperations.group(notExistingGroupUuid).get();
  }

  @Test
  public void uuidOfInternalGroupCanBeRetrieved() throws Exception {
    GroupInfo group = gApi.groups().create(createArbitraryGroupInput()).detail();
    AccountGroup.UUID groupUuid = new AccountGroup.UUID(group.id);

    TestGroup foundGroup = groupOperations.group(groupUuid).get();
    assertThat(foundGroup.groupUuid()).isEqualTo(groupUuid);
  }

  @Test
  public void nameOfInternalGroupCanBeRetrieved() throws Exception {
    GroupInfo group = gApi.groups().create("ABC-789-this-name-must-be-unique").detail();
    AccountGroup.UUID groupUuid = new AccountGroup.UUID(group.id);

    TestGroup foundGroup = groupOperations.group(groupUuid).get();
    assertThat(foundGroup.name()).isEqualTo("ABC-789-this-name-must-be-unique");
  }

  @Test
  public void descriptionOfInternalGroupCanBeRetrieved() throws Exception {
    GroupInput input = createArbitraryGroupInput();
    input.description = "This is a very detailed description of this group.";
    GroupInfo group = gApi.groups().create(input).detail();
    AccountGroup.UUID groupUuid = new AccountGroup.UUID(group.id);

    TestGroup foundGroup = groupOperations.group(groupUuid).get();
    assertThat(foundGroup.description())
        .hasValue("This is a very detailed description of this group.");
  }

  @Test
  public void emptyDescriptionOfInternalGroupCanBeRetrieved() throws Exception {
    GroupInput input = createArbitraryGroupInput();
    input.description = null;
    GroupInfo group = gApi.groups().create(input).detail();
    AccountGroup.UUID groupUuid = new AccountGroup.UUID(group.id);

    TestGroup foundGroup = groupOperations.group(groupUuid).get();
    assertThat(foundGroup.description()).isEmpty();
  }

  // TODO(aliceks): Add more tests.

  private GroupInput createArbitraryGroupInput() {
    GroupInput groupInput = new GroupInput();
    groupInput.name = name("verifiers");
    return groupInput;
  }

  private GroupInfo getGroupFromServer(AccountGroup.UUID groupUuid) throws RestApiException {
    return gApi.groups().id(groupUuid.get()).detail();
  }

  private static Correspondence<AccountInfo, Account.Id> getAccountToIdCorrespondence() {
    return new Correspondence<AccountInfo, Account.Id>() {
      @Override
      public boolean compare(AccountInfo actualAccount, Account.Id expectedId) {
        Account.Id accountId =
            Optional.ofNullable(actualAccount)
                .map(account -> account._accountId)
                .map(Account.Id::new)
                .orElse(null);
        return Objects.equals(accountId, expectedId);
      }

      @Override
      public String toString() {
        return "has ID";
      }
    };
  }

  private static Correspondence<GroupInfo, AccountGroup.UUID> getGroupToUuidCorrespondence() {
    return new Correspondence<GroupInfo, AccountGroup.UUID>() {
      @Override
      public boolean compare(GroupInfo actualGroup, AccountGroup.UUID expectedUuid) {
        AccountGroup.UUID groupUuid =
            Optional.ofNullable(actualGroup)
                .map(group -> group.id)
                .map(AccountGroup.UUID::new)
                .orElse(null);
        return Objects.equals(groupUuid, expectedUuid);
      }

      @Override
      public String toString() {
        return "has UUID";
      }
    };
  }
}
