// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Account;
import org.junit.Test;

public class AccountSshKeyTest {
  private static final String KEY =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCgug5VyMXQGnem2H1KVC4/HcRcD4zzBqS"
          + "uJBRWVonSSoz3RoAZ7bWXCVVGwchtXwUURD689wFYdiPecOrWOUgeeyRq754YWRhU+W28"
          + "vf8IZixgjCmiBhaL2gt3wff6pP+NXJpTSA4aeWE5DfNK5tZlxlSxqkKOS8JRSUeNQov5T"
          + "w== john.doe@example.com";

  @Test
  public void optionsSurviveSerialization() {
    AccountSshKey key =
        key("no-pty,!agent-forwarding,expiry-time=\"20300102Z\",command=\"echo a b\" ");
    AccountSshKey reparsed = new AccountSshKey(Account.id(1), 1, key.sshPublicKey());
    assertThat(reparsed).isEqualTo(key);
    assertThat(reparsed.sshPublicKey()).isEqualTo(key.sshPublicKey());
  }

  @Test
  public void keyWithoutOptionsIsSerializedAsIs() {
    assertThat(key("").sshPublicKey()).isEqualTo(KEY);
  }

  @Test
  public void unparsableKeyIsKeptAsIs() {
    String unparsable = "ssh-rsa not-base64!! john.doe@example.com";
    AccountSshKey key = new AccountSshKey(Account.id(1), 1, unparsable);
    assertThat(key.key()).isEmpty();
    assertThat(key.sshPublicKey()).isEqualTo(unparsable);
    assertThat(key.asInvalid().sshPublicKey()).isEqualTo(unparsable);
  }

  private static AccountSshKey key(String options) {
    return new AccountSshKey(Account.id(1), 1, options + KEY);
  }
}
