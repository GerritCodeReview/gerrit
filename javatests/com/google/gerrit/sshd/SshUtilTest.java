// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.sshd;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.AccountSshKey;
import java.security.spec.InvalidKeySpecException;
import org.junit.Test;

public class SshUtilTest {
  private static final Account.Id TEST_ACCOUNT_ID = Account.id(1);
  private static final int TEST_SSHKEY_SEQUENCE = 1;
  private static final String INVALID_ALGO = "invalid-algo";
  private static final String VALID_OPENSSH_RSA_KEY =
      "AAAAB3NzaC1yc2EAAAABIwAAAIEA0R66EoZ7hFp81w9sAJqu34UFyE+w36H/mobUqnT5Lns7PcTOJh3sgMJAlswX2lFAWqvF2gd2PRMpMhbfEU4iq2SfY8x+RDCJ4ZQWESln/587T41BlQjOXzu3W1bqgmtHnRCte3DjyWDvM/fucnUMSwOgP+FVEZCLTrk3thLMWsU=";
  private static final Object VALID_SSH_RSA_ALGO = "ssh-rsa";

  @Test
  public void shouldFailParsingOpenSshKeyWithInvalidAlgo() {
    String sshKeyWithInvalidAlgo = String.format("%s %s", INVALID_ALGO, VALID_OPENSSH_RSA_KEY);
    AccountSshKey sshKey =
        AccountSshKey.create(TEST_ACCOUNT_ID, TEST_SSHKEY_SEQUENCE, sshKeyWithInvalidAlgo);
    assertThrows(InvalidKeySpecException.class, () -> SshUtil.parse(sshKey));
  }

  @Test
  public void shouldParseSshKeyWithAlgoMatchingKey() {
    String sshKeyWithValidKeyAlgo =
        String.format("%s %s", VALID_SSH_RSA_ALGO, VALID_OPENSSH_RSA_KEY);
    AccountSshKey sshKey =
        AccountSshKey.create(TEST_ACCOUNT_ID, TEST_SSHKEY_SEQUENCE, sshKeyWithValidKeyAlgo);
    assertThat(sshKey).isNotNull();
  }
}
