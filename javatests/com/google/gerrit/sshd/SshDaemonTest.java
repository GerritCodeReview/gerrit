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

package com.google.gerrit.sshd;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.server.session.ServerSession;
import org.junit.Test;

public class SshDaemonTest {
  @Test
  public void createSshSessionUsesProxyAddressWhenAvailable() {
    ServerSession session = mock(ServerSession.class);
    IoSession io = mock(IoSession.class);
    SocketAddress proxyAddress = new InetSocketAddress("192.0.2.1", 22);
    SocketAddress socketAddress = new InetSocketAddress("198.51.100.1", 22);
    when(session.getClientAddress()).thenReturn(proxyAddress);
    when(io.getRemoteAddress()).thenReturn(socketAddress);

    SshSession sshSession = SshDaemon.createSshSession(1, session, io);

    assertThat(sshSession.getRemoteAddress()).isEqualTo(proxyAddress);
  }

  @Test
  public void createSshSessionFallsBackToSocketAddress() {
    ServerSession session = mock(ServerSession.class);
    IoSession io = mock(IoSession.class);
    SocketAddress socketAddress = new InetSocketAddress("198.51.100.1", 22);
    when(session.getClientAddress()).thenReturn(null);
    when(io.getRemoteAddress()).thenReturn(socketAddress);

    SshSession sshSession = SshDaemon.createSshSession(1, session, io);

    assertThat(sshSession.getRemoteAddress()).isEqualTo(socketAddress);
  }
}
