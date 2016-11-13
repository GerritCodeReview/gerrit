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

package com.google.gerrit.gpg;

import static com.google.gerrit.gpg.PublicKeyStore.keyIdToString;
import static com.google.gerrit.gpg.PublicKeyStore.keyToString;
import static com.google.gerrit.gpg.testutil.TestKeys.expiredKey;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithExpiration;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithoutExpiration;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.gpg.testutil.TestKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.eclipse.jgit.transport.PushCertificateParser;
import org.eclipse.jgit.transport.SignedPushConfig;
import org.junit.Before;
import org.junit.Test;

public class PushCertificateCheckerTest {
  private InMemoryRepository repo;
  private PublicKeyStore store;
  private SignedPushConfig signedPushConfig;
  private PushCertificateChecker checker;

  @Before
  public void setUp() throws Exception {
    TestKey key1 = validKeyWithoutExpiration();
    TestKey key3 = expiredKey();
    repo = new InMemoryRepository(new DfsRepositoryDescription("repo"));
    store = new PublicKeyStore(repo);
    store.add(key1.getPublicKeyRing());
    store.add(key3.getPublicKeyRing());

    PersonIdent ident = new PersonIdent("A U Thor", "author@example.com");
    CommitBuilder cb = new CommitBuilder();
    cb.setAuthor(ident);
    cb.setCommitter(ident);
    assertEquals(RefUpdate.Result.NEW, store.save(cb));

    signedPushConfig = new SignedPushConfig();
    signedPushConfig.setCertNonceSeed("sekret");
    signedPushConfig.setCertNonceSlopLimit(60 * 24);
    checker = newChecker(true);
  }

  private PushCertificateChecker newChecker(boolean checkNonce) {
    PublicKeyChecker keyChecker = new PublicKeyChecker().setStore(store);
    return new PushCertificateChecker(keyChecker) {
      @Override
      protected Repository getRepository() {
        return repo;
      }

      @Override
      protected boolean shouldClose(Repository repo) {
        return false;
      }
    }.setCheckNonce(checkNonce);
  }

  @Test
  public void validCert() throws Exception {
    PushCertificate cert = newSignedCert(validNonce(), validKeyWithoutExpiration());
    assertNoProblems(cert);
  }

  @Test
  public void invalidNonce() throws Exception {
    PushCertificate cert = newSignedCert("invalid-nonce", validKeyWithoutExpiration());
    assertProblems(cert, "Invalid nonce");
  }

  @Test
  public void invalidNonceNotChecked() throws Exception {
    checker = newChecker(false);
    PushCertificate cert = newSignedCert("invalid-nonce", validKeyWithoutExpiration());
    assertNoProblems(cert);
  }

  @Test
  public void missingKey() throws Exception {
    TestKey key2 = validKeyWithExpiration();
    PushCertificate cert = newSignedCert(validNonce(), key2);
    assertProblems(cert, "No public keys found for key ID " + keyIdToString(key2.getKeyId()));
  }

  @Test
  public void invalidKey() throws Exception {
    TestKey key3 = expiredKey();
    PushCertificate cert = newSignedCert(validNonce(), key3);
    assertProblems(
        cert, "Invalid public key " + keyToString(key3.getPublicKey()) + ":\n  Key is expired");
  }

  @Test
  public void signatureByExpiredKeyBeforeExpiration() throws Exception {
    TestKey key3 = expiredKey();
    Date now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse("2005-07-10 12:00:00 -0400");
    PushCertificate cert = newSignedCert(validNonce(), key3, now);
    assertNoProblems(cert);
  }

  private String validNonce() {
    return signedPushConfig
        .getNonceGenerator()
        .createNonce(repo, System.currentTimeMillis() / 1000);
  }

  private PushCertificate newSignedCert(String nonce, TestKey signingKey) throws Exception {
    return newSignedCert(nonce, signingKey, null);
  }

  private PushCertificate newSignedCert(String nonce, TestKey signingKey, Date now)
      throws Exception {
    PushCertificateIdent ident =
        new PushCertificateIdent(signingKey.getFirstUserId(), System.currentTimeMillis(), -7 * 60);
    String payload =
        "certificate version 0.1\n"
            + "pusher "
            + ident.getRaw()
            + "\n"
            + "pushee test://localhost/repo.git\n"
            + "nonce "
            + nonce
            + "\n"
            + "\n"
            + "0000000000000000000000000000000000000000"
            + " deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
            + " refs/heads/master\n";
    PGPSignatureGenerator gen =
        new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(signingKey.getPublicKey().getAlgorithm(), PGPUtil.SHA1));

    if (now != null) {
      PGPSignatureSubpacketGenerator subGen = new PGPSignatureSubpacketGenerator();
      subGen.setSignatureCreationTime(false, now);
      gen.setHashedSubpackets(subGen.generate());
    }

    gen.init(PGPSignature.BINARY_DOCUMENT, signingKey.getPrivateKey());
    gen.update(payload.getBytes(UTF_8));
    PGPSignature sig = gen.generate();

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    try (BCPGOutputStream out = new BCPGOutputStream(new ArmoredOutputStream(bout))) {
      sig.encode(out);
    }

    String cert = payload + new String(bout.toByteArray(), UTF_8);
    Reader reader = new InputStreamReader(new ByteArrayInputStream(cert.getBytes(UTF_8)));
    PushCertificateParser parser = new PushCertificateParser(repo, signedPushConfig);
    return parser.parse(reader);
  }

  private void assertProblems(PushCertificate cert, String first, String... rest) throws Exception {
    List<String> expected = new ArrayList<>();
    expected.add(first);
    expected.addAll(Arrays.asList(rest));
    CheckResult result = checker.check(cert).getCheckResult();
    assertEquals(expected, result.getProblems());
  }

  private void assertNoProblems(PushCertificate cert) {
    CheckResult result = checker.check(cert).getCheckResult();
    assertEquals(Collections.emptyList(), result.getProblems());
  }
}
