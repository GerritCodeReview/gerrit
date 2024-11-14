// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

@UseSsh
@NoHttpd
public class SetAccountIT extends AbstractDaemonTest {
  @Inject private ExternalIds externalIds;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void setAccount_deleteExternalId_all() throws Exception {
    TestAccount testAccount = accountCreator.create("user1", "user1@example.com", null, null);
    adminSshSession.exec("gerrit set-account --delete-external-id ALL user1");
    adminSshSession.assertSuccess();
    assertThat(externalIds.byAccount(testAccount.id()).isEmpty()).isTrue();
  }

  @Test
  public void setAccount_deleteExternalId_single() throws Exception {
    TestAccount testAccount = accountCreator.create("user2", "user2@example.com", null, null);
    List<String> extIdKeys = getExternalIdKeys(testAccount);
    assertThat(extIdKeys.contains("username:user2")).isTrue();
    assertThat(extIdKeys.contains("mailto:user2@example.com")).isTrue();
    adminSshSession.exec("gerrit set-account --delete-external-id username:user2 user2");
    adminSshSession.assertSuccess();
    extIdKeys = getExternalIdKeys(testAccount);
    assertThat(extIdKeys.contains("username:user3")).isFalse();
    assertThat(extIdKeys.contains("mailto:user3@example.com")).isFalse();
  }

  @Test
  public void setAccount_deleteExternalId_multiple() throws Exception {
    TestAccount testAccount = accountCreator.create("user3", "user3@example.com", null, null);
    List<String> extIdKeys = getExternalIdKeys(testAccount);
    assertThat(extIdKeys.contains("username:user3")).isTrue();
    assertThat(extIdKeys.contains("mailto:user3@example.com")).isTrue();
    adminSshSession.exec(
        "gerrit set-account --delete-external-id username:user3 --delete-external-id"
            + " mailto:user3@example.com user3");
    adminSshSession.assertSuccess();
    extIdKeys = getExternalIdKeys(testAccount);
    assertThat(extIdKeys.contains("username:user3")).isFalse();
    assertThat(extIdKeys.contains("mailto:user3@example.com")).isFalse();
  }

  @Test
  public void setAccount_deleteExternalId_byUser() throws Exception {
    userSshSession.exec("gerrit set-account --delete-external-id mailto:admin@example.com admin");
    userSshSession.assertFailure();
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.MODIFY_ACCOUNT).group(REGISTERED_USERS))
        .update();
    userSshSession.exec("gerrit set-account --delete-external-id mailto:admin@example.com admin");
    userSshSession.assertSuccess();
    userSshSession.exec("gerrit set-account --delete-external-id username:admin admin");
    userSshSession.assertFailure();
  }

  private List<String> getExternalIdKeys(TestAccount account) throws Exception {
    return externalIds.byAccount(account.id()).stream()
        .map(e -> e.key().get())
        .collect(Collectors.toList());
  }
}
