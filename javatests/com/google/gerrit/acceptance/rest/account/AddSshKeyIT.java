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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.google.gerrit.acceptance.testsuite.request.SshSessionFactory;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.util.time.TimeUtil;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@UseSsh
public class AddSshKeyIT extends AbstractDaemonTest {
  private static final DateTimeFormatter EXPIRY_TIME =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

  private Instant now;
  private String key;

  @Before
  public void setup() throws Exception {
    now = TimeUtil.truncateToSecond(TimeUtil.now());
    TimeUtil.setCurrentMillisSupplier(now::toEpochMilli);
    key = TestSshKeys.publicKey(SshSessionFactory.genSshKey(), admin.email());
  }

  @After
  public void resetClock() {
    TimeUtil.resetCurrentMillisSupplier();
  }

  @Test
  public void noLifetimeAddsKeyWithoutExpiry() throws Exception {
    gApi.accounts().self().addSshKey(key);
    assertThat(lastKey().sshPublicKey).isEqualTo(key);
    assertThat(lastKey().expiration).isNull();
  }

  @Test
  public void lifetimeSetsExpiryTime() throws Exception {
    SshKeyInfo added = gApi.accounts().self().addSshKey(key, "30d");
    assertThat(added.expiration).isEqualTo(Timestamp.from(now.plus(Duration.ofDays(30))));
    assertThat(lastKey().sshPublicKey).isEqualTo(expiring(Duration.ofDays(30)) + key);
    assertThat(lastKey().expiration).isEqualTo(added.expiration);
  }

  @Test
  public void lifetimeWithExpiryTimeOptionIsRejected() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.accounts().self().addSshKey("expiry-time=\"20200101Z\" " + key, "1h"));
    assertThat(thrown).hasMessageThat().contains("expiry-time");
    assertThat(gApi.accounts().self().listSshKeys()).hasSize(1);
  }

  @Test
  @GerritConfig(name = "auth.maxSshKeyLifetime", value = "7d")
  public void maxLifetimeIsAppliedAsDefault() throws Exception {
    SshKeyInfo added = gApi.accounts().self().addSshKey(key);
    assertThat(added.expiration).isEqualTo(Timestamp.from(now.plus(Duration.ofDays(7))));
    assertThat(lastKey().sshPublicKey).isEqualTo(expiring(Duration.ofDays(7)) + key);
  }

  @Test
  @GerritConfig(name = "auth.maxSshKeyLifetime", value = "7d")
  public void expiryTimeOptionIsCappedAtMax() throws Exception {
    SshKeyInfo added = gApi.accounts().self().addSshKey("expiry-time=\"20990101000000Z\" " + key);
    assertThat(added.expiration).isEqualTo(Timestamp.from(now.plus(Duration.ofDays(7))));
  }

  @Test
  @GerritConfig(name = "auth.maxSshKeyLifetime", value = "7d")
  public void shorterExpiryTimeOptionIsKept() throws Exception {
    SshKeyInfo added = gApi.accounts().self().addSshKey(expiring(Duration.ofDays(2)) + key);
    assertThat(added.expiration).isEqualTo(Timestamp.from(now.plus(Duration.ofDays(2))));
  }

  @Test
  @GerritConfig(name = "auth.maxSshKeyLifetime", value = "7d")
  public void lifetimeIsCappedAtMax() throws Exception {
    SshKeyInfo added = gApi.accounts().self().addSshKey(key, "8d");
    assertThat(added.expiration).isEqualTo(Timestamp.from(now.plus(Duration.ofDays(7))));
  }

  @Test
  @GerritConfig(name = "auth.maxSshKeyLifetime", value = "7d")
  public void shorterLifetimeIsKept() throws Exception {
    SshKeyInfo added = gApi.accounts().self().addSshKey(key, "6d");
    assertThat(added.expiration).isEqualTo(Timestamp.from(now.plus(Duration.ofDays(6))));
  }

  @Test
  public void expiredKeyCannotBeAddedAgain() throws Exception {
    gApi.accounts().self().addSshKey(key, "1h");
    Instant later = now.plus(Duration.ofHours(2));
    TimeUtil.setCurrentMillisSupplier(later::toEpochMilli);

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> gApi.accounts().self().addSshKey(key, "1h"));
    assertThat(thrown).hasMessageThat().contains("expired");
    assertThat(gApi.accounts().self().listSshKeys()).hasSize(2);
  }

  @Test
  public void lifetimeAsQueryParameter() throws Exception {
    RestResponse r =
        adminRestSession.postRaw("/accounts/self/sshkeys?lifetime=2h", RawInputUtil.create(key));
    r.assertCreated();
    assertThat(lastKey().sshPublicKey).isEqualTo(expiring(Duration.ofHours(2)) + key);
  }

  @Test
  public void invalidLifetime() throws Exception {
    for (String lifetime : new String[] {"foo", "0", "0d", "-1d"}) {
      BadRequestException thrown =
          assertThrows(
              BadRequestException.class, () -> gApi.accounts().self().addSshKey(key, lifetime));
      assertThat(thrown).hasMessageThat().contains("lifetime");
    }
    assertThat(gApi.accounts().self().listSshKeys()).hasSize(1);
  }

  private String expiring(Duration lifetime) {
    return "expiry-time=\"" + EXPIRY_TIME.format(now.plus(lifetime)) + "\" ";
  }

  private SshKeyInfo lastKey() throws Exception {
    List<SshKeyInfo> keys = gApi.accounts().self().listSshKeys();
    return keys.get(keys.size() - 1);
  }
}
