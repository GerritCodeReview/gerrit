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

package com.google.gerrit.gpg.testing;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;

/** Signs hand-built commit payloads without reserializing any of their bytes. */
public class TestCommitSignatures {
  /** Reuses test key material, with a certified signing subkey and unencrypted secret keys. */
  public static TestKey signingSubkey() throws Exception {
    return signingSubkey(KeyFlags.CERTIFY_OTHER, KeyFlags.SIGN_DATA);
  }

  @SuppressWarnings("JdkObsolete")
  public static TestKey signingSubkey(int primaryFlags, int subkeyFlags) throws Exception {
    TestKey primary = TestKeys.validKeyWithoutExpiration();
    TestKey subkey = TestKeys.validKeyWithExpiration();
    var primaryPair = new PGPKeyPair(primary.getPublicKey(), primary.getPrivateKey());
    var subkeyPair = new PGPKeyPair(subkey.getPublicKey(), subkey.getPrivateKey());
    var signerBuilder =
        new BcPGPContentSignerBuilder(
            primary.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256);
    var primaryPackets = new PGPSignatureSubpacketGenerator();
    primaryPackets.setSignatureCreationTime(
        false, Date.from(Instant.parse("2019-01-01T00:00:00Z")));
    primaryPackets.setKeyFlags(false, primaryFlags);
    var generator =
        new PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            primaryPair,
            "Testuser One <test1@example.com>",
            new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1),
            primaryPackets.generate(),
            null,
            signerBuilder,
            null);
    var backSignature =
        new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(
                subkey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
    backSignature.init(PGPSignature.PRIMARYKEY_BINDING, subkey.getPrivateKey());
    backSignature.setHashedSubpackets(primaryPackets.generate());
    var subkeyPackets = new PGPSignatureSubpacketGenerator();
    subkeyPackets.setSignatureCreationTime(false, Date.from(Instant.parse("2019-01-01T00:00:00Z")));
    subkeyPackets.setKeyFlags(false, subkeyFlags);
    subkeyPackets.setKeyExpirationTime(false, 50L * 365 * 24 * 60 * 60);
    subkeyPackets.addEmbeddedSignature(
        false,
        backSignature.generateCertification(primaryPair.getPublicKey(), subkeyPair.getPublicKey()));
    generator.addSubKey(subkeyPair, subkeyPackets.generate(), null);
    ByteArrayOutputStream publicBytes = new ByteArrayOutputStream();
    try (ArmoredOutputStream out = new ArmoredOutputStream(publicBytes)) {
      generator.generatePublicKeyRing().encode(out);
    }
    ByteArrayOutputStream secretBytes = new ByteArrayOutputStream();
    try (ArmoredOutputStream out = new ArmoredOutputStream(secretBytes)) {
      generator.generateSecretKeyRing().encode(out);
    }
    return new TestKey(publicBytes.toString(US_ASCII), secretBytes.toString(US_ASCII));
  }

  public static byte[] sign(byte[] payload, TestKey key, Instant time) throws Exception {
    return sign(payload, key.getSecretKey(), time);
  }

  @SuppressWarnings("JdkObsolete")
  public static byte[] sign(byte[] payload, PGPSecretKey key, Instant time) throws Exception {
    PGPSignatureGenerator generator =
        new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(
                key.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
    generator.init(
        PGPSignature.BINARY_DOCUMENT,
        key.extractPrivateKey(
            new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider())
                .build(new char[0])));
    PGPSignatureSubpacketGenerator packets = new PGPSignatureSubpacketGenerator();
    packets.setSignatureCreationTime(false, Date.from(time));
    generator.setHashedSubpackets(packets.generate());
    generator.update(payload);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ArmoredOutputStream out = new ArmoredOutputStream(buffer)) {
      generator.generate().encode(out);
    }
    return addSignature(payload, buffer.toString(US_ASCII));
  }

  public static PGPSecretKey secretKey(TestKey key, long keyId) throws Exception {
    try (ArmoredInputStream in =
        new ArmoredInputStream(
            new ByteArrayInputStream(key.getSecretKeyArmored().getBytes(US_ASCII)))) {
      return new PGPSecretKeyRing(in, new BcKeyFingerprintCalculator()).getSecretKey(keyId);
    }
  }

  public static byte[] addSignature(byte[] payload, String signature) throws Exception {
    for (int i = 0; i + 1 < payload.length; i++) {
      if (payload[i] == '\n' && payload[i + 1] == '\n') {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(payload, 0, i + 1);
        result.write(
            ("gpgsig " + signature.strip().replace("\n", "\n ") + "\n").getBytes(US_ASCII));
        result.write(payload, i + 1, payload.length - i - 1);
        return result.toByteArray();
      }
    }
    throw new IllegalArgumentException("Commit payload has no header separator");
  }

  private TestCommitSignatures() {}
}
