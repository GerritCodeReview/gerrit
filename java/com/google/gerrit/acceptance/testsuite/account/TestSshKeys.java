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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.config.SshClientImplementation.getFromEnvironment;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.gerrit.acceptance.SshEnabled;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.config.SshClientImplementation;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import org.apache.sshd.common.cipher.ECCurves;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.util.io.pem.PemObject;

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
  public KeyPair getKeyPair(com.google.gerrit.acceptance.TestAccount account) throws Exception {
    checkState(sshEnabled, "Requested SSH key pair, but SSH is disabled");
    checkState(
        account.username() != null,
        "Requested SSH key pair for account %s, but username is not set",
        account.id());

    String username = account.username();
    KeyPair keyPair = sshKeyPairs.get(username);
    if (keyPair == null) {
      keyPair = createKeyPair(account.id(), username, account.email());
      sshKeyPairs.put(username, keyPair);
    }
    return keyPair;
  }

  public KeyPair getKeyPair(TestAccount account) throws Exception {
    checkState(sshEnabled, "Requested SSH key pair, but SSH is disabled");
    checkState(
        account.username().isPresent(),
        "Requested SSH key pair for account %s, but username is not set",
        account.accountId());

    String username = account.username().get();
    KeyPair keyPair = sshKeyPairs.get(username);
    if (keyPair == null) {
      keyPair = createKeyPair(account.accountId(), username, account.preferredEmail().orElse(null));
      sshKeyPairs.put(username, keyPair);
    }
    return keyPair;
  }

  private KeyPair createKeyPair(Account.Id accountId, String username, @Nullable String email)
      throws Exception {
    KeyPair keyPair = genSshKey();
    authorizedKeys.addKey(accountId, publicKey(keyPair, email));
    sshKeyCache.evict(username);
    return keyPair;
  }

  public static KeyPair genSshKey() throws GeneralSecurityException {
    SshClientImplementation client = getFromEnvironment();
    KeyPairGenerator gen;
    if (client == SshClientImplementation.APACHE) {
      int size = 256;
      gen = SecurityUtils.getKeyPairGenerator(KeyUtils.EC_ALGORITHM);
      ECCurves curve = ECCurves.fromCurveSize(size);
      if (curve == null) {
        throw new InvalidKeySpecException("Unknown curve for key size=" + size);
      }
      gen.initialize(curve.getParameters());
    } else {
      gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(1024, new SecureRandom());
    }

    return gen.generateKeyPair();
  }

  public static String publicKey(KeyPair sshKey, @Nullable String comment)
      throws IOException, GeneralSecurityException {
    return preparePublicKey(sshKey, comment).toString(US_ASCII.name()).trim();
  }

  public static byte[] publicKeyBlob(KeyPair sshKey) throws IOException, GeneralSecurityException {
    return preparePublicKey(sshKey, null).toByteArray();
  }

  private static ByteArrayOutputStream preparePublicKey(KeyPair sshKey, String comment)
      throws IOException, GeneralSecurityException {
    OpenSSHKeyPairResourceWriter keyPairWriter = new OpenSSHKeyPairResourceWriter();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    keyPairWriter.writePublicKey(sshKey, comment, out);
    return out;
  }

  public static byte[] privateKey(KeyPair keyPair) throws IOException, GeneralSecurityException {
    SshClientImplementation client = getFromEnvironment();
    if (client == SshClientImplementation.APACHE) {
      OpenSSHKeyPairResourceWriter keyPairWriter = new OpenSSHKeyPairResourceWriter();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      keyPairWriter.writePrivateKey(keyPair, null, null, out);
      return out.toByteArray();
    }
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
