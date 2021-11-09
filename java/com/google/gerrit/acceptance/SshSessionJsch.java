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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.Scanner;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.util.io.pem.PemObject;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;

public class SshSessionJsch extends SshSession {

  private Session session;

  public static void initClient(KeyPair keyPair) {
    Properties config = new Properties();
    config.put("StrictHostKeyChecking", "no");
    JSch.setConfig(config);

    // register a JschConfigSessionFactory that adds the private key as identity
    // to the JSch instance of JGit so that SSH communication via JGit can
    // succeed
    SshSessionFactory.setInstance(
        new JschConfigSessionFactory() {
          @Override
          protected void configure(Host hc, Session session) {
            try {
              JSch jsch = getJSch(hc, FS.DETECTED);
              jsch.addIdentity(
                  "KeyPair", privateKey(keyPair), TestSshKeys.publicKeyBlob(keyPair), null);
            } catch (JSchException | GeneralSecurityException | IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
  }

  public static KeyPairGenerator initKeyPairGenerator() throws NoSuchAlgorithmException {
    KeyPairGenerator gen;
    gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(512, new SecureRandom());
    return gen;
  }

  public SshSessionJsch(TestSshKeys sshKeys, InetSocketAddress addr, TestAccount account) {
    super(sshKeys, addr, account);
  }

  @Override
  public void open() throws Exception {
    getJschSession();
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
    ChannelExec channel = (ChannelExec) getJschSession().openChannel("exec");
    try {
      channel.setCommand(command);
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

  @SuppressWarnings("resource")
  @Override
  public int execAndReturnStatus(String command) throws Exception {
    ChannelExec channel = (ChannelExec) getJschSession().openChannel("exec");
    try {
      channel.setCommand(command);
      InputStream err = channel.getErrStream();
      channel.connect();

      Scanner s = new Scanner(err, UTF_8.name()).useDelimiter("\\A");
      error = s.hasNext() ? s.next() : null;
      return channel.getExitStatus();
    } finally {
      channel.disconnect();
    }
  }

  @Override
  public Reader execAndReturnReader(String command) throws Exception {
    ChannelExec channel = (ChannelExec) getJschSession().openChannel("exec");
    channel.setCommand(command);
    channel.connect();

    return new InputStreamReader(channel.getInputStream(), StandardCharsets.UTF_8) {
      @Override
      public void close() throws IOException {
        super.close();
        channel.disconnect();
      }
    };
  }

  private Session getJschSession() throws Exception {
    if (session == null) {
      KeyPair keyPair = sshKeys.getKeyPair(account);
      JSch jsch = new JSch();
      jsch.addIdentity("KeyPair", privateKey(keyPair), TestSshKeys.publicKeyBlob(keyPair), null);
      String username = getUsername();
      session = jsch.getSession(username, addr.getAddress().getHostAddress(), addr.getPort());
      session.setConfig("StrictHostKeyChecking", "no");
      session.connect();
    }
    return session;
  }

  private static byte[] privateKey(KeyPair keyPair) throws IOException {
    // unencrypted form of PKCS#8 file
    JcaPKCS8Generator gen1 = new JcaPKCS8Generator(keyPair.getPrivate(), null);
    PemObject obj1 = gen1.generate();
    StringWriter sw1 = new StringWriter();
    try (JcaPEMWriter pw = new JcaPEMWriter(sw1)) {
      pw.writeObject(obj1);
    }
    return sw1.toString().getBytes(US_ASCII.name());
  }
}
