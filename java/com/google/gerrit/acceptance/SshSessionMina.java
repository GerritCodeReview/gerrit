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

package com.google.gerrit.acceptance;

import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.CharSink;
import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Scanner;
import org.apache.sshd.common.cipher.ECCurves;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.DefaultProxyDataFactory;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSession;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.util.FS;

public class SshSessionMina extends SshSession {
  private static final int TIMEOUT = 100000;

  private SshdSession session;

  public static void initClient() {
    JGitKeyCache keyCache = new JGitKeyCache();
    SshdSessionFactory factory = new SshdSessionFactory(keyCache, new DefaultProxyDataFactory());
    SshSessionFactory.setInstance(factory);
  }

  public static KeyPairGenerator initKeyPairGenerator()
      throws GeneralSecurityException, InvalidKeySpecException, InvalidAlgorithmParameterException {
    int size = 256;
    KeyPairGenerator gen = SecurityUtils.getKeyPairGenerator(KeyUtils.EC_ALGORITHM);
    ECCurves curve = ECCurves.fromCurveSize(size);
    if (curve == null) {
      throw new InvalidKeySpecException("Unknown curve for key size=" + size);
    }
    gen.initialize(curve.getParameters());
    return gen;
  }

  public SshSessionMina(TestSshKeys sshKeys, InetSocketAddress addr, TestAccount account) {
    super(sshKeys, addr, account);
  }

  @Override
  public void open() throws Exception {
    getMinaSession();
  }

  @Override
  public void close() {
    if (session != null) {
      session.disconnect();
      session = null;
    }
  }

  @SuppressWarnings("resource")
  @Override
  public String exec(String command) throws Exception {
    Process process = getMinaSession().exec(command, TIMEOUT);
    InputStream in = process.getInputStream();
    InputStream err = process.getErrorStream();

    Scanner s = new Scanner(err, UTF_8.name()).useDelimiter("\\A");
    error = s.hasNext() ? s.next() : null;

    s = new Scanner(in, UTF_8.name()).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }

  @SuppressWarnings("resource")
  @Override
  public int execAndReturnStatus(String command) throws Exception {
    Process process = getMinaSession().exec(command, 0);
    InputStream err = process.getErrorStream();

    Scanner s = new Scanner(err, UTF_8.name()).useDelimiter("\\A");
    error = s.hasNext() ? s.next() : null;

    try {
      return process.exitValue();
    } catch (IllegalThreadStateException e) {
      // SSH command was interrupted
      return -1;
    }
  }

  @Override
  public Reader execAndReturnReader(String command) throws Exception {
    return new InputStreamReader(
        getMinaSession().exec(command, 0).getInputStream(), StandardCharsets.UTF_8);
  }

  private SshdSession getMinaSession() throws Exception {
    if (session == null) {
      String username = getUsername();

      URIish uri =
          new URIish(
              "ssh://"
                  + username
                  + "@"
                  + addr.getAddress().getHostAddress()
                  + ":"
                  + addr.getPort());

      // TODO(davido): Switch to memory only key resolving mode.
      File userhome = Files.createTempDir();

      FS fs = FS.DETECTED.setUserHome(userhome);
      File sshDir = new File(userhome, ".ssh");
      sshDir.mkdir();
      OpenSSHKeyPairResourceWriter keyPairWriter = new OpenSSHKeyPairResourceWriter();
      try (OutputStream out = new FileOutputStream(new File(sshDir, "id_ecdsa"))) {
        keyPairWriter.writePrivateKey(sshKeys.getKeyPair(account), null, null, out);
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
}
