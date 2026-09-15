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

package com.google.gerrit.util.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

/**
 * Authenticated encryption of short strings with AES-256-GCM.
 *
 * <p>The supplied key material is expanded into a dedicated 256-bit AES key with HKDF-SHA256 (RFC
 * 5869) using BouncyCastle, domain-separated by a caller-provided {@code info} label; the input key
 * bytes are zeroed after derivation. Each value is sealed as {@code "gcm:v1:" + Base64(iv ||
 * ciphertext || tag)} with a fresh 96-bit IV, and the caller supplies opaque additional
 * authenticated data (AAD) that is bound into the tag.
 */
public final class AesGcmCipher {
  private static final String PREFIX = "gcm:v1:";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int DERIVED_KEY_BYTES = 32;
  private static final ThreadLocal<Cipher> CIPHERS =
      ThreadLocal.withInitial(AesGcmCipher::newCipher);

  private final SecretKey derivedKey;

  /**
   * Derives a dedicated 256-bit AES key from the given key material via HKDF, then wipes the input.
   *
   * @param masterKeyBytes raw key material (at least 128 bits); zeroed after derivation
   * @param info HKDF domain-separation label
   */
  public AesGcmCipher(byte[] masterKeyBytes, byte[] info) {
    if (masterKeyBytes == null || masterKeyBytes.length < 16) {
      throw new IllegalArgumentException("Master key material must be at least 128 bits.");
    }
    try {
      byte[] keyBytes = hkdfSha256(masterKeyBytes, info, DERIVED_KEY_BYTES);
      try {
        this.derivedKey = new SecretKeySpec(keyBytes, "AES");
      } finally {
        Arrays.fill(keyBytes, (byte) 0);
      }
    } finally {
      Arrays.fill(masterKeyBytes, (byte) 0);
    }
  }

  /** Returns whether {@code value} is a sealed payload produced by {@link #seal}. */
  public boolean isSealed(String value) {
    return value != null && value.startsWith(PREFIX);
  }

  /**
   * Seals {@code plaintext} under {@code aad}.
   *
   * @return {@code "gcm:v1:" + Base64(iv || ciphertext || tag)}
   */
  public String seal(byte[] aad, String plaintext) {
    try {
      byte[] iv = new byte[IV_BYTES];
      Holder.RANDOM.nextBytes(iv);

      Cipher cipher = CIPHERS.get();
      cipher.init(Cipher.ENCRYPT_MODE, derivedKey, new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(aad);

      byte[] pt = plaintext.getBytes(UTF_8);
      byte[] ct;
      try {
        ct = cipher.doFinal(pt);
      } finally {
        Arrays.fill(pt, (byte) 0);
      }

      byte[] out = new byte[IV_BYTES + ct.length];
      System.arraycopy(iv, 0, out, 0, IV_BYTES);
      System.arraycopy(ct, 0, out, IV_BYTES, ct.length);
      return PREFIX + Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM encryption failed", e);
    }
  }

  /**
   * Opens a payload produced by {@link #seal} under the same {@code aad}.
   *
   * @throws IllegalStateException if the payload is corrupt, tampered, or sealed under a different
   *     key or AAD
   */
  public String open(byte[] aad, String sealed) {
    if (!isSealed(sealed)) {
      throw new IllegalStateException("AES-GCM decryption failed (missing prefix)");
    }
    try {
      byte[] all = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
      if (all.length < IV_BYTES + (TAG_BITS / 8)) {
        throw new IllegalArgumentException("Truncated or corrupted ciphertext payload.");
      }
      Cipher cipher = CIPHERS.get();
      cipher.init(
          Cipher.DECRYPT_MODE, derivedKey, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
      cipher.updateAAD(aad);

      byte[] pt = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
      try {
        return new String(pt, UTF_8);
      } finally {
        Arrays.fill(pt, (byte) 0);
      }
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("AES-GCM decryption failed (corrupt payload)", e);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM decryption failed (wrong key or tampered)", e);
    }
  }

  /** HKDF-SHA256 using BouncyCastle HKDFBytesGenerator. */
  private static byte[] hkdfSha256(byte[] ikm, byte[] info, int length) {
    HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
    hkdf.init(new HKDFParameters(ikm, null, info));
    byte[] okm = new byte[length];
    hkdf.generateBytes(okm, 0, length);
    return okm;
  }

  private static Cipher newCipher() {
    try {
      return Cipher.getInstance(TRANSFORMATION);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to initialize AES-GCM cipher", e);
    }
  }

  private static class Holder {
    private static final SecureRandom RANDOM = new SecureRandom();
  }
}
