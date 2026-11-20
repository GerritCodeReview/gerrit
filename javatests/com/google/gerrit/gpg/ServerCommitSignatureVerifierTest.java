// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Status.UNVERIFIED;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Status.VERIFIED;
import static com.google.gerrit.gpg.testing.TestCommitSignatures.secretKey;
import static com.google.gerrit.gpg.testing.TestCommitSignatures.sign;
import static com.google.gerrit.gpg.testing.TestCommitSignatures.signingSubkey;
import static com.google.gerrit.gpg.testing.TestKeys.expiredKey;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithExpiration;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithoutExpiration;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.extensions.common.CommitSignatureInfo;
import com.google.gerrit.gpg.testing.TestKey;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.git.CommitSignatureVerifier;
import com.google.gerrit.server.git.ServerCommitSigner;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ServerCommitSignatureVerifierTest {
  private static final Instant SIGNATURE_TIME = Instant.parse("2020-01-01T00:00:00Z");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private LifecycleManager lifecycle;
  private Injector injector;

  @After
  public void tearDown() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
  }

  @Test
  public void configuredPublicKeyAttestsWithoutAMatchingAccountOrCommitter() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var verifier = initialize(configure(key));
    var info = verifier.verify(sign(payload(), key, SIGNATURE_TIME));
    assertThat(info.status).isEqualTo(VERIFIED);
    assertThat(info.signer).isNull();
    assertThat(info.signerType).isEqualTo(CommitSignatureInfo.SignerType.SERVER);
    assertThat(info.primaryKeyFingerprint).isEqualTo("04AEA7ED2F821133E5B128D1ED0625DC46328A8C");
    assertThat(info.signingKeyFingerprint).isEqualTo(info.primaryKeyFingerprint);
    assertThat(info.reason).isNull();
  }

  @Test
  public void attestationStillRequiresTheExactPayload() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var verifier = initialize(configure(key));
    byte[] signed = sign(payload(), key, SIGNATURE_TIME);
    signed[signed.length - 2] ^= 1;
    var info = verifier.verify(signed);
    assertThat(info.status).isEqualTo(UNVERIFIED);
    assertThat(info.reason).isEqualTo(CommitSignatureInfo.Reason.INVALID_SIGNATURE);
  }

  @Test
  public void configuredSigningSubkeyRetainsItsPrimaryAttestationIdentity() throws Exception {
    TestKey key = signingSubkey();
    var verifier = initialize(configure(key));
    var publicKeys = key.getPublicKeyRing().getPublicKeys();
    publicKeys.next();
    long subkeyId = publicKeys.next().getKeyID();
    var info = verifier.verify(sign(payload(), secretKey(key, subkeyId), SIGNATURE_TIME));
    assertThat(info.status).isEqualTo(VERIFIED);
    assertThat(info.signer).isNull();
    assertThat(info.primaryKeyFingerprint).isEqualTo("04AEA7ED2F821133E5B128D1ED0625DC46328A8C");
    assertThat(info.signingKeyFingerprint).isEqualTo("C378369ACBCD34CC138D90B145311A6F378A0AED");
  }

  @Test
  public void publicKeyFileMustAgreeWithTheConfiguredPrimaryFingerprint() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    Config cfg = configure(key);
    String subsection = "server-key " + fingerprint(key);
    String path = cfg.getString("gpg", subsection, "publicKeyFile");
    cfg.unsetSection("gpg", subsection);
    cfg.setString(
        "gpg", "server-key C378369ACBCD34CC138D90B145311A6F378A0AED", "publicKeyFile", path);
    assertThrows(RuntimeException.class, () -> initialize(cfg));
  }

  @Test
  public void publicKeyFileCannotHideASecondArmorBlock() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    Config cfg = configure(key);
    Path file = Path.of(cfg.getString("gpg", "server-key " + fingerprint(key), "publicKeyFile"));
    Files.writeString(
        file, key.getPublicKeyArmored() + validKeyWithExpiration().getPublicKeyArmored(), UTF_8);
    assertThrows(RuntimeException.class, () -> initialize(cfg));
  }

  @Test
  public void binaryPublicKeyFileCannotHideARepeatedRing() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    Config cfg = configure(key);
    var encoded = new ByteArrayOutputStream();
    key.getPublicKeyRing().encode(encoded);
    key.getPublicKeyRing().encode(encoded);
    Path file = Path.of(cfg.getString("gpg", "server-key " + fingerprint(key), "publicKeyFile"));
    Files.write(file, encoded.toByteArray());
    assertThrows(RuntimeException.class, () -> initialize(cfg));
  }

  @Test
  public void activeKeyWithoutSecretMaterialCannotStart() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    Config cfg = configure(key);
    cfg.setString("gpg", null, "serverSigningKey", fingerprint(key));
    assertThrows(RuntimeException.class, () -> initialize(cfg));
  }

  @Test
  public void activeSigningCannotSilentlyStartWithoutVerification() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    Config cfg = configure(key);
    cfg.setBoolean("gpg", null, "enableCommitSignatureVerification", false);
    cfg.setString("gpg", null, "serverSigningKey", fingerprint(key));
    assertThrows(RuntimeException.class, () -> initialize(cfg));
  }

  @Test
  public void secretOptionsInOrdinaryConfigurationAreRejected() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    Config cfg = configure(key);
    cfg.setString("gpg", "server-key " + fingerprint(key), "passphrase", "must-not-be-used");
    assertThrows(RuntimeException.class, () -> initialize(cfg));
  }

  @Test
  public void inheritedOrdinaryPublicKeyConfigurationIsRecognized() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var verifier = initialize(new Config(configure(key)));
    assertThat(verifier.verify(sign(payload(), key, SIGNATURE_TIME)).status).isEqualTo(VERIFIED);
  }

  @Test
  public void secretOptionsInAnOrdinaryBaseConfigurationAreRejected() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    Config base = configure(key);
    base.setString("gpg", "server-key " + fingerprint(key), "passphrase", "must-not-be-used");
    assertThrows(RuntimeException.class, () -> initialize(new Config(base)));
  }

  @Test
  public void retiredPublicOnlyKeyVerifiesHistoricalButNotExpiredSignatures() throws Exception {
    TestKey key = expiredKey();
    var verifier = initialize(configure(key));
    assertThat(verifier.verify(sign(payload(), key, Instant.parse("2005-07-10T16:00:00Z"))).status)
        .isEqualTo(VERIFIED);
    var expired = verifier.verify(sign(payload(), key, SIGNATURE_TIME));
    assertThat(expired.status).isEqualTo(UNVERIFIED);
    assertThat(expired.reason).isEqualTo(CommitSignatureInfo.Reason.INVALID_KEY);
  }

  @Test
  public void rotateFromAToBRetainingOnlyAPublicKeyThenRemoveATrust() throws Exception {
    TestKey a = validKeyWithoutExpiration();
    TestKey b = validKeyWithExpiration();
    Config cfgA = configure(a);
    cfgA.setString("gpg", null, "serverSigningKey", fingerprint(a));
    Config secureA = secretConfiguration(a);
    var initial = initialize(cfgA, secureA);
    byte[] signedA = injector.getInstance(ServerCommitSigner.class).signIfUnsigned(payload());
    assertThat(initial.verify(signedA).status).isEqualTo(VERIFIED);

    Config cfgB = configure(b);
    cfgB.setString("gpg", null, "serverSigningKey", fingerprint(b));
    cfgB.setString(
        "gpg",
        "server-key " + fingerprint(a),
        "publicKeyFile",
        cfgA.getString("gpg", "server-key " + fingerprint(a), "publicKeyFile"));
    Config secureB = secretConfiguration(b);
    Files.delete(
        Path.of(secureA.getString("gpg", "server-key " + fingerprint(a), "secretKeyFile")));
    var rotated = initialize(cfgB, secureB);
    byte[] signedB = injector.getInstance(ServerCommitSigner.class).signIfUnsigned(payload());
    var resultA = rotated.verify(signedA);
    var resultB = rotated.verify(signedB);
    assertThat(resultA.status).isEqualTo(VERIFIED);
    assertThat(resultA.signerType).isEqualTo(CommitSignatureInfo.SignerType.SERVER);
    assertThat(resultA.primaryKeyFingerprint).isEqualTo("04AEA7ED2F821133E5B128D1ED0625DC46328A8C");
    assertThat(resultB.status).isEqualTo(VERIFIED);
    assertThat(resultB.primaryKeyFingerprint).isEqualTo("C378369ACBCD34CC138D90B145311A6F378A0AED");
    assertThat(resultA.signer).isNull();
    assertThat(resultB.signer).isNull();
    cfgB.unsetSection("gpg", "server-key " + fingerprint(a));
    // Configuration/key files are restart-scoped, not silently hot-reloaded during a request.
    assertThat(rotated.verify(signedA).status).isEqualTo(VERIFIED);
    var removed = initialize(cfgB, secureB);
    assertThat(removed.verify(signedA).reason).isEqualTo(CommitSignatureInfo.Reason.UNKNOWN_KEY);
    assertThat(removed.verify(signedB).status).isEqualTo(VERIFIED);
  }

  @Test
  public void generatedSignatureUsesTheOnlyEligibleSigningSubkey() throws Exception {
    TestKey key = signingSubkey();
    Config cfg = configure(key);
    cfg.setString("gpg", null, "serverSigningKey", fingerprint(key));
    var verifier = initialize(cfg, secretConfiguration(key));
    byte[] signed = injector.getInstance(ServerCommitSigner.class).signIfUnsigned(payload());
    var result = verifier.verify(signed);
    assertThat(result.status).isEqualTo(VERIFIED);
    assertThat(result.signingKeyFingerprint).isEqualTo("C378369ACBCD34CC138D90B145311A6F378A0AED");
    assertThat(result.primaryKeyFingerprint).isEqualTo("04AEA7ED2F821133E5B128D1ED0625DC46328A8C");
  }

  @Test
  public void configuredKeyPolicyChecksAreSafeAcrossConcurrentRequests() throws Exception {
    TestKey key = signingSubkey();
    var verifier = initialize(configure(key));
    var keys = key.getPublicKeyRing().getPublicKeys();
    keys.next();
    byte[] signed = sign(payload(), secretKey(key, keys.next().getKeyID()), SIGNATURE_TIME);
    try (var executor = Executors.newFixedThreadPool(8)) {
      var start = new CountDownLatch(1);
      var results = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 8; i++) {
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  for (int n = 0; n < 25; n++) {
                    if (verifier.verify(signed).status != VERIFIED) {
                      return false;
                    }
                  }
                  return true;
                }));
      }
      start.countDown();
      for (var result : results) {
        assertThat(result.get()).isTrue();
      }
    }
  }

  private Config secretConfiguration(TestKey key) throws Exception {
    var file = temporaryFolder.newFile().toPath();
    Files.writeString(file, key.getSecretKeyArmored(), UTF_8);
    Config secure = new Config();
    secure.setString("gpg", "server-key " + fingerprint(key), "secretKeyFile", file.toString());
    return secure;
  }

  private Config configure(TestKey key) throws Exception {
    Config cfg = InMemoryModule.newDefaultConfig();
    cfg.setBoolean("gpg", null, "enableCommitSignatureVerification", true);
    var file = temporaryFolder.newFile().toPath();
    Files.writeString(file, key.getPublicKeyArmored(), UTF_8);
    cfg.setString("gpg", "server-key " + fingerprint(key), "publicKeyFile", file.toString());
    return cfg;
  }

  private CommitSignatureVerifier initialize(Config cfg) throws Exception {
    return initialize(cfg, new Config());
  }

  private CommitSignatureVerifier initialize(Config cfg, Config secure) throws Exception {
    if (lifecycle != null) {
      lifecycle.stop();
      lifecycle = null;
    }
    Path site = temporaryFolder.newFolder().toPath();
    Files.createDirectories(site.resolve("etc"));
    Files.writeString(site.resolve("etc/secure.config"), secure.toText(), UTF_8);
    injector =
        Guice.createInjector(
            Modules.override(new InMemoryModule(cfg))
                .with(
                    new AbstractModule() {
                      @Override
                      protected void configure() {
                        bind(Path.class).annotatedWith(SitePath.class).toInstance(site);
                      }
                    }));
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();
    injector.getInstance(SchemaCreator.class).create();
    return injector.getInstance(CommitSignatureVerifier.class);
  }

  private static String fingerprint(TestKey key) {
    return BaseEncoding.base16().encode(key.getPublicKey().getFingerprint());
  }

  private static byte[] payload() {
    return ("tree 4b825dc642cb6eb9a060e54bf8d69288fbee4904\n"
            + "author Original Author <author@example.com> 1577836800 -0300\n"
            + "committer Another User <unregistered@example.com> 1577836800 +0200\n"
            + "encoding ISO-8859-1\n\n"
            + "Server attestation preserves metadata and message  \n")
        .getBytes(UTF_8);
  }
}
