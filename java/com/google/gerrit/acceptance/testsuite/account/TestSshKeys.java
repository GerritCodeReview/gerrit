// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.account;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.gerrit.acceptance.SshEnabled;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Singleton
public class TestSshKeys {
  private final Map<String, KeyPair> sshKeyPairs;

  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final SshKeyCache sshKeyCache;
  private final boolean sshEnabled;

  @Inject
  TestSshKeys(
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      SshKeyCache sshKeyCache,
      @SshEnabled boolean sshEnabled) {
    this.authorizedKeys = authorizedKeys;
    this.sshKeyCache = sshKeyCache;
    this.sshEnabled = sshEnabled;
    this.sshKeyPairs = new HashMap<>();
  }

  // TODO(ekempin): Remove this method when com.google.gerrit.acceptance.TestAccount is gone
  public Optional<KeyPair> getKeyPair(com.google.gerrit.acceptance.TestAccount account)
      throws Exception {
    if (!sshEnabled || account.username == null) {
      return Optional.empty();
    }

    String username = account.username;
    KeyPair keyPair = sshKeyPairs.get(username);
    if (keyPair == null) {
      keyPair = genSshKey();
      authorizedKeys.addKey(account.id, publicKey(keyPair, account.email));
      sshKeyCache.evict(username);
      sshKeyPairs.put(username, keyPair);
    }
    return Optional.of(keyPair);
  }

  public Optional<KeyPair> getKeyPair(TestAccount account) throws Exception {
    if (!sshEnabled || !account.username().isPresent()) {
      return Optional.empty();
    }

    String username = account.username().get();
    KeyPair keyPair = sshKeyPairs.get(username);
    if (keyPair == null) {
      keyPair = genSshKey();
      authorizedKeys.addKey(
          account.accountId(), publicKey(keyPair, account.preferredEmail().orElse(null)));
      sshKeyCache.evict(username);
      sshKeyPairs.put(username, keyPair);
    }
    return Optional.of(keyPair);
  }

  public static KeyPair genSshKey() throws JSchException {
    JSch jsch = new JSch();
    return KeyPair.genKeyPair(jsch, KeyPair.RSA);
  }

  public static String publicKey(KeyPair sshKey, @Nullable String comment)
      throws UnsupportedEncodingException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sshKey.writePublicKey(out, comment);
    return out.toString(US_ASCII.name()).trim();
  }

  public static byte[] privateKey(KeyPair keyPair) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    keyPair.writePrivateKey(out);
    return out.toByteArray();
  }
}
