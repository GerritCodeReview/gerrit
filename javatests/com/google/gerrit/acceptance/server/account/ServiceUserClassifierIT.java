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

package com.google.gerrit.acceptance.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.gerrit.server.group.testing.TestGroupBackend;
import com.google.inject.Inject;
import org.junit.Test;

public class ServiceUserClassifierIT extends AbstractDaemonTest {
  @Inject private GroupOperations groupOperations;
  @Inject private ServiceUserClassifier serviceUserClassifier;
  @Inject private UniversalGroupBackend universalGroupBackend;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void userWithoutMembershipInServiceUserIsNotAServiceUser() throws Exception {
    TestAccount user = accountCreator.create();
    assertThat(serviceUserClassifier.isServiceUser(user.id())).isFalse();
  }

  @Test
  public void userWithDirectMembershipInServiceUserIsAServiceUser() throws Exception {
    TestAccount user = accountCreator.create(null, ServiceUserClassifier.SERVICE_USERS);
    assertThat(serviceUserClassifier.isServiceUser(user.id())).isTrue();
  }

  @Test
  public void userWithIndirectMembershipInServiceUserIsAServiceUser() throws Exception {
    TestAccount user = accountCreator.create();
    AccountGroup.UUID subGroupUUID =
        groupOperations.newGroup().name("CI Service Users").addMember(user.id()).create();
    groupOperations.group(serviceUsersUUID()).forUpdate().addSubgroup(subGroupUUID).update();
    assertThat(serviceUserClassifier.isServiceUser(user.id())).isTrue();
  }

  @Test
  public void userWithIndirectExternalMembershipInServiceUserIsAServiceUser() throws Exception {
    TestGroupBackend testGroupBackend = new TestGroupBackend();
    TestAccount user = accountCreator.create();
    GroupDescription.Basic externalServiceUsers = testGroupBackend.create("External Service Users");

    try (ExtensionRegistry.Registration registration =
        extensionRegistry.newRegistration().add(testGroupBackend)) {
      assertThat(universalGroupBackend.handles(externalServiceUsers.getGroupUUID())).isTrue();
      assertThat(serviceUserClassifier.isServiceUser(user.id())).isFalse();

      groupOperations
          .group(serviceUsersUUID())
          .forUpdate()
          .addSubgroup(externalServiceUsers.getGroupUUID())
          .update();
      testGroupBackend.setMembershipsOf(
          user.id(),
          new ListGroupMembership(ImmutableList.of(externalServiceUsers.getGroupUUID())));
      assertThat(serviceUserClassifier.isServiceUser(user.id())).isTrue();
    }
  }

  @Test
  public void cyclicSubgroupsDontCauseInfiniteLoop() throws Exception {
    TestAccount user = accountCreator.create();
    AccountGroup.UUID subGroupUUID = groupOperations.newGroup().name("CI Service Users").create();
    groupOperations.group(serviceUsersUUID()).forUpdate().addSubgroup(subGroupUUID).update();
    groupOperations.group(subGroupUUID).forUpdate().addSubgroup(serviceUsersUUID()).update();
    assertThat(serviceUserClassifier.isServiceUser(user.id())).isFalse();
  }

  private AccountGroup.UUID serviceUsersUUID() {
    return groupCache
        .get(AccountGroup.nameKey(ServiceUserClassifier.SERVICE_USERS))
        .orElseThrow(() -> new IllegalStateException("unable to find 'Service Users'"))
        .getGroupUUID();
  }
}
