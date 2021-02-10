// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.request;

import static com.google.gerrit.server.config.SshClientImplementation.getFromEnvironment;

import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.SshSessionJsch;
import com.google.gerrit.acceptance.SshSessionMina;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import java.net.InetSocketAddress;
import java.security.KeyPair;

public class SshSessionFactory {
  public static SshSession createSession(
      TestSshKeys testSshKeys, InetSocketAddress sshAddress, TestAccount testAccount) {
    return getFromEnvironment().isMina()
        ? new SshSessionMina(testSshKeys, sshAddress, testAccount)
        : new SshSessionJsch(testSshKeys, sshAddress, testAccount);
  }

  public static void initSsh(KeyPair keyPair) {
    if (getFromEnvironment().isMina()) {
      SshSessionMina.initClient();
    } else {
      SshSessionJsch.initClient(keyPair);
    }
  }

  private SshSessionFactory() {}
}
