// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Scanner;

public class SshSession {
  private final InetSocketAddress addr;
  private final TestAccount account;
  private Session session;
  private String error;

  public SshSession(GerritServer server, TestAccount account) {
    this.addr = server.getSshdAddress();
    this.account = account;
  }

  public void open() throws JSchException {
    getSession();
  }

  @SuppressWarnings("resource")
  public String exec(String command, InputStream opt) throws JSchException, IOException {
    ChannelExec channel = (ChannelExec) getSession().openChannel("exec");
    try {
      channel.setCommand(command);
      channel.setInputStream(opt);
      InputStream in = channel.getInputStream();
      InputStream err = channel.getErrStream();
      channel.connect();

      Scanner s = new Scanner(err, UTF_8.name()).useDelimiter("\\A");
      error = s.hasNext() ? s.next() : null;

      s = new Scanner(in, UTF_8.name()).useDelimiter("\\A");
      return s.hasNext() ? s.next() : "";
    } finally {
      channel.disconnect();
    }
  }

  public InputStream exec2(String command, InputStream opt) throws JSchException, IOException {
    ChannelExec channel = (ChannelExec) getSession().openChannel("exec");
    channel.setCommand(command);
    channel.setInputStream(opt);
    InputStream in = channel.getInputStream();
    channel.connect();
    return in;
  }

  public String exec(String command) throws JSchException, IOException {
    return exec(command, null);
  }

  private boolean hasError() {
    return error != null;
  }

  public String getError() {
    return error;
  }

  public void assertSuccess() {
    assert_().withFailureMessage(getError()).that(hasError()).isFalse();
  }

  public void assertFailure() {
    assertThat(hasError()).isTrue();
  }

  public void assertFailure(String error) {
    assertThat(hasError()).isTrue();
    assertThat(getError()).contains(error);
  }

  public void close() {
    if (session != null) {
      session.disconnect();
      session = null;
    }
  }

  private Session getSession() throws JSchException {
    if (session == null) {
      JSch jsch = new JSch();
      jsch.addIdentity("KeyPair", account.privateKey(), account.sshKey.getPublicKeyBlob(), null);
      session =
          jsch.getSession(account.username, addr.getAddress().getHostAddress(), addr.getPort());
      session.setConfig("StrictHostKeyChecking", "no");
      session.connect();
    }
    return session;
  }

  public String getUrl() {
    checkState(session != null, "session must be opened");
    StringBuilder b = new StringBuilder();
    b.append("ssh://");
    b.append(session.getUserName());
    b.append("@");
    b.append(session.getHost());
    b.append(":");
    b.append(session.getPort());
    return b.toString();
  }

  public TestAccount getAccount() {
    return account;
  }
}
