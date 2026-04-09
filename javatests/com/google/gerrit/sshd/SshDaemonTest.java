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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.Test;

public class SshDaemonTest {
  @Test
  public void updateRemoteAddressUsesNewAddress() {
    SocketAddress socketAddress = new InetSocketAddress("198.51.100.1", 22);
    SocketAddress proxyAddress = new InetSocketAddress("192.0.2.1", 22);
    SshSession sshSession = new SshSession(1, socketAddress);

    sshSession.updateRemoteAddress(proxyAddress);

    assertThat(sshSession.getRemoteAddress()).isEqualTo(proxyAddress);
    assertThat(sshSession.getRemoteAddressAsString()).isEqualTo("192.0.2.1");
  }

  @Test
  public void updateRemoteAddressIgnoresNull() {
    SocketAddress socketAddress = new InetSocketAddress("198.51.100.1", 22);
    SshSession sshSession = new SshSession(1, socketAddress);

    sshSession.updateRemoteAddress(null);

    assertThat(sshSession.getRemoteAddress()).isEqualTo(socketAddress);
    assertThat(sshSession.getRemoteAddressAsString()).isEqualTo("198.51.100.1");
  }
}
