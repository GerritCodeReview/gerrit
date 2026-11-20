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
import static com.google.gerrit.gpg.testing.TestCommitSignatures.signingSubkey;
import static com.google.gerrit.gpg.testing.TestKeys.expiredKey;
import static com.google.gerrit.gpg.testing.TestKeys.selfRevokedKey;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithExpiration;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithoutExpiration;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.gerrit.gpg.testing.TestKey;
import com.google.gerrit.server.config.GerritServerConfigProvider;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.securestore.DefaultSecureStore;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ServerSigningKeysTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void secretPrimaryFingerprintMustMatchThePublicKey() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture = configure(key, validKeyWithExpiration().getSecretKeyArmored());
    assertThrows(ProvisionException.class, fixture::load);
  }

  @Test
  public void malformedSecretKeyFileCannotBeAnActiveSigner() throws Exception {
    var fixture = configure(validKeyWithoutExpiration(), "not a secret key ring");
    assertThrows(ProvisionException.class, fixture::load);
  }

  @Test
  public void secretKeyFileCannotHideASecondArmorBlock() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture =
        configure(key, key.getSecretKeyArmored() + validKeyWithExpiration().getSecretKeyArmored());
    assertThrows(ProvisionException.class, fixture::load);
  }

  @Test
  public void binarySecretKeyFileCannotHideARepeatedRing() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture = configure(key, key.getSecretKeyArmored());
    var encoded = new ByteArrayOutputStream();
    try (var in =
        PGPUtil.getDecoderStream(
            new ByteArrayInputStream(key.getSecretKeyArmored().getBytes(US_ASCII)))) {
      var ring = new PGPSecretKeyRing(in, new BcKeyFingerprintCalculator());
      ring.encode(encoded);
      ring.encode(encoded);
    }
    Files.write(fixture.site.site_path.resolve("secret.asc"), encoded.toByteArray());
    assertThrows(ProvisionException.class, fixture::load);
  }

  @Test
  public void includedOrdinarySecretKeyFileIsRejectedEvenWithASecureOverride() throws Exception {
    assertOrdinarySecretOptionRejected("secretKeyFile", "ordinary-secret.asc", "secret.asc");
  }

  @Test
  public void includedOrdinaryPassphraseIsRejectedEvenWithASecureOverride() throws Exception {
    assertOrdinarySecretOptionRejected("passphrase", "ordinary-passphrase", "");
  }

  @Test
  public void includedAdditionalActiveKeyIsRejectedEvenWithASecureOverride() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture = configure(key, key.getSecretKeyArmored());
    String fingerprint = ServerSigningKeys.fingerprint(key.getPublicKey());
    fixture.secure.setString("gpg", null, "serverSigningKey", fingerprint);
    fixture.cfg.setStringList("gpg", null, "serverSigningKey", List.of(fingerprint, fingerprint));
    assertThrows(ProvisionException.class, fixture::loadServerConfig);

    fixture.cfg.setString("gpg", null, "serverSigningKey", fingerprint);
    Config included = new Config();
    included.setString("gpg", null, "serverSigningKey", fingerprint);
    fixture.include(included);
    assertThrows(ProvisionException.class, fixture::loadServerConfig);
  }

  @Test
  public void includedParentActiveKeyCanBeOverriddenByAnotherLayersScalar() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture = configure(key, key.getSecretKeyArmored());
    Config included = new Config();
    included.setString("gpg", null, "serverSigningKey", "C378369ACBCD34CC138D90B145311A6F378A0AED");
    Files.writeString(fixture.site.etc_dir.resolve("parent.inc"), included.toText(), US_ASCII);
    Config parentConfig = new Config();
    parentConfig.setString("include", null, "path", "parent.inc");
    Path parentFile = fixture.site.etc_dir.resolve("parent.config");
    Files.writeString(parentFile, parentConfig.toText(), US_ASCII);
    var parent = new FileBasedConfig(parentFile.toFile(), FS.DETECTED);
    parent.load();
    Config override = new Config(parent);
    override.fromText(fixture.cfg.toText());
    assertSignsPayload(new Fixture(fixture.site, override, fixture.secure).load(), key);
  }

  @Test
  public void expiredActiveKeyCannotSignNewContent() throws Exception {
    TestKey key = expiredKey();
    var fixture = configure(key, key.getSecretKeyArmored());
    assertThrows(ProvisionException.class, fixture::load);
  }

  @Test
  public void revokedActiveKeyCannotSignNewContent() throws Exception {
    TestKey key = selfRevokedKey();
    var fixture = configure(key, key.getSecretKeyArmored());
    assertThrows(ProvisionException.class, fixture::load);
  }

  @Test
  public void moreThanOneActiveKeyOptionIsRejected() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture = configure(key, key.getSecretKeyArmored());
    String fingerprint = ServerSigningKeys.fingerprint(key.getPublicKey());
    fixture.cfg.setStringList("gpg", null, "serverSigningKey", List.of(fingerprint, fingerprint));
    assertThrows(ProvisionException.class, fixture::load);
  }

  @Test
  public void scalarActiveOverrideDoesNotCombineTheBaseAndOverrideValues() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture = configure(key, key.getSecretKeyArmored());
    Config override = new Config(fixture.cfg);
    override.setString(
        "gpg", null, "serverSigningKey", ServerSigningKeys.fingerprint(key.getPublicKey()));
    var overridden = new Fixture(fixture.site, override, fixture.secure);
    assertSignsPayload(overridden.load(), key);
  }

  @Test
  public void activeKeyWithoutSigningCapabilityIsRejected() throws Exception {
    TestKey key = signingSubkey(KeyFlags.CERTIFY_OTHER, KeyFlags.ENCRYPT_COMMS);
    var fixture = configure(key, key.getSecretKeyArmored());
    assertThrows(ProvisionException.class, fixture::load);
  }

  @Test
  public void multipleEligibleSecretSigningKeysAreAmbiguous() throws Exception {
    TestKey key = signingSubkey(KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA, KeyFlags.SIGN_DATA);
    var fixture = configure(key, key.getSecretKeyArmored());
    assertThrows(ProvisionException.class, fixture::load);
  }

  @Test
  public void wrongSecurePassphraseCannotUnlockTheActiveKey() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture = configure(key, encryptedSecret(key, "correct-passphrase"));
    fixture.secure.setString(
        "gpg",
        "server-key " + ServerSigningKeys.fingerprint(key.getPublicKey()),
        "passphrase",
        "wrong-passphrase");
    assertThrows(ProvisionException.class, fixture::load);
  }

  @Test
  public void securePassphraseUnlocksTheActiveKey() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture = configure(key, encryptedSecret(key, "correct-passphrase"));
    fixture.secure.setString(
        "gpg",
        "server-key " + ServerSigningKeys.fingerprint(key.getPublicKey()),
        "passphrase",
        "correct-passphrase");
    assertSignsPayload(fixture.load(), key);
  }

  @Test
  public void binaryPublicAndSecretKeyFilesCanSignContent() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture = configure(key, key.getSecretKeyArmored());
    Files.write(fixture.site.site_path.resolve("public.asc"), key.getPublicKeyRing().getEncoded());
    try (var in =
        PGPUtil.getDecoderStream(
            new ByteArrayInputStream(key.getSecretKeyArmored().getBytes(US_ASCII)))) {
      Files.write(
          fixture.site.site_path.resolve("secret.asc"),
          new PGPSecretKeyRing(in, new BcKeyFingerprintCalculator()).getEncoded());
    }
    assertSignsPayload(fixture.load(), key);
  }

  @Test
  @SuppressWarnings("JdkObsolete")
  public void aNewlyEligibleSecondSigningKeyFailsClosedUntilRestart() throws Exception {
    TestKey key = signingSubkey();
    Instant future = Instant.now().plusSeconds(2 * 24 * 60 * 60);
    var generator =
        new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(
                key.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
    generator.init(PGPSignature.POSITIVE_CERTIFICATION, key.getPrivateKey());
    var packets = new PGPSignatureSubpacketGenerator();
    packets.setSignatureCreationTime(false, Date.from(future));
    packets.setKeyFlags(false, KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA);
    generator.setHashedSubpackets(packets.generate());
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            key.getPublicKey(),
            key.getFirstUserId(),
            generator.generateCertification(key.getFirstUserId(), key.getPublicKey()));
    var publicBytes = new ByteArrayOutputStream();
    try (var out = new ArmoredOutputStream(publicBytes)) {
      PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary).encode(out);
    }
    TestKey futureKey = new TestKey(publicBytes.toString(US_ASCII), key.getSecretKeyArmored());
    var signer =
        new GerritServerCommitSigner(configure(futureKey, futureKey.getSecretKeyArmored()).load());
    try {
      TimeUtil.setCurrentMillisSupplier(() -> future.plusSeconds(1).toEpochMilli());
      assertThrows(IOException.class, () -> signer.signIfUnsigned(unsignedPayload()));
    } finally {
      TimeUtil.resetCurrentMillisSupplier();
    }
  }

  private static byte[] unsignedPayload() throws IOException {
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(Constants.EMPTY_TREE_ID);
    builder.setAuthor(new PersonIdent("Author", "author@example.com"));
    builder.setCommitter(new PersonIdent("Committer", "committer@example.com"));
    builder.setMessage("generated content\n");
    return builder.build();
  }

  private void assertOrdinarySecretOptionRejected(
      String name, String ordinaryValue, String secureValue) throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var fixture = configure(key, key.getSecretKeyArmored());
    String subsection = "server-key " + ServerSigningKeys.fingerprint(key.getPublicKey());
    fixture.secure.setString("gpg", subsection, name, secureValue);
    fixture.cfg.setString("gpg", subsection, name, ordinaryValue);
    assertThrows(ProvisionException.class, fixture::loadServerConfig);

    fixture.cfg.unset("gpg", subsection, name);
    Config included = new Config();
    included.setString("gpg", subsection, name, ordinaryValue);
    fixture.include(included);
    assertThrows(ProvisionException.class, fixture::loadServerConfig);
  }

  private static void assertSignsPayload(ServerSigningKeys keys, TestKey expected)
      throws Exception {
    byte[] payload = unsignedPayload();
    byte[] signed = new GerritServerCommitSigner(keys).signIfUnsigned(payload);
    byte[] armor = RevCommit.parse(signed).getRawGpgSignature();
    assertThat(armor).isNotNull();
    try (var in = PGPUtil.getDecoderStream(new ByteArrayInputStream(armor))) {
      var signatures = (PGPSignatureList) new BcPGPObjectFactory(in).nextObject();
      PGPSignature signature = signatures.get(0);
      signature.init(
          new BcPGPContentVerifierBuilderProvider(),
          expected.getPublicKeyRing().getPublicKey(signature.getKeyID()));
      signature.update(payload);
      assertThat(signature.verify()).isTrue();
    }
  }

  private Fixture configure(TestKey key, String secret) throws Exception {
    Path site = temporaryFolder.newFolder().toPath();
    Files.createDirectories(site.resolve("etc"));
    Files.writeString(site.resolve("public.asc"), key.getPublicKeyArmored(), US_ASCII);
    Files.writeString(site.resolve("secret.asc"), secret, US_ASCII);
    String fingerprint = ServerSigningKeys.fingerprint(key.getPublicKey());
    Config cfg = new Config();
    cfg.setString("gpg", null, "serverSigningKey", fingerprint);
    cfg.setString("gpg", "server-key " + fingerprint, "publicKeyFile", "public.asc");
    Config secure = new Config();
    secure.setString("gpg", "server-key " + fingerprint, "secretKeyFile", "secret.asc");
    return new Fixture(new SitePaths(site), cfg, secure);
  }

  private record Fixture(SitePaths site, Config cfg, Config secure) {
    ServerSigningKeys load() throws Exception {
      return new ServerSigningKeys(cfg, injector().getInstance(SecureStore.class), site);
    }

    ServerSigningKeys loadServerConfig() throws Exception {
      Files.writeString(site.gerrit_config, cfg.toText(), US_ASCII);
      var injector = injector();
      return new ServerSigningKeys(
          injector.getInstance(GerritServerConfigProvider.class).get(),
          injector.getInstance(SecureStore.class),
          site);
    }

    void include(Config included) throws Exception {
      Files.writeString(site.etc_dir.resolve("ordinary.inc"), included.toText(), US_ASCII);
      cfg.setString("include", null, "path", "ordinary.inc");
    }

    private Injector injector() throws Exception {
      Files.writeString(site.secure_config, secure.toText(), US_ASCII);
      return Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(SitePaths.class).toInstance(site);
              bind(SecureStore.class).to(DefaultSecureStore.class);
            }
          });
    }
  }

  private static String encryptedSecret(TestKey key, String passphrase) throws Exception {
    PGPSecretKeyRing ring;
    try (var in =
        PGPUtil.getDecoderStream(
            new ByteArrayInputStream(key.getSecretKeyArmored().getBytes(US_ASCII)))) {
      ring = new PGPSecretKeyRing(in, new BcKeyFingerprintCalculator());
    }
    var digests = new BcPGPDigestCalculatorProvider();
    ring =
        PGPSecretKeyRing.copyWithNewPassword(
            ring,
            new BcPBESecretKeyDecryptorBuilder(digests).build(new char[0]),
            new BcPBESecretKeyEncryptorBuilder(
                    SymmetricKeyAlgorithmTags.AES_256, digests.get(HashAlgorithmTags.SHA256))
                .build(passphrase.toCharArray()));
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ArmoredOutputStream out = new ArmoredOutputStream(buffer)) {
      ring.encode(out);
    }
    return buffer.toString(US_ASCII);
  }
}
