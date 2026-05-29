// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.restapi.account.DeleteSshKey;
import com.google.inject.Inject;
import java.security.KeyPair;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class DeleteSshKeyIT extends AbstractDaemonTest {

  private static final String KEY1 =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCgug5VyMXQGnem2H1KVC4/HcRcD4zzBqS"
          + "uJBRWVonSSoz3RoAZ7bWXCVVGwchtXwUURD689wFYdiPecOrWOUgeeyRq754YWRhU+W28"
          + "vf8IZixgjCmiBhaL2gt3wff6pP+NXJpTSA4aeWE5DfNK5tZlxlSxqkKOS8JRSUeNQov5T"
          + "w== john.doe@example.com";

  @Inject VersionedAuthorizedKeys.Accessor authorizedKeys;
  @Inject DeleteSshKey deleteSshKey;

  private AccountSshKey userSshKey;
  private AccountSshKey adminSshKey;

  @Before
  public void setup() throws Exception {
    addUserSshKeys();
    addAdminSshKeys();
  }

  @Test
  @UseSsh
  public void assertUsersHaveSshKeysPreconditions() throws Exception {
    List<AccountSshKey> userSshKeys = authorizedKeys.getKeys(user.id());
    assertThat(userSshKeys).containsExactly(userSshKey, AccountSshKey.create(user.id(), 2, KEY1));
    List<AccountSshKey> adminSshKeys = authorizedKeys.getKeys(admin.id());
    assertThat(adminSshKeys).containsExactly(adminSshKey);
  }

  @Test
  @UseSsh
  public void deleteSshKeyRestApi() throws Exception {
    gApi.accounts().id(user.id().get()).deleteSshKey(userSshKey.seq());
    List<AccountSshKey> sshKeysAfterDel = authorizedKeys.getKeys(user.id());
    assertThat(sshKeysAfterDel).containsExactly(AccountSshKey.create(user.id(), 2, KEY1));
  }

  @Test
  @UseSsh
  public void adminCanDeleteUserSshKey() throws Exception {
    adminRestSession
        .delete(String.format("/accounts/%s/sshkeys/%d", user.id(), userSshKey.seq()))
        .assertNoContent();
    List<AccountSshKey> sshKeysAfterDel = authorizedKeys.getKeys(user.id());
    assertThat(sshKeysAfterDel).containsExactly(AccountSshKey.create(user.id(), 2, KEY1));
  }

  @Test
  @UseSsh
  public void deleteSshKeyOnBehalf() throws Exception {
    assertThat(deleteSshKey.apply(identifiedUserFactory.create(user.id()), userSshKey))
        .isEqualTo(Response.none());
    List<AccountSshKey> sshKeysAfterDel = authorizedKeys.getKeys(user.id());
    assertThat(sshKeysAfterDel).containsExactly(AccountSshKey.create(user.id(), 2, KEY1));
  }

  @Test
  @UseSsh
  public void userCanDeleteOwnSshKey() throws Exception {
    userRestSession
        .delete(String.format("/accounts/self/sshkeys/%d", userSshKey.seq()))
        .assertNoContent();
    List<AccountSshKey> sshKeysAfterDel = authorizedKeys.getKeys(user.id());
    assertThat(sshKeysAfterDel).containsExactly(AccountSshKey.create(user.id(), 2, KEY1));
  }

  @Test
  @UseSsh
  public void userCannotDeleteOtherUsersSshKey() throws Exception {
    userRestSession
        .delete(String.format("/accounts/%s/sshkeys/%d", admin.id(), adminSshKey.seq()))
        .assertNotFound();
    List<AccountSshKey> sshKeysAfterDel = authorizedKeys.getKeys(admin.id());
    assertThat(sshKeysAfterDel).containsExactly(adminSshKey);
  }

  private void addUserSshKeys() throws Exception {
    KeyPair keyPair = sshKeys.getKeyPair(user);
    userSshKey =
        authorizedKeys.addKey(
            user(user).getAccountId(), TestSshKeys.publicKey(keyPair, user.email()));
    gApi.accounts().id(user.id().get()).addSshKey(KEY1);
  }

  private void addAdminSshKeys() throws Exception {
    KeyPair keyPair = sshKeys.getKeyPair(admin);
    adminSshKey =
        authorizedKeys.addKey(
            user(admin).getAccountId(), TestSshKeys.publicKey(keyPair, admin.email()));
  }
}
