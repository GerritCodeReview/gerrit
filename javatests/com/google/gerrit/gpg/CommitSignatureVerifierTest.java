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
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.IDENTITY_MISMATCH;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.INVALID_KEY;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.INVALID_SIGNATURE;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.MALFORMED_SIGNATURE;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.UNKNOWN_KEY;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.UNSUPPORTED_FORMAT;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Status.UNVERIFIED;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Status.VERIFIED;
import static com.google.gerrit.gpg.testing.TestCommitSignatures.addSignature;
import static com.google.gerrit.gpg.testing.TestCommitSignatures.secretKey;
import static com.google.gerrit.gpg.testing.TestCommitSignatures.sign;
import static com.google.gerrit.gpg.testing.TestCommitSignatures.signingSubkey;
import static com.google.gerrit.gpg.testing.TestKeys.expiredKey;
import static com.google.gerrit.gpg.testing.TestKeys.revokedCompromisedKey;
import static com.google.gerrit.gpg.testing.TestKeys.selfRevokedKey;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithoutExpiration;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.CommitSignatureInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.gpg.testing.TestKey;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.git.CommitSignatureVerifier;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.bcpg.sig.RevocationReasonTags;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CommitSignatureVerifierTest {
  private static final Instant SIGNATURE_TIME = Instant.parse("2020-01-01T00:00:00Z");
  private static final Instant HISTORICAL_SIGNATURE_TIME = Instant.parse("2005-07-10T16:00:00Z");

  @Inject private CommitSignatureVerifier verifier;
  @Inject private AccountManager accountManager;
  @Inject private AuthRequest.Factory authRequestFactory;
  @Inject @ServerInitiated private Provider<AccountsUpdate> accountsUpdate;
  @Inject private ExternalIdFactory externalIdFactory;
  @Inject private IdentifiedUser.GenericFactory userFactory;
  @Inject private SchemaCreator schemaCreator;
  @Inject private ThreadLocalRequestContext requestContext;
  @Inject private GitRepositoryManager repoManager;
  @Inject private AllUsersName allUsers;
  @Inject private GerritPublicKeyChecker.Factory checkerFactory;
  @Inject private ExternalIds externalIds;
  @Inject private ExternalIdKeyFactory externalIdKeyFactory;
  @Inject private Provider<PublicKeyStore> storeProvider;
  @Inject private Provider<InternalAccountQuery> accountQueryProvider;
  @Inject private DynamicItem<UrlFormatter> urlFormatter;

  private LifecycleManager lifecycle;
  private Account.Id signerId;

  @Before
  public void setUp() throws Exception {
    Config cfg = InMemoryModule.newDefaultConfig();
    cfg.setBoolean("gpg", null, "enableCommitSignatureVerification", true);
    var injector = Guice.createInjector(new InMemoryModule(cfg));
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    injector.injectMembers(this);
    lifecycle.start();
    schemaCreator.create();
    signerId =
        accountManager.authenticate(authRequestFactory.createForUser("signer")).getAccountId();
    @SuppressWarnings("unused")
    var unused = requestContext.setContext(() -> userFactory.create(signerId));
  }

  @After
  public void tearDown() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
  }

  @Test
  public void signedExactPayloadWithMultilineHeader() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    register(key);
    var info = verifier.verify(sign(payload("test1@example.com"), key, SIGNATURE_TIME));
    assertThat(info.status).isEqualTo(VERIFIED);
    assertThat(info.signer._accountId).isEqualTo(signerId.get());
    assertThat(info.signingKeyFingerprint).isEqualTo("04AEA7ED2F821133E5B128D1ED0625DC46328A8C");
    assertThat(info.primaryKeyFingerprint).isEqualTo(info.signingKeyFingerprint);
    assertThat(info.reason).isNull();
  }

  @Test
  public void tamperedContentIsRejected() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    register(key);
    byte[] signed = sign(payload("test1@example.com"), key, SIGNATURE_TIME);
    signed[signed.length - 2] ^= 1;
    assertUnverified(verifier.verify(signed), INVALID_SIGNATURE);
  }

  @Test
  public void committerMustMatchSigner() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    register(key);
    assertUnverified(
        verifier.verify(sign(payload("other@example.com"), key, SIGNATURE_TIME)),
        IDENTITY_MISMATCH);
  }

  @Test
  public void committerEmailNeedsCertifiedKeyIdentity() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    register(key);
    String email = "registered-but-uncertified@example.com";
    accountsUpdate
        .get()
        .update(
            "Register another email",
            signerId,
            u -> u.addExternalId(externalIdFactory.createEmail(signerId, email)));
    assertUnverified(verifier.verify(sign(payload(email), key, SIGNATURE_TIME)), IDENTITY_MISMATCH);
  }

  @Test
  public void unknownKeyIsUnverified() throws Exception {
    assertUnverified(
        verifier.verify(
            sign(payload("test1@example.com"), validKeyWithoutExpiration(), SIGNATURE_TIME)),
        UNKNOWN_KEY);
  }

  @Test
  public void unsupportedSignatureFormat() throws Exception {
    assertUnverified(
        verifier.verify(
            addSignature(
                payload("test1@example.com"),
                "-----BEGIN SSH SIGNATURE-----\nabc\n-----END SSH SIGNATURE-----")),
        UNSUPPORTED_FORMAT);
  }

  @Test
  public void malformedOpenPgpSignature() throws Exception {
    assertUnverified(
        verifier.verify(
            addSignature(
                payload("test1@example.com"),
                "-----BEGIN PGP SIGNATURE-----\nnot valid\n-----END PGP SIGNATURE-----")),
        MALFORMED_SIGNATURE);
  }

  @Test
  public void alternateSignatureHeaderIsNotUnsigned() throws Exception {
    byte[] signed =
        addSignature(
            payload("test1@example.com"),
            "-----BEGIN SSH SIGNATURE-----\nabc\n-----END SSH SIGNATURE-----");
    byte[] alternate =
        new String(signed, UTF_8).replace("\ngpgsig ", "\ngpgsig-sha256 ").getBytes(UTF_8);
    assertUnverified(verifier.verify(alternate), UNSUPPORTED_FORMAT);
  }

  @Test
  public void multipleArmoredSignaturesAreMalformed() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    register(key);
    String signed = new String(sign(payload("test1@example.com"), key, SIGNATURE_TIME), UTF_8);
    int start = signed.indexOf("\ngpgsig ") + "\ngpgsig ".length();
    String armor = signed.substring(start, signed.indexOf("\n\n", start)).replace("\n ", "\n");
    assertUnverified(
        verifier.verify(addSignature(payload("test1@example.com"), armor + "\n" + armor)),
        MALFORMED_SIGNATURE);
  }

  @Test
  public void revokedCertifiedEmailIsNotSignerIdentity() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    var generator =
        new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(
                key.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
    generator.init(PGPSignature.CERTIFICATION_REVOCATION, key.getPrivateKey());
    PGPSignatureSubpacketGenerator packets = new PGPSignatureSubpacketGenerator();
    packets.setSignatureCreationTime(
        false, java.util.Date.from(Instant.parse("2019-01-01T00:00:00Z")));
    generator.setHashedSubpackets(packets.generate());
    var primary =
        PGPPublicKey.addCertification(
            key.getPublicKey(),
            key.getFirstUserId(),
            generator.generateCertification(key.getFirstUserId(), key.getPublicKey()));
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertUnverified(
        verifier.verify(sign(payload("test1@example.com"), key, SIGNATURE_TIME)),
        IDENTITY_MISMATCH);
  }

  @Test
  public void revocationStoreFailureIsNotAnUnverifiedResult() throws Exception {
    TestKey key = revokedCompromisedKey();
    register(key);
    CommitSignatureVerifier failing =
        new GerritCommitSignatureVerifier(
            () -> {
              Repository repo;
              try {
                repo = repoManager.openRepository(allUsers);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
              return new PublicKeyStore(repo) {
                @Override
                public PGPPublicKeyRing get(byte[] fingerprint) throws IOException {
                  throw new IOException("Injected key-store outage");
                }

                @Override
                public void close() {
                  super.close();
                  repo.close();
                }
              };
            },
            checkerFactory,
            externalIds,
            externalIdKeyFactory,
            userFactory);
    assertThrows(
        IOException.class,
        () -> failing.verify(sign(payload("test6@example.com"), key, SIGNATURE_TIME)));
  }

  @Test
  public void expiredKeyIsRejected() throws Exception {
    TestKey key = expiredKey();
    register(key);
    assertUnverified(
        verifier.verify(sign(payload("test3@example.com"), key, SIGNATURE_TIME)), INVALID_KEY);
  }

  @Test
  public void expiredKeyWasValidAtSignatureTime() throws Exception {
    TestKey key = expiredKey();
    register(key);
    assertThat(
            verifier.verify(
                    sign(payload("test3@example.com"), key, Instant.parse("2005-07-10T16:00:00Z")))
                .status)
        .isEqualTo(VERIFIED);
  }

  @Test
  public void retroactiveTrustAcceptsNewerRoot() throws Exception {
    TestKey key = expiredKey();
    TestKey root = validKeyWithoutExpiration();
    register(root);
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            key.getPublicKey(),
            key.getFirstUserId(),
            trustCertification(root, key, Instant.parse("2019-01-01T00:00:00Z")));
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertThat(
            verifierWithTrust(root)
                .verify(sign(payload("test3@example.com"), key, HISTORICAL_SIGNATURE_TIME))
                .status)
        .isEqualTo(VERIFIED);
  }

  @Test
  public void retroactiveTrustRejectsRootCreatedAfterCertification() throws Exception {
    TestKey key = expiredKey();
    TestKey root = validKeyWithoutExpiration();
    register(root);
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            key.getPublicKey(),
            key.getFirstUserId(),
            trustCertification(root, key, Instant.parse("2010-01-01T00:00:00Z")));
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertUnverified(
        verifierWithTrust(root)
            .verify(sign(payload("test3@example.com"), key, HISTORICAL_SIGNATURE_TIME)),
        INVALID_KEY);
  }

  @Test
  public void retroactiveTrustRejectsExpiredRootAtCertification() throws Exception {
    TestKey key = expiredKey();
    TestKey root = validKeyWithoutExpiration();
    PGPPublicKey expiredRoot =
        PGPPublicKey.addCertification(
            root.getPublicKey(),
            root.getFirstUserId(),
            certification(root, Instant.parse("2018-01-01T00:00:00Z"), 1));
    register(root, PGPPublicKeyRing.insertPublicKey(root.getPublicKeyRing(), expiredRoot));
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            key.getPublicKey(),
            key.getFirstUserId(),
            trustCertification(root, key, Instant.parse("2019-01-01T00:00:00Z")));
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertUnverified(
        verifierWithTrust(root)
            .verify(sign(payload("test3@example.com"), key, HISTORICAL_SIGNATURE_TIME)),
        INVALID_KEY);
  }

  @Test
  public void retroactiveTrustRejectsCompromisedRoot() throws Exception {
    TestKey key = expiredKey();
    TestKey root = validKeyWithoutExpiration();
    PGPPublicKey revokedRoot =
        PGPPublicKey.addCertification(
            PGPPublicKey.addCertification(
                root.getPublicKey(),
                primaryRevocation(
                    root, RevocationReasonTags.KEY_RETIRED, Instant.parse("2021-01-01T00:00:00Z"))),
            primaryRevocation(
                root, RevocationReasonTags.KEY_COMPROMISED, Instant.parse("2022-01-01T00:00:00Z")));
    register(root, PGPPublicKeyRing.insertPublicKey(root.getPublicKeyRing(), revokedRoot));
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            key.getPublicKey(),
            key.getFirstUserId(),
            trustCertification(root, key, Instant.parse("2019-01-01T00:00:00Z")));
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertUnverified(
        verifierWithTrust(root)
            .verify(sign(payload("test3@example.com"), key, HISTORICAL_SIGNATURE_TIME)),
        INVALID_KEY);
  }

  @Test
  public void retroactiveTrustRetriesCertifierAtDifferentCertificationTimes() throws Exception {
    TestKey key = expiredKey();
    TestKey root = validKeyWithoutExpiration();
    register(root);
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            PGPPublicKey.addCertification(
                key.getPublicKey(),
                key.getFirstUserId(),
                trustCertification(root, key, Instant.parse("2010-01-01T00:00:00Z"))),
            key.getFirstUserId(),
            trustCertification(root, key, Instant.parse("2019-01-01T00:00:00Z")));
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertThat(
            verifierWithTrust(root)
                .verify(sign(payload("test3@example.com"), key, HISTORICAL_SIGNATURE_TIME))
                .status)
        .isEqualTo(VERIFIED);
  }

  @Test
  public void forgedExpirationDoesNotValidateExpiredKey() throws Exception {
    TestKey key = expiredKey();
    PGPSignature extension = certification(key, Instant.parse("2019-01-01T00:00:00Z"), 0);
    byte[] bytes = extension.getEncoded();
    bytes[bytes.length - 1] ^= 1;
    PGPSignature damaged =
        ((PGPSignatureList) new BcPGPObjectFactory(new ByteArrayInputStream(bytes)).nextObject())
            .get(0);
    PGPPublicKey primary =
        PGPPublicKey.addCertification(key.getPublicKey(), key.getFirstUserId(), damaged);
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertUnverified(
        verifier.verify(sign(payload("test3@example.com"), key, SIGNATURE_TIME)), INVALID_KEY);
  }

  @Test
  public void expirationExtensionAfterSignatureDoesNotValidateExpiredKey() throws Exception {
    TestKey key = expiredKey();
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            key.getPublicKey(),
            key.getFirstUserId(),
            certification(key, Instant.parse("2021-01-01T00:00:00Z"), 0));
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertUnverified(
        verifier.verify(sign(payload("test3@example.com"), key, SIGNATURE_TIME)), INVALID_KEY);
  }

  @Test
  public void forgedExpirationDoesNotInvalidateValidKey() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    PGPSignature shortening = certification(key, Instant.parse("2019-01-01T00:00:00Z"), 1);
    byte[] bytes = shortening.getEncoded();
    bytes[bytes.length - 1] ^= 1;
    PGPSignature damaged =
        ((PGPSignatureList) new BcPGPObjectFactory(new ByteArrayInputStream(bytes)).nextObject())
            .get(0);
    PGPPublicKey primary =
        PGPPublicKey.addCertification(key.getPublicKey(), key.getFirstUserId(), damaged);
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertThat(verifier.verify(sign(payload("test1@example.com"), key, SIGNATURE_TIME)).status)
        .isEqualTo(VERIFIED);
  }

  @Test
  public void expirationShorteningAfterSignatureDoesNotInvalidateValidKey() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            key.getPublicKey(),
            key.getFirstUserId(),
            certification(key, Instant.parse("2021-01-01T00:00:00Z"), 1));
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertThat(verifier.verify(sign(payload("test1@example.com"), key, SIGNATURE_TIME)).status)
        .isEqualTo(VERIFIED);
  }

  @Test
  public void compromisedKeyIsRejectedEvenBeforeRevocation() throws Exception {
    TestKey key = selfRevokedKey();
    register(key);
    assertUnverified(
        verifier.verify(
            sign(payload("test4@example.com"), key, Instant.parse("2015-07-08T20:00:00Z"))),
        INVALID_KEY);
  }

  @Test
  public void compromiseRevocationAfterRetirementInvalidatesHistoricalSignature() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    PGPSignature retirement =
        primaryRevocation(
            key, RevocationReasonTags.KEY_RETIRED, Instant.parse("2021-01-01T00:00:00Z"));
    PGPSignature compromise =
        primaryRevocation(
            key, RevocationReasonTags.KEY_COMPROMISED, Instant.parse("2022-01-01T00:00:00Z"));
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            PGPPublicKey.addCertification(key.getPublicKey(), retirement), compromise);
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertUnverified(
        verifier.verify(sign(payload("test1@example.com"), key, SIGNATURE_TIME)), INVALID_KEY);
  }

  @Test
  public void compromiseRevocationBeforeRetirementInvalidatesHistoricalSignature()
      throws Exception {
    TestKey key = validKeyWithoutExpiration();
    PGPSignature retirement =
        primaryRevocation(
            key, RevocationReasonTags.KEY_RETIRED, Instant.parse("2021-01-01T00:00:00Z"));
    PGPSignature compromise =
        primaryRevocation(
            key, RevocationReasonTags.KEY_COMPROMISED, Instant.parse("2022-01-01T00:00:00Z"));
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            PGPPublicKey.addCertification(key.getPublicKey(), compromise), retirement);
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertUnverified(
        verifier.verify(sign(payload("test1@example.com"), key, SIGNATURE_TIME)), INVALID_KEY);
  }

  @Test
  public void retiredPrimaryWasValidBeforeRevocation() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    PGPPublicKey primary =
        PGPPublicKey.addCertification(
            key.getPublicKey(),
            primaryRevocation(
                key, RevocationReasonTags.KEY_RETIRED, Instant.parse("2021-01-01T00:00:00Z")));
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), primary));
    assertThat(verifier.verify(sign(payload("test1@example.com"), key, SIGNATURE_TIME)).status)
        .isEqualTo(VERIFIED);
    assertUnverified(
        verifier.verify(
            sign(payload("test1@example.com"), key, Instant.parse("2022-01-01T00:00:00Z"))),
        INVALID_KEY);
  }

  @Test
  public void validSigningSubkey() throws Exception {
    TestKey key = signingSubkey();
    register(key);
    PGPPublicKey subkey = subkey(key.getPublicKeyRing());
    var info =
        verifier.verify(
            sign(payload("test1@example.com"), secretKey(key, subkey.getKeyID()), SIGNATURE_TIME));
    assertThat(info.status).isEqualTo(VERIFIED);
    assertThat(info.signingKeyFingerprint).isEqualTo("C378369ACBCD34CC138D90B145311A6F378A0AED");
    assertThat(info.primaryKeyFingerprint).isEqualTo("04AEA7ED2F821133E5B128D1ED0625DC46328A8C");
    assertThat(info.signer._accountId).isEqualTo(signerId.get());
  }

  @Test
  public void signingSubkeyNeedsBinding() throws Exception {
    TestKey key = signingSubkey();
    PGPPublicKey subkey = subkey(key.getPublicKeyRing());
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), removeBindings(subkey)));
    assertUnverified(
        verifier.verify(
            sign(payload("test1@example.com"), secretKey(key, subkey.getKeyID()), SIGNATURE_TIME)),
        INVALID_KEY);
  }

  @Test
  public void signingSubkeyNeedsValidBinding() throws Exception {
    TestKey key = signingSubkey();
    PGPPublicKey subkey = subkey(key.getPublicKeyRing());
    byte[] bytes = subkey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING).next().getEncoded();
    bytes[bytes.length - 1] ^= 1;
    PGPSignature damaged =
        ((PGPSignatureList) new BcPGPObjectFactory(new ByteArrayInputStream(bytes)).nextObject())
            .get(0);
    PGPPublicKey replacement = PGPPublicKey.addCertification(removeBindings(subkey), damaged);
    register(key, PGPPublicKeyRing.insertPublicKey(key.getPublicKeyRing(), replacement));
    assertUnverified(
        verifier.verify(
            sign(payload("test1@example.com"), secretKey(key, subkey.getKeyID()), SIGNATURE_TIME)),
        INVALID_KEY);
  }

  @Test
  public void signingSubkeyNeedsBackSignature() throws Exception {
    TestKey key = signingSubkey();
    PGPPublicKey subkey = subkey(key.getPublicKeyRing());
    PGPSignature binding =
        keySignature(
            key,
            subkey,
            PGPSignature.SUBKEY_BINDING,
            KeyFlags.SIGN_DATA,
            (byte) 0,
            Instant.parse("2019-01-01T00:00:00Z"));
    register(
        key,
        PGPPublicKeyRing.insertPublicKey(
            key.getPublicKeyRing(),
            PGPPublicKey.addCertification(removeBindings(subkey), binding)));
    assertUnverified(
        verifier.verify(
            sign(payload("test1@example.com"), secretKey(key, subkey.getKeyID()), SIGNATURE_TIME)),
        INVALID_KEY);
  }

  @Test
  public void encryptionSubkeyCannotSignCommits() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    register(key);
    PGPPublicKey subkey = subkey(key.getPublicKeyRing());
    assertUnverified(
        verifier.verify(
            sign(payload("test1@example.com"), secretKey(key, subkey.getKeyID()), SIGNATURE_TIME)),
        INVALID_KEY);
  }

  @Test
  public void certificationOnlyPrimaryCannotSignCommits() throws Exception {
    TestKey key = signingSubkey();
    register(key);
    assertUnverified(
        verifier.verify(sign(payload("test1@example.com"), key, SIGNATURE_TIME)), INVALID_KEY);
  }

  @Test
  public void expiredSubkeyIsRejected() throws Exception {
    TestKey key = signingSubkey();
    register(key);
    PGPPublicKey subkey = subkey(key.getPublicKeyRing());
    assertUnverified(
        verifier.verify(
            sign(
                payload("test1@example.com"),
                secretKey(key, subkey.getKeyID()),
                Instant.parse("2075-01-01T00:00:00Z"))),
        INVALID_KEY);
  }

  @Test
  public void keyMustExistAtSignatureTime() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    register(key);
    assertUnverified(
        verifier.verify(
            sign(payload("test1@example.com"), key, Instant.parse("2010-01-01T00:00:00Z"))),
        INVALID_KEY);
  }

  @Test
  public void compromisedSubkeyInvalidatesHistoricalSignature() throws Exception {
    TestKey key = signingSubkey();
    PGPPublicKey subkey = subkey(key.getPublicKeyRing());
    PGPSignature revocation =
        keySignature(
            key,
            subkey,
            PGPSignature.SUBKEY_REVOCATION,
            0,
            RevocationReasonTags.KEY_COMPROMISED,
            Instant.parse("2021-01-01T00:00:00Z"));
    register(
        key,
        PGPPublicKeyRing.insertPublicKey(
            key.getPublicKeyRing(), PGPPublicKey.addCertification(subkey, revocation)));
    assertUnverified(
        verifier.verify(
            sign(payload("test1@example.com"), secretKey(key, subkey.getKeyID()), SIGNATURE_TIME)),
        INVALID_KEY);
  }

  @Test
  public void retiredSubkeyWasValidBeforeRevocation() throws Exception {
    TestKey key = signingSubkey();
    PGPPublicKey subkey = subkey(key.getPublicKeyRing());
    PGPSignature revocation =
        keySignature(
            key,
            subkey,
            PGPSignature.SUBKEY_REVOCATION,
            0,
            RevocationReasonTags.KEY_RETIRED,
            Instant.parse("2021-01-01T00:00:00Z"));
    register(
        key,
        PGPPublicKeyRing.insertPublicKey(
            key.getPublicKeyRing(), PGPPublicKey.addCertification(subkey, revocation)));
    assertThat(
            verifier.verify(
                    sign(
                        payload("test1@example.com"),
                        secretKey(key, subkey.getKeyID()),
                        SIGNATURE_TIME))
                .status)
        .isEqualTo(VERIFIED);
    assertUnverified(
        verifier.verify(
            sign(
                payload("test1@example.com"),
                secretKey(key, subkey.getKeyID()),
                Instant.parse("2022-01-01T00:00:00Z"))),
        INVALID_KEY);
  }

  private static PGPPublicKey subkey(PGPPublicKeyRing ring) {
    var keys = ring.getPublicKeys();
    keys.next();
    return keys.next();
  }

  private static PGPPublicKey removeBindings(PGPPublicKey subkey) {
    var signatures = subkey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING);
    while (signatures.hasNext()) {
      subkey = PGPPublicKey.removeCertification(subkey, signatures.next());
    }
    return subkey;
  }

  @SuppressWarnings("JdkObsolete")
  private static PGPSignature certification(TestKey key, Instant time, long keyValidity)
      throws Exception {
    PGPSignatureGenerator generator =
        new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(
                key.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
    generator.init(PGPSignature.POSITIVE_CERTIFICATION, key.getPrivateKey());
    PGPSignatureSubpacketGenerator packets = new PGPSignatureSubpacketGenerator();
    packets.setSignatureCreationTime(false, Date.from(time));
    packets.setKeyExpirationTime(false, keyValidity);
    packets.setKeyFlags(false, KeyFlags.SIGN_DATA | KeyFlags.CERTIFY_OTHER);
    generator.setHashedSubpackets(packets.generate());
    return generator.generateCertification(key.getFirstUserId(), key.getPublicKey());
  }

  @SuppressWarnings("JdkObsolete")
  private static PGPSignature trustCertification(TestKey root, TestKey key, Instant time)
      throws Exception {
    PGPSignatureGenerator generator =
        new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(
                root.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
    generator.init(PGPSignature.POSITIVE_CERTIFICATION, root.getPrivateKey());
    PGPSignatureSubpacketGenerator packets = new PGPSignatureSubpacketGenerator();
    packets.setSignatureCreationTime(false, Date.from(time));
    packets.setTrust(false, 1, 120);
    generator.setHashedSubpackets(packets.generate());
    return generator.generateCertification(key.getFirstUserId(), key.getPublicKey());
  }

  @SuppressWarnings("JdkObsolete")
  private static PGPSignature primaryRevocation(TestKey key, byte reason, Instant time)
      throws Exception {
    PGPSignatureGenerator generator =
        new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(
                key.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
    generator.init(PGPSignature.KEY_REVOCATION, key.getPrivateKey());
    PGPSignatureSubpacketGenerator packets = new PGPSignatureSubpacketGenerator();
    packets.setSignatureCreationTime(false, Date.from(time));
    packets.setRevocationReason(false, reason, "private untrusted revocation description");
    generator.setHashedSubpackets(packets.generate());
    return generator.generateCertification(key.getPublicKey());
  }

  @SuppressWarnings("JdkObsolete")
  private static PGPSignature keySignature(
      TestKey key, PGPPublicKey subkey, int type, int flags, byte reason, Instant time)
      throws Exception {
    PGPSignatureGenerator generator =
        new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(
                key.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
    generator.init(type, key.getPrivateKey());
    PGPSignatureSubpacketGenerator packets = new PGPSignatureSubpacketGenerator();
    packets.setSignatureCreationTime(false, Date.from(time));
    if (type == PGPSignature.SUBKEY_BINDING) {
      packets.setKeyFlags(false, flags);
    } else {
      packets.setRevocationReason(false, reason, "private untrusted revocation description");
    }
    generator.setHashedSubpackets(packets.generate());
    return generator.generateCertification(key.getPublicKey(), subkey);
  }

  private void register(TestKey key) throws Exception {
    register(key, key.getPublicKeyRing());
  }

  private CommitSignatureVerifier verifierWithTrust(TestKey root) {
    Config cfg = InMemoryModule.newDefaultConfig();
    cfg.setInt("receive", null, "maxTrustDepth", 1);
    cfg.setString(
        "receive",
        null,
        "trustedKey",
        BaseEncoding.base16().encode(root.getPublicKey().getFingerprint()));
    return new GerritCommitSignatureVerifier(
        storeProvider,
        new GerritPublicKeyChecker.Factory(
            cfg, accountQueryProvider, userFactory, urlFormatter, externalIdKeyFactory),
        externalIds,
        externalIdKeyFactory,
        userFactory);
  }

  private void register(TestKey key, PGPPublicKeyRing ring) throws Exception {
    String email = PushCertificateIdent.parse(key.getFirstUserId()).getEmailAddress();
    accountsUpdate
        .get()
        .update(
            "Register signing key",
            signerId,
            u ->
                u.setPreferredEmail(email)
                    .addExternalIds(
                        List.of(
                            externalIdFactory.createEmail(signerId, email),
                            externalIdFactory.create(
                                ExternalId.SCHEME_GPGKEY,
                                BaseEncoding.base16().encode(ring.getPublicKey().getFingerprint()),
                                signerId))));
    save(ring);
  }

  private void save(PGPPublicKeyRing ring) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers);
        PublicKeyStore store = new PublicKeyStore(repo)) {
      store.add(ring);
      CommitBuilder commit = new CommitBuilder();
      PersonIdent ident = new PersonIdent("Key store", "store@example.com");
      commit.setAuthor(ident);
      commit.setCommitter(ident);
      assertThat(store.save(commit))
          .isAnyOf(
              org.eclipse.jgit.lib.RefUpdate.Result.NEW,
              org.eclipse.jgit.lib.RefUpdate.Result.FAST_FORWARD);
    }
  }

  private static byte[] payload(String committerEmail) {
    return ("tree 4b825dc642cb6eb9a060e54bf8d69288fbee4904\n"
            + "author Different Author <author@example.com> 1577836800 +0200\n"
            + "committer Test Signer <"
            + committerEmail
            + "> 1577836800 +0200\n"
            + "mergetag object deadbeefdeadbeefdeadbeefdeadbeefdeadbeef\n"
            + " type commit\n tag preservation\n gpgsig this is not a signature header\n"
            + "encoding ISO-8859-1\n\n"
            + "Preserve raw café bytes and trailing spaces  \n\ngpgsig in message\n")
        .getBytes(UTF_8);
  }

  private static void assertUnverified(
      CommitSignatureInfo info, CommitSignatureInfo.Reason reason) {
    assertThat(info.status).isEqualTo(UNVERIFIED);
    assertThat(info.reason).isEqualTo(reason);
    assertThat(info.message).isNotEmpty();
    assertThat(info.signer).isNull();
  }
}
