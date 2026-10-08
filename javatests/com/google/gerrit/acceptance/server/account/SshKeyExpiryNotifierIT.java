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

package com.google.gerrit.acceptance.server.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.google.gerrit.acceptance.testsuite.request.SshSessionFactory;
import com.google.gerrit.server.account.SshKeyExpiryNotifier;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.Test;

@NoHttpd
@UseSsh
public class SshKeyExpiryNotifierIT extends AbstractDaemonTest {
  @Inject private SshKeyExpiryNotifier notifier;
  @Inject private VersionedAuthorizedKeys.Accessor authorizedKeys;

  @Test
  public void noEmailForKeysWithoutExpiry() throws Exception {
    addKey(Optional.empty());

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void emailSentForKeyExpiringInAWeek() throws Exception {
    String key = addKey(Optional.of(Instant.now().plus(Duration.ofHours(6 * 24 + 12))));

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).body()).contains("will expire");
    assertThat(sender.getMessages().get(0).body()).contains(key);
    assertThat(sender.getMessages().get(0).htmlBody()).contains("will expire");
  }

  @Test
  public void emailSentForKeyThatExpired() throws Exception {
    String key = addKey(Optional.of(Instant.now().minus(Duration.ofHours(12))));

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).body()).contains("has expired");
    assertThat(sender.getMessages().get(0).body()).contains(key);
    assertThat(sender.getMessages().get(0).htmlBody()).contains("has expired");
  }

  @Test
  public void noEmailForKeyExpiringLater() throws Exception {
    addKey(Optional.of(Instant.now().plus(Duration.ofDays(30))));

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void noEmailForKeyThatExpiredLongAgo() throws Exception {
    addKey(Optional.of(Instant.now().minus(Duration.ofDays(3))));

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void noEmailForInvalidKey() throws Exception {
    addKey(Optional.of(Instant.now().minus(Duration.ofHours(12))));
    int seq = authorizedKeys.getKeys(user.id()).size();
    testRefAction(() -> authorizedKeys.markKeyInvalid(user.id(), seq));

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).isEmpty();
  }

  private String addKey(Optional<Instant> expiry) throws Exception {
    String key = TestSshKeys.publicKey(SshSessionFactory.genSshKey(), user.email());
    testRefAction(() -> authorizedKeys.addKey(user.id(), key, expiry));
    return key;
  }
}
