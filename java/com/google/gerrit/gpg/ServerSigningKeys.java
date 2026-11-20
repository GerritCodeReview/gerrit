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

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.eclipse.jgit.lib.Config;

/** Restart-scoped key material and explicit trust for server attestations, not user identities. */
@Singleton
public class ServerSigningKeys implements PublicKeyLookup {
  private static final String PREFIX = "server-key ";

  private final ImmutableMap<String, PGPPublicKeyRing> publicKeys;
  @Nullable private final ActiveKey activeKey;

  @Inject
  ServerSigningKeys(@GerritServerConfig Config cfg, SecureStore secureStore, SitePaths site) {
    try {
      // The merged server config can hide ordinary values behind secure overrides. Inspect each
      // source layer's entry names, retaining loaded includes that Config.toText() would omit.
      for (Config source = cfg; source != null; source = source.getBaseConfig()) {
        if (source.getNames("gpg", false).contains("serverSigningKey")) {
          Config base = source.getBaseConfig();
          // JGit appends this layer's values to the base list. A scalar override in a different
          // layer is not a duplicate, but multiple entries within one layer (including includes)
          // are.
          int inherited =
              base != null ? base.getStringList("gpg", null, "serverSigningKey").length : 0;
          if (source.getStringList("gpg", null, "serverSigningKey").length - inherited > 1) {
            throw invalid("exactly one serverSigningKey may be active");
          }
        }
        for (String subsection : source.getSubsections("gpg")) {
          var names = source.getNames("gpg", subsection, false);
          if (subsection.startsWith(PREFIX)
              && (names.contains("secretKeyFile") || names.contains("passphrase"))) {
            throw invalid("secretKeyFile and passphrase must be stored only in secure.config");
          }
        }
      }
      var keys = ImmutableMap.<String, PGPPublicKeyRing>builder();
      var subsections = new HashMap<String, String>();
      for (String subsection : cfg.getSubsections("gpg")) {
        if (!subsection.startsWith(PREFIX)) {
          continue;
        }
        String fingerprint = normalizeFingerprint(subsection.substring(PREFIX.length()));
        String file = cfg.getString("gpg", subsection, "publicKeyFile");
        if (file == null || file.isBlank()) {
          throw invalid("every server-key entry requires publicKeyFile");
        }
        byte[] encoded = readKeyFile(site.resolve(file));
        try (var in = PGPUtil.getDecoderStream(new ByteArrayInputStream(encoded))) {
          var rings = new PGPPublicKeyRingCollection(in, new BcKeyFingerprintCalculator());
          if (rings.size() != 1) {
            throw invalid("publicKeyFile must contain exactly one public key ring");
          }
          var ring = rings.getKeyRings().next();
          if (!fingerprint.equals(fingerprint(ring.getPublicKey()))) {
            throw invalid("server-key fingerprint does not match publicKeyFile");
          }
          keys.put(fingerprint, ring);
          subsections.put(fingerprint, subsection);
        }
      }
      publicKeys = keys.buildOrThrow();
      String active = secureOption(secureStore, null, "serverSigningKey");
      if (active == null) {
        active = cfg.getString("gpg", null, "serverSigningKey");
      }
      if (active != null) {
        String fingerprint = normalizeFingerprint(active);
        if (!publicKeys.containsKey(fingerprint)) {
          throw invalid("serverSigningKey must identify a configured server-key primary key");
        }
        String subsection = subsections.get(fingerprint);
        String secret = secureOption(secureStore, subsection, "secretKeyFile");
        if (secret == null || secret.isBlank()) {
          throw invalid("the active serverSigningKey requires secretKeyFile in secure.config");
        }
        String passphrase = secureOption(secureStore, subsection, "passphrase");
        activeKey = loadActiveKey(publicKeys.get(fingerprint), site.resolve(secret), passphrase);
      } else {
        activeKey = null;
      }
    } catch (IOException | PGPException | IllegalArgumentException e) {
      throw new ProvisionException("Cannot load configured server signing keys", e);
    }
  }

  @Nullable
  ActiveKey activeKey() {
    return activeKey;
  }

