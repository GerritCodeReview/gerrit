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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

@NoHttpd
@UseSsh
public class SshProxyProtocolIT extends AbstractDaemonTest {
  private static final String PROXY_SOURCE_ADDRESS = "192.0.2.1";
  private static final String SERVER_ADDRESS = "127.0.0.1";
  private static final int PROXY_SOURCE_PORT = 12345;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("sshd", null, "enableProxyProtocol", true);
    return cfg;
  }

  @Inject private AccountOperations accountOperations;

  @Test
  public void proxyProtocolSourceIsReportedByShowConnections() throws Exception {
    int sshPort = new URIish(adminSshSession.getUrl()).getPort();
    for (ProxyProtocolFormat format : ProxyProtocolFormat.values()) {
      if (format == ProxyProtocolFormat.V2_LOCAL) {
        continue;
      }
      try (ProxyProtocolSshSession proxiedSession =
          new ProxyProtocolSshSession(
              sshKeys, sshPort, accountOperations.account(admin.id()).get(), format)) {
        proxiedSession.open();

        String output = adminSshSession.exec("gerrit show-connections --numeric --wide");
        assertThat(output).contains(PROXY_SOURCE_ADDRESS);
      }
    }
  }

  // A v2 LOCAL command describes a connection made by the proxy itself, not a relayed client.
  // The receiver must ignore the address block and keep the actual socket peer address.
  @Test
  public void proxyProtocolLocalCommandUsesActualPeerAddress() throws Exception {
    int sshPort = new URIish(adminSshSession.getUrl()).getPort();
    try (ProxyProtocolSshSession localSession =
        new ProxyProtocolSshSession(
            sshKeys,
            sshPort,
            accountOperations.account(admin.id()).get(),
            ProxyProtocolFormat.V2_LOCAL)) {
      localSession.open();

      String output = adminSshSession.exec("gerrit show-connections --numeric --wide");
      assertThat(output).contains(SERVER_ADDRESS);
      assertThat(output).doesNotContain(PROXY_SOURCE_ADDRESS);
    }
  }

  private enum ProxyProtocolFormat {
    V1_PROXY,
    V2_PROXY,
    V2_LOCAL
  }

  private static final class ProxyProtocolSshSession implements AutoCloseable {
    private static final byte[] PROXY_PROTOCOL_V2_SIGNATURE = {
      0x0d, 0x0a, 0x0d, 0x0a, 0x00, 0x0d, 0x0a, 0x51, 0x55, 0x49, 0x54, 0x0a
    };
    private static final int TIMEOUT_MILLIS = 100_000;

    private final TestSshKeys sshKeys;
    private final int targetPort;
    private final TestAccount account;
    private final ProxyProtocolFormat format;

    private SshClient client;
    private ClientSession session;

    ProxyProtocolSshSession(
        TestSshKeys sshKeys, int targetPort, TestAccount account, ProxyProtocolFormat format) {
      this.sshKeys = sshKeys;
      this.targetPort = targetPort;
      this.account = account;
      this.format = format;
    }

    void open() throws Exception {
      client = SshClient.setUpDefaultClient();
      client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
      client.setClientProxyConnector(
          session ->
              session
                  .getIoSession()
                  .writeBuffer(new ByteArrayBuffer(createProxyProtocolHeader(format, targetPort))));
      client.start();
      try {
        session =
            client
                .connect(
                    account.username().get(),
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), targetPort))
                .verify(TIMEOUT_MILLIS)
                .getSession();
        session.addPublicKeyIdentity(sshKeys.getKeyPair(account));
        session.auth().verify(TIMEOUT_MILLIS);
      } catch (Exception e) {
        close();
        throw e;
      }
    }

    private static byte[] createProxyProtocolHeader(ProxyProtocolFormat format, int targetPort)
        throws UnknownHostException {
      if (format == ProxyProtocolFormat.V1_PROXY) {
        return ("PROXY TCP4 "
                + PROXY_SOURCE_ADDRESS
                + " "
                + SERVER_ADDRESS
                + " "
                + PROXY_SOURCE_PORT
                + " "
                + targetPort
                + "\r\n")
            .getBytes(StandardCharsets.US_ASCII);
      }

      ByteBuffer header = ByteBuffer.allocate(28);
      header.put(PROXY_PROTOCOL_V2_SIGNATURE);
      header.put((byte) (format == ProxyProtocolFormat.V2_LOCAL ? 0x20 : 0x21));
      header.put((byte) 0x11); // IPv4, stream transport.
      header.putShort((short) 12); // IPv4 addresses and ports: 4 + 4 + 2 + 2 bytes.
      header.put(InetAddress.getByName(PROXY_SOURCE_ADDRESS).getAddress());
      header.put(InetAddress.getByName(SERVER_ADDRESS).getAddress());
      header.putShort((short) PROXY_SOURCE_PORT);
      header.putShort((short) targetPort);
      return header.array();
    }

    @Override
    public void close() throws Exception {
      try {
        if (session != null) {
          session.close();
        }
      } finally {
        if (client != null) {
          client.stop();
        }
      }
    }
  }
}
