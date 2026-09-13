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

package com.google.gerrit.auth.oauth;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthTokenEncrypter;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;

/**
 * Encrypts the sensitive fields ({@code token}, {@code secret}, {@code raw}) of an {@link
 * OAuthToken} with AES-256-GCM before they are persisted in the {@code oauth_tokens} cache, and
 * decrypts them on read. {@code expiresAt} and {@code providerId} are left in cleartext but
 * authenticated as GCM additional data (AAD), together with the field name, to detect metadata
 * tampering and encrypted field swaps.
 *
 * <p>The configured key is expanded into an isolated 256-bit AES key with HKDF-SHA256 (RFC 5869 /
 * JDK 25 {@link KDF}), domain-separated by {@code "gerrit-oauth-token-encryption-v1"}. Input key
 * bytes are zeroed after derivation.
 *
 * <p>Bound (see {@code AuthModule}) when {@code auth.tokenEncryptionKey} is set. Each value is
 * stored as {@code "gcm:v1:" + Base64(iv || ciphertext || tag)} with a fresh 96-bit IV; a value
 * without the prefix is returned unchanged, so a cleartext entry written before the key was set
 * still reads back.
 */
public final class OAuthTokenAesGcmEncrypter implements OAuthTokenEncrypter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PREFIX = "gcm:v1:";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String KDF_ALGORITHM = "HKDF-SHA256";
  private static final byte[] HKDF_INFO = "gerrit-oauth-token-encryption-v1".getBytes(UTF_8);
  private static final byte[] AAD_CONTEXT = "gerrit-oauth-token-encryption-aad-v1".getBytes(UTF_8);

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int DERIVED_KEY_BYTES = 32;

  private final SecretKey derivedKey;

  /**
   * Derives a dedicated 256-bit AES key from the given key material via HKDF, then wipes the input.
   *
   * @param masterKeyBytes raw key material (at least 128 bits)
   */
  public OAuthTokenAesGcmEncrypter(byte[] masterKeyBytes) {
    if (masterKeyBytes == null || masterKeyBytes.length < 16) {
      throw new IllegalArgumentException("Master key material must be at least 128 bits.");
    }
    try {
      AlgorithmParameterSpec hkdfSpec =
          HKDFParameterSpec.ofExtract()
              .addIKM(masterKeyBytes)
              .thenExpand(HKDF_INFO, DERIVED_KEY_BYTES);
      KDF kdf = KDF.getInstance(KDF_ALGORITHM);
      this.derivedKey = kdf.deriveKey("AES", hkdfSpec);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to derive encryption key via HKDF", e);
    } finally {
      Arrays.fill(masterKeyBytes, (byte) 0);
    }
  }

  @Override
  @Nullable
  public OAuthToken encrypt(OAuthToken t) {
    if (t == null) {
      return null;
    }
    OAuthToken out =
        new OAuthToken(
            enc("token", t.getToken(), t.getExpiresAt(), t.getProviderId()),
            enc("secret", t.getSecret(), t.getExpiresAt(), t.getProviderId()),
            enc("raw", t.getRaw(), t.getExpiresAt(), t.getProviderId()),
            t.getExpiresAt(),
            t.getProviderId());
    logger.atFine().log(
        "AES-GCM (HKDF+AAD) encrypt provider=%s expiresAt=%d: token %dB->%dB",
        t.getProviderId(), t.getExpiresAt(), len(t.getToken()), len(out.getToken()));
    return out;
  }

  @Override
  @Nullable
  public OAuthToken decrypt(OAuthToken t) {
    if (t == null) {
      return null;
    }
    OAuthToken out =
        new OAuthToken(
            dec("token", t.getToken(), t.getExpiresAt(), t.getProviderId()),
            dec("secret", t.getSecret(), t.getExpiresAt(), t.getProviderId()),
            dec("raw", t.getRaw(), t.getExpiresAt(), t.getProviderId()),
            t.getExpiresAt(),
            t.getProviderId());
    logger.atFine().log(
        "AES-GCM (HKDF+AAD) decrypt provider=%s expiresAt=%d: stored=%s -> token %dB",
        t.getProviderId(),
        t.getExpiresAt(),
        t.getToken() != null && t.getToken().startsWith(PREFIX) ? "gcm:v1" : "cleartext",
        len(out.getToken()));
    return out;
  }

  private String enc(String fieldName, String plain, long expiresAt, String providerId) {
    if (plain == null || plain.isEmpty()) {
      return plain;
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      Holder.RANDOM.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, derivedKey, new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(aad(fieldName, expiresAt, providerId));

      byte[] pt = plain.getBytes(UTF_8);
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
      throw new IllegalStateException("OAuth token encryption failed", e);
    }
  }

  private String dec(String fieldName, String stored, long expiresAt, String providerId) {
    if (stored == null || !stored.startsWith(PREFIX)) {
      return stored; // Cleartext entry written before the key was set.
    }
    try {
      byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      if (all.length < IV_BYTES + (TAG_BITS / 8)) {
        throw new IllegalArgumentException("Truncated or corrupted ciphertext payload.");
      }
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, derivedKey, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
      cipher.updateAAD(aad(fieldName, expiresAt, providerId));

      byte[] pt = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
      try {
        return new String(pt, UTF_8);
      } finally {
        Arrays.fill(pt, (byte) 0);
      }
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("OAuth token decryption failed (corrupt payload)", e);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("OAuth token decryption failed (wrong key or tampered)", e);
    }
  }

  private static byte[] aad(String fieldName, long expiresAt, String providerId) {
    byte[] fieldNameBytes = fieldName.getBytes(UTF_8);
    byte[] providerIdBytes = providerId == null ? new byte[0] : providerId.getBytes(UTF_8);
    int totalSize =
        AAD_CONTEXT.length
            + Integer.BYTES
            + fieldNameBytes.length
            + Long.BYTES
            + 1
            + Integer.BYTES
            + providerIdBytes.length;
    return ByteBuffer.allocate(totalSize)
        .put(AAD_CONTEXT)
        .putInt(fieldNameBytes.length)
        .put(fieldNameBytes)
        .putLong(expiresAt)
        .put((byte) (providerId == null ? 0 : 1))
        .putInt(providerIdBytes.length)
        .put(providerIdBytes)
        .array();
  }

  private static int len(String s) {
    return s == null ? 0 : s.length();
  }

  private static class Holder {
    private static final SecureRandom RANDOM = new SecureRandom();
  }
}