  private ActiveKey loadActiveKey(
      PGPPublicKeyRing publicRing, Path secretFile, @Nullable String passphrase)
      throws IOException, PGPException {
    byte[] encoded = readKeyFile(secretFile);
    try (var in = PGPUtil.getDecoderStream(new ByteArrayInputStream(encoded))) {
      var rings = new PGPSecretKeyRingCollection(in, new BcKeyFingerprintCalculator());
      if (rings.size() != 1) {
        throw invalid("secretKeyFile must contain exactly one secret key ring");
      }
      var secretRing = rings.getKeyRings().next();
      if (!fingerprint(publicRing.getPublicKey()).equals(fingerprint(secretRing.getPublicKey()))) {
        throw invalid("the secret and public primary key fingerprints must agree");
      }
      var candidates = new ArrayList<PGPSecretKey>();
      var secretPublicKeys = ImmutableList.<PGPPublicKey>builder();
      Instant now = Instant.now();
      for (var keys = secretRing.getSecretKeys(); keys.hasNext(); ) {
        PGPSecretKey secretKey = keys.next();
        PGPPublicKey publicKey = publicRing.getPublicKey(secretKey.getKeyID());
        if (publicKey == null
            || !Arrays.equals(
                publicKey.getFingerprint(), secretKey.getPublicKey().getFingerprint())) {
          throw invalid("the secret and public key/subkey fingerprints must agree");
        }
        if (!secretKey.isPrivateKeyEmpty()) {
          secretPublicKeys.add(publicKey);
          if (isValid(publicRing, publicKey, now)) {
            candidates.add(secretKey);
          }
        }
      }
      if (candidates.size() != 1) {
        throw invalid(
            "the active key must contain exactly one currently eligible secret signing key");
      }
      PGPSecretKey secretKey = candidates.get(0);
      char[] password = passphrase != null ? passphrase.toCharArray() : new char[0];
      try {
        PGPPrivateKey privateKey =
            secretKey.extractPrivateKey(
                new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider())
                    .build(password));
        if (privateKey == null) {
          throw invalid("the active signing key has no usable private key material");
        }
        return new ActiveKey(
            publicRing,
            publicRing.getPublicKey(secretKey.getKeyID()),
            privateKey,
            secretPublicKeys.build());
      } finally {
        Arrays.fill(password, '\0');
      }
    } finally {
      Arrays.fill(encoded, (byte) 0);
    }
  }

  private static byte[] readKeyFile(Path file) throws IOException {
    byte[] encoded = Files.readAllBytes(file);
    // A binary OpenPGP packet begins with its high bit set. Armor decoding otherwise stops at the
    // first block, so explicitly reject additional blocks or trailing data before decoding.
    if (encoded.length == 0 || (encoded[0] & 0x80) == 0) {
      String armor = new String(encoded, US_ASCII).strip();
      int newline = armor.indexOf('\n');
      if (!armor.startsWith("-----BEGIN PGP ") || newline < 0) {
        throw invalid("a key file must contain binary key packets or one complete armor block");
      }
      String endMarker = armor.substring(0, newline).stripTrailing().replace("BEGIN", "END");
      int end = armor.indexOf(endMarker, newline);
      if (end < 0 || !armor.substring(end + endMarker.length()).isBlank()) {
        throw invalid("a key file must contain exactly one complete armor block");
      }
    }
    return encoded;
  }

  @Nullable
  private static String secureOption(
      SecureStore secureStore, @Nullable String subsection, String name) {
    String[] values = secureStore.getList("gpg", subsection, name);
    if (values == null || values.length == 0) {
      return null;
    }
    if (values.length != 1) {
      throw invalid("each secure server-key option must have exactly one value");
    }
    return values[0];
  }

  record ActiveKey(
      PGPPublicKeyRing ring,
      PGPPublicKey publicKey,
      PGPPrivateKey privateKey,
      ImmutableList<PGPPublicKey> secretPublicKeys) {}

  synchronized boolean isActiveKeyValid(Instant time) {
    if (activeKey == null) {
      return false;
    }
    int candidates = 0;
    boolean selectedKeyValid = false;
    // A future certification can enable another secret key. Never silently switch keys or use the
    // startup selection after it becomes ambiguous; only its private material is decrypted.
    for (PGPPublicKey key : activeKey.secretPublicKeys()) {
      if (isValid(activeKey.ring(), key, time)) {
        candidates++;
        selectedKeyValid |=
            Arrays.equals(key.getFingerprint(), activeKey.publicKey().getFingerprint());
      }
    }
    return candidates == 1 && selectedKeyValid;
  }

  @Override
  public PGPPublicKeyRingCollection get(long keyId) {
    return new PGPPublicKeyRingCollection(
        publicKeys.values().stream().filter(ring -> ring.getPublicKey(keyId) != null).toList());
  }

  @Nullable
  @Override
  public PGPPublicKeyRing get(byte[] fingerprint) {
    return publicKeys.get(BaseEncoding.base16().encode(fingerprint));
  }

  // BC certification signatures contain mutable verifier state. Serialize checks of the shared
  // restart-scoped key packets; every request still performs a new validity/revocation check.
  synchronized boolean isValid(PGPPublicKeyRing ring, PGPPublicKey signingKey, Instant time) {
    return CommitSigningKeyChecker.isValid(ring.getPublicKey(), signingKey, time)
        && new PublicKeyChecker()
            .setStore(this)
            .enableCommitSignatureChecks()
            .setFailOnError()
            .setEffectiveTime(time)
            .check(ring.getPublicKey())
            .isTrusted();
  }

  static String fingerprint(PGPPublicKey key) {
    return BaseEncoding.base16().encode(key.getFingerprint());
  }

  private static String normalizeFingerprint(String fingerprint) {
    String normalized = fingerprint.toUpperCase(Locale.ROOT);
    if (!(normalized.length() == 40 || normalized.length() == 64)
        || !normalized.matches("[0-9A-F]+")) {
      throw invalid("a full primary key fingerprint is required");
    }
    return normalized;
  }

  private static ProvisionException invalid(String message) {
    return new ProvisionException("Invalid server signing configuration: " + message);
  }
}
