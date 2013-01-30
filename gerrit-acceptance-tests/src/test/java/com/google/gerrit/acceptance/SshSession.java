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

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

class SshSession {

  private final TestAccount account;
  private Session session;

  SshSession(TestAccount account) {
    this.account = account;
  }

  String exec(String command) throws JSchException, IOException {
    ChannelExec channel = (ChannelExec) getSession().openChannel("exec");
    try {
      channel.setCommand(command);
      channel.setInputStream(null);
      InputStream in = channel.getInputStream();
      channel.connect();

      Scanner s = new Scanner(in).useDelimiter("\\A");
      return s.hasNext() ? s.next() : "";
    } finally {
      channel.disconnect();
    }
  }

  void close() {
    if (session != null) {
      session.disconnect();
      session = null;
    }
  }

  private Session getSession() throws JSchException {
    if (session == null) {
      JSch jsch = new JSch();
      jsch.addIdentity("KeyPair",
          account.privateKey(), account.sshKey.getPublicKeyBlob(), null);
      session = jsch.getSession(account.username, "localhost", 29418);
      session.setConfig("StrictHostKeyChecking", "no");
      session.connect();
    }
    return session;
  }
}
