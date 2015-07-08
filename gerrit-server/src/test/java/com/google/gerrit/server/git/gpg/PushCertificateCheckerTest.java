// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.git.gpg;

import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyIdToString;
import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyToString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.reviewdb.client.RefNames;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.eclipse.jgit.transport.PushCertificateParser;
import org.eclipse.jgit.transport.SignedPushConfig;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;

public class PushCertificateCheckerTest {
  private TestRepository<?> tr;
  private SignedPushConfig signedPushConfig;
  private PushCertificateChecker checker;

  @Before
  public void setUp() throws Exception {
    TestKey key1 = TestKey.key1();
    TestKey key3 = TestKey.key3();
    tr = new TestRepository<>(new InMemoryRepository(
        new DfsRepositoryDescription("repo")));
    tr.branch(RefNames.REFS_GPG_KEYS).commit()
        .add(PublicKeyStore.keyObjectId(key1.getPublicKey().getKeyID()).name(),
            key1.getPublicKeyArmored())
        .add(PublicKeyStore.keyObjectId(key3.getPublicKey().getKeyID()).name(),
            key3.getPublicKeyArmored())
        .create();
    signedPushConfig = new SignedPushConfig();
    signedPushConfig.setCertNonceSeed("sekret");
    signedPushConfig.setCertNonceSlopLimit(60 * 24);

    checker = new PushCertificateChecker(new PublicKeyChecker()) {
      @Override
      protected Repository getRepository() {
        return tr.getRepository();
      }

      @Override
      protected boolean shouldClose(Repository repo) {
        return false;
      }
    };
  }

  @Test
  public void validCert() throws Exception {
    PushCertificate cert = newSignedCert(validNonce(), TestKey.key1());
    assertProblems(cert);
  }

  @Test
  public void invalidNonce() throws Exception {
    PushCertificate cert = newSignedCert("invalid-nonce", TestKey.key1());
    assertProblems(cert, "Invalid nonce");
  }

  @Test
  public void missingKey() throws Exception {
    TestKey key2 = TestKey.key2();
    PushCertificate cert = newSignedCert(validNonce(), key2);
    assertProblems(cert,
        "No public keys found for Key ID " + keyIdToString(key2.getKeyId()));
  }

  @Test
  public void invalidKey() throws Exception {
    TestKey key3 = TestKey.key3();
    PushCertificate cert = newSignedCert(validNonce(), key3);
    assertProblems(cert,
        "Invalid public key (" + keyToString(key3.getPublicKey())
          + "):\n  Key is expired");
  }

  private String validNonce() {
    return signedPushConfig.getNonceGenerator()
        .createNonce(tr.getRepository(), System.currentTimeMillis() / 1000);
  }

  private PushCertificate newSignedCert(String nonce, TestKey signingKey)
      throws Exception {
    PushCertificateIdent ident = new PushCertificateIdent(
        signingKey.getFirstUserId(), System.currentTimeMillis(), -7 * 60);
    String payload = "certificate version 0.1\n"
      + "pusher " + ident.getRaw() + "\n"
      + "pushee test://localhost/repo.git\n"
      + "nonce " + nonce + "\n"
      + "\n"
      + "0000000000000000000000000000000000000000"
      + " deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
      + " refs/heads/master\n";
    PGPSignatureGenerator gen = new PGPSignatureGenerator(
        new BcPGPContentSignerBuilder(
          signingKey.getPublicKey().getAlgorithm(), PGPUtil.SHA1));
    gen.init(PGPSignature.BINARY_DOCUMENT, signingKey.getPrivateKey());
    gen.update(payload.getBytes(UTF_8));
    PGPSignature sig = gen.generate();

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    try (BCPGOutputStream out = new BCPGOutputStream(
        new ArmoredOutputStream(bout))) {
      sig.encode(out);
    }

    String cert = payload + new String(bout.toByteArray(), UTF_8);
    Reader reader =
        new InputStreamReader(new ByteArrayInputStream(cert.getBytes(UTF_8)));
    PushCertificateParser parser =
        new PushCertificateParser(tr.getRepository(), signedPushConfig);
    return parser.parse(reader);
  }

  private void assertProblems(PushCertificate cert, String... expected)
      throws Exception {
    CheckResult result = checker.check(cert);
    assertEquals(Arrays.asList(expected), result.getProblems());
  }
}
