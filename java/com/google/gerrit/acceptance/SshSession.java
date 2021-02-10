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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import java.net.InetSocketAddress;

public abstract class SshSession {
  protected final TestSshKeys sshKeys;
  protected final InetSocketAddress addr;
  protected final TestAccount account;
  protected String error;

  public SshSession(TestSshKeys sshKeys, InetSocketAddress addr, TestAccount account) {
    this.sshKeys = sshKeys;
    this.addr = addr;
    this.account = account;
  }

  public abstract void open() throws Exception;

  public abstract void close();

  public abstract String exec(String command) throws Exception;

  public abstract int execAndReturnStatus(String command) throws Exception;

  private boolean hasError() {
    return error != null;
  }

  public String getError() {
    return error;
  }

  public void assertSuccess() {
    assertWithMessage(getError()).that(hasError()).isFalse();
  }

  public void assertFailure() {
    assertThat(hasError()).isTrue();
  }

  public void assertFailure(String error) {
    assertThat(hasError()).isTrue();
    assertThat(getError()).contains(error);
  }

  public String getUrl() {
    StringBuilder b = new StringBuilder();
    b.append("ssh://");
    b.append(account.username().get());
    b.append("@");
    b.append(addr.getAddress().getHostAddress());
    b.append(":");
    b.append(addr.getPort());
    return b.toString();
  }

  protected String getUsername() {
    return account
        .username()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "account " + account.accountId() + " must have a username to use SSH"));
  }
}
