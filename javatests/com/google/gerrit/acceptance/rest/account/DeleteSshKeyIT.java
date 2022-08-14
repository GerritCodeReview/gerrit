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
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.restapi.account.DeleteSshKey;
import com.google.inject.Inject;
import java.security.KeyPair;
import java.util.List;
import org.junit.Test;

public class DeleteSshKeyIT extends AbstractDaemonTest {

  @Inject VersionedAuthorizedKeys.Accessor authorizedKeys;
  @Inject DeleteSshKey deleteSshKey;

  private void setupSshKeys() throws Exception {
    KeyPair keyPair = sshKeys.getKeyPair(user);
    AccountSshKey sshKey =
        authorizedKeys.addKey(
            user(user).getAccountId(), TestSshKeys.publicKey(keyPair, user.email()));
    assertThat(authorizedKeys.getKeys(user.id())).contains(sshKey);
  }

  private void assertDeleteSshKeys(List<AccountSshKey> sshKeysBeforeDel, AccountSshKey sshKey)
      throws Exception {
    List<AccountSshKey> sshKeysAfterDel = authorizedKeys.getKeys(user.id());
    assertThat(sshKeysAfterDel.size()).isEqualTo(sshKeysBeforeDel.size() - 1);
    assertThat(sshKeysAfterDel).doesNotContain(sshKey);
  }

  @Test
  @UseSsh
  public void deleteSshKeyRestApi() throws Exception {
    setupSshKeys();
    List<AccountSshKey> sshKeysBeforeDel = authorizedKeys.getKeys(user.id());
    AccountSshKey sshKey = sshKeysBeforeDel.get(0);
    gApi.accounts().id(user.id().get()).deleteSshKey(sshKey.seq());
    assertDeleteSshKeys(sshKeysBeforeDel, sshKey);
  }

  @Test
  @UseSsh
  public void deleteSshKeyOnBehalf() throws Exception {
    setupSshKeys();
    List<AccountSshKey> sshKeysBeforeDel = authorizedKeys.getKeys(user.id());
    AccountSshKey sshKey = sshKeysBeforeDel.get(0);
    deleteSshKey.apply(identifiedUserFactory.create(user.id()), sshKey);
    assertDeleteSshKeys(sshKeysBeforeDel, sshKey);
  }
}
