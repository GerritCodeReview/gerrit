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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.google.gerrit.acceptance.testsuite.request.SshSessionFactory;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshException;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Test;

@NoHttpd
@UseSsh
public class SshKeyExpiryIT extends AbstractDaemonTest {
  private static final DateTimeFormatter EXPIRY_TIME =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final int TIMEOUT_MILLIS = 100_000;

  @Inject private VersionedAuthorizedKeys.Accessor authorizedKeys;
  @Inject private SshKeyCache sshKeyCache;

  @Test
  public void keyBeforeExpiryTimeCanLogin() throws Exception {
    KeyPair key = addKeyExpiringAt(TimeUtil.now().plus(Duration.ofDays(1)));
    assertThat(canLogin(key)).isTrue();
  }

  @Test
  public void keyAfterExpiryTimeCannotLogin() throws Exception {
    KeyPair key = addKeyExpiringAt(TimeUtil.now().minus(Duration.ofDays(1)));
    assertThat(canLogin(key)).isFalse();
  }

  @Test
  public void keyThatExpiresWhileCachedCannotLogin() throws Exception {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    KeyPair key = addKeyExpiringAt(TimeUtil.now().plus(Duration.ofDays(1)));
    assertThat(canLogin(key)).isTrue();

    TestTimeUtil.incrementClock(2, TimeUnit.DAYS);
    assertThat(canLogin(key)).isFalse();
  }

  @After
  public void resetClock() {
    TestTimeUtil.useSystemTime();
  }

  private KeyPair addKeyExpiringAt(Instant expiry) throws Exception {
    KeyPair key = SshSessionFactory.genSshKey();
    String line =
        String.format(
            "expiry-time=\"%s\" %s", EXPIRY_TIME.format(expiry), TestSshKeys.publicKey(key, null));
    testRefAction(() -> authorizedKeys.addKey(user.id(), line));
    sshKeyCache.evict(user.username());
    return key;
  }

  private boolean canLogin(KeyPair key) throws Exception {
    int port = new URIish(adminSshSession.getUrl()).getPort();
    SshClient client = SshClient.setUpDefaultClient();
    client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
    client.start();
    try (ClientSession session =
        client
            .connect(user.username(), new InetSocketAddress(InetAddress.getLoopbackAddress(), port))
            .verify(TIMEOUT_MILLIS)
            .getSession()) {
      session.addPublicKeyIdentity(key);
      session.auth().verify(TIMEOUT_MILLIS);
      return true;
    } catch (SshException e) {
      return false;
    } finally {
      client.stop();
    }
  }
}
