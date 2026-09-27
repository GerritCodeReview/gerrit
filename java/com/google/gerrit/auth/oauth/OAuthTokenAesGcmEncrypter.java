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
import com.google.gerrit.util.crypto.AesGcmCipher;
import java.nio.ByteBuffer;

/**
 * Encrypts the sensitive fields ({@code token}, {@code secret}, {@code raw}) of an {@link
 * OAuthToken} before they are persisted in the {@code oauth_tokens} cache, and decrypts them on
 * read. {@code expiresAt} and {@code providerId} are left in cleartext but authenticated as
 * additional data (AAD), together with the field name, to detect metadata tampering and encrypted
 * field swaps.
 *
 * <p>The AES-GCM/HKDF mechanics live in {@link AesGcmCipher}; this class supplies only the
 * OAuth-specific policy: which fields are encrypted, how {@code expiresAt}/{@code providerId} are
 * bound as AAD, and the legacy cleartext passthrough for entries written before a key was set.
 *
 * <p>Bound (see {@code AuthModule}) when {@code auth.tokenEncryptionKey} is set. A value without
 * the {@code gcm:v1:} prefix is returned unchanged, so a cleartext entry written before the key was
 * set still reads back.
 */
public final class OAuthTokenAesGcmEncrypter implements OAuthTokenEncrypter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final byte[] HKDF_INFO = "gerrit-oauth-token-encryption-v1".getBytes(UTF_8);
  private static final byte[] AAD_CONTEXT = "gerrit-oauth-token-encryption-aad-v1".getBytes(UTF_8);

  private final AesGcmCipher cipher;

  /**
   * @param masterKeyBytes raw key material (at least 128 bits); zeroed after key derivation
   */
  public OAuthTokenAesGcmEncrypter(byte[] masterKeyBytes) {
    this.cipher = new AesGcmCipher(masterKeyBytes, HKDF_INFO);
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
        cipher.isSealed(t.getToken()) ? "gcm:v1" : "cleartext",
        len(out.getToken()));
    return out;
  }

  private String enc(String fieldName, String plain, long expiresAt, String providerId) {
    if (plain == null || plain.isEmpty()) {
      return plain;
    }
    return cipher.seal(aad(fieldName, expiresAt, providerId), plain);
  }

  private String dec(String fieldName, String stored, long expiresAt, String providerId) {
    if (!cipher.isSealed(stored)) {
      return stored; // Cleartext entry written before the key was set.
    }
    return cipher.open(aad(fieldName, expiresAt, providerId), stored);
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
}
