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
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Scanner;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.DefaultProxyDataFactory;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSession;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.util.FS;

public class SshSession {
  private static final int TIMEOUT = 100000;

  private final TestSshKeys sshKeys;
  private final InetSocketAddress addr;
  private final TestAccount account;
  private SshdSession session;
  private File userhome;
  private String error;

  public SshSession(TestSshKeys sshKeys, GerritServer server, TestAccount account) {
    this.sshKeys = sshKeys;
    this.addr = server.getSshdAddress();
    this.account = account;
  }

  public void open() throws Exception {
    getSession();
  }

  @SuppressWarnings("resource")
  public String exec(String command) throws Exception {
    Process process = getSession().exec(command, TIMEOUT);
    InputStream in = process.getInputStream();
    InputStream err = process.getErrorStream();

    Scanner s = new Scanner(err, UTF_8.name()).useDelimiter("\\A");
    error = s.hasNext() ? s.next() : null;

    s = new Scanner(in, UTF_8.name()).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }

  public InputStream exec2(String command, InputStream opt) throws Exception {
    Process process = getSession().exec(command, TIMEOUT);
    if (opt != null) {
      ByteStreams.copy(opt, process.getOutputStream());
    }
    return process.getInputStream();
  }

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

  public void close() {
    if (session != null) {
      session.disconnect();
      session = null;
    }
  }

  private SshdSession getSession() throws Exception {
    if (session == null) {
      assertWithMessage("account " + account.id + " must have a username to use SSH")
          .that(account.username)
          .isNotNull();
      String username = account.username;

      URIish uri =
          new URIish(
              "ssh://"
                  + username
                  + "@"
                  + addr.getAddress().getHostAddress()
                  + ":"
                  + addr.getPort());

      // TODO(davido): Switch to memory only key resolving mode.
      userhome = Files.createTempDir();

      FS fs = FS.DETECTED.setUserHome(userhome);
      File sshDir = new File(userhome, ".ssh");
      sshDir.mkdir();
      try (OutputStream out = new FileOutputStream(new File(sshDir, "id_ecdsa"))) {
        sshKeys.getKeyPair(account).writePrivateKey(out);
      }

      // TODO(davido): Disable programmatically host key checking: "StrictHostKeyChecking: no" mode.
      CharSink configFile = Files.asCharSink(new File(sshDir, "config"), UTF_8);
      configFile.writeLines(Arrays.asList("Host *", "StrictHostKeyChecking no"));

      JGitKeyCache keyCache = new JGitKeyCache();
      try (SshdSessionFactory factory =
          new SshdSessionFactory(keyCache, new DefaultProxyDataFactory())) {
        factory.setHomeDirectory(userhome);
        factory.setSshDirectory(sshDir);

        session = factory.getSession(uri, null, fs, TIMEOUT);

        session.addCloseListener(
            future -> {
              try {
                MoreFiles.deleteRecursively(userhome.toPath(), ALLOW_INSECURE);
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
      }
    }
    return session;
  }

  public String getUrl() {
    checkState(session != null, "session must be opened");
    StringBuilder b = new StringBuilder();
    b.append("ssh://");
    b.append(account.username);
    b.append("@");
    b.append(addr.getAddress().getHostAddress());
    b.append(":");
    b.append(addr.getPort());
    return b.toString();
  }

  public TestAccount getAccount() {
    return account;
  }

  public File getUserhome() {
    return userhome;
  }
}
