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
import com.google.gerrit.server.util.time.TimeUtil;
import java.time.Instant;
import org.junit.After;
import org.junit.Test;

public class AccountSshKeyTest {
  private static final String KEY =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCgug5VyMXQGnem2H1KVC4/HcRcD4zzBqS"
          + "uJBRWVonSSoz3RoAZ7bWXCVVGwchtXwUURD689wFYdiPecOrWOUgeeyRq754YWRhU+W28"
          + "vf8IZixgjCmiBhaL2gt3wff6pP+NXJpTSA4aeWE5DfNK5tZlxlSxqkKOS8JRSUeNQov5T"
          + "w== john.doe@example.com";

  @After
  public void resetClock() {
    TimeUtil.resetCurrentMillisSupplier();
  }

  @Test
  public void noExpiryTime() {
    AccountSshKey key = key("");
    assertThat(key.expiryTime()).isEmpty();
    assertThat(key.isExpired()).isFalse();
  }

  @Test
  public void expiryTimeWithSeconds() {
    AccountSshKey key = key("expiry-time=\"20300102030405Z\" ");
    Instant expiry = Instant.parse("2030-01-02T03:04:05Z");
    assertThat(key.expiryTime()).hasValue(expiry);
    TimeUtil.setCurrentMillisSupplier(expiry::toEpochMilli);
    assertThat(key.isExpired()).isFalse();
    TimeUtil.setCurrentMillisSupplier(() -> expiry.plusSeconds(1).toEpochMilli());
    assertThat(key.isExpired()).isTrue();
  }

  @Test
  public void expiryTimeWithMinutes() {
    assertThat(key("expiry-time=\"203001020304Z\" ").expiryTime())
        .hasValue(Instant.parse("2030-01-02T03:04:00Z"));
  }

  @Test
  public void expiryTimeWithDateOnly() {
    assertThat(key("expiry-time=\"20300102Z\" ").expiryTime())
        .hasValue(Instant.parse("2030-01-02T00:00:00Z"));
  }

  @Test
  public void expiryTimeAmongOtherOptions() {
    AccountSshKey key = key("no-pty,expiry-time=\"20300102Z\",no-agent-forwarding ");
    assertThat(key.expiryTime()).hasValue(Instant.parse("2030-01-02T00:00:00Z"));
    assertThat(key.algorithm()).isEqualTo("ssh-rsa");
    assertThat(key.comment()).isEqualTo("john.doe@example.com");
  }

  @Test
  public void malformedExpiryTimeIsIgnored() {
    for (String value : new String[] {"tomorrow", "2030", "20301340Z", "20300230Z"}) {
      AccountSshKey key = key("expiry-time=\"" + value + "\" ");
      assertThat(key.expiryTime()).isEmpty();
      assertThat(key.isExpired()).isFalse();
    }
  }

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
