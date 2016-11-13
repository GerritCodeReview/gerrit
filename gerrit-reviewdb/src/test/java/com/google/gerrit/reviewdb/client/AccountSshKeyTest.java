// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class AccountSshKeyTest {
  private static final String KEY =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCgug5VyMXQGnem2H1KVC4/HcRcD4zzBqS"
          + "uJBRWVonSSoz3RoAZ7bWXCVVGwchtXwUURD689wFYdiPecOrWOUgeeyRq754YWRhU+W28"
          + "vf8IZixgjCmiBhaL2gt3wff6pP+NXJpTSA4aeWE5DfNK5tZlxlSxqkKOS8JRSUeNQov5T"
          + "w== john.doe@example.com";

  private final Account.Id accountId = new Account.Id(1);

  @Test
  public void testValidity() throws Exception {
    AccountSshKey key = new AccountSshKey(new AccountSshKey.Id(accountId, -1), KEY);
    assertThat(key.isValid()).isFalse();
    key = new AccountSshKey(new AccountSshKey.Id(accountId, 0), KEY);
    assertThat(key.isValid()).isFalse();
    key = new AccountSshKey(new AccountSshKey.Id(accountId, 1), KEY);
    assertThat(key.isValid()).isTrue();
  }

  @Test
  public void testGetters() throws Exception {
    AccountSshKey key = new AccountSshKey(new AccountSshKey.Id(accountId, 1), KEY);
    assertThat(key.getSshPublicKey()).isEqualTo(KEY);
    assertThat(key.getAlgorithm()).isEqualTo(KEY.split(" ")[0]);
    assertThat(key.getEncodedKey()).isEqualTo(KEY.split(" ")[1]);
    assertThat(key.getComment()).isEqualTo(KEY.split(" ")[2]);
  }
}
