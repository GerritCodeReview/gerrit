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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertThrows;

import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import org.junit.Test;

public class OAuthTokenAesGcmEncrypterTest {
  private final OAuthTokenAesGcmEncrypter enc = new OAuthTokenAesGcmEncrypter(key());

  private static byte[] key() {
    return "0123456789abcdef0123456789abcdef".getBytes(US_ASCII); // 32 B
  }

  private static OAuthToken sample() {
    return new OAuthToken("at", "bearer", "{\"refresh_token\":\"r-1\"}", 12345L, "p:e-oauth");
  }

  @Test
  public void encryptThenDecrypt_roundTrips() {
    OAuthToken original = sample();
    assertThat(enc.decrypt(enc.encrypt(original))).isEqualTo(original);
  }

  @Test
  public void encrypt_hidesSecrets_keepsExpiresAtAndProviderId() {
    OAuthToken e = enc.encrypt(sample());
    assertThat(e.getToken()).startsWith("gcm:v1:");
    assertThat(e.getRaw()).doesNotContain("r-1"); // refresh token is not left in cleartext
    assertThat(e.getExpiresAt()).isEqualTo(12345L);
    assertThat(e.getProviderId()).isEqualTo("p:e-oauth");
  }

  @Test
  public void encrypt_usesFreshIv_soCiphertextDiffers() {
    assertThat(enc.encrypt(sample()).getRaw()).isNotEqualTo(enc.encrypt(sample()).getRaw());
  }

  @Test
  public void decrypt_cleartext_passesThrough() {
    OAuthToken cleartext = sample(); // no encrypted prefix
    assertThat(enc.decrypt(cleartext)).isEqualTo(cleartext);
  }

  @Test
  public void decrypt_tampered_throws() {
    OAuthToken e = enc.encrypt(sample());
    String raw = e.getRaw();
    int i = raw.length() / 2; // flip a character inside the base64 body (not padding)
    String tampered =
        raw.substring(0, i) + (raw.charAt(i) == 'A' ? 'B' : 'A') + raw.substring(i + 1);
    OAuthToken t =
        new OAuthToken(e.getToken(), e.getSecret(), tampered, e.getExpiresAt(), e.getProviderId());
    assertThrows(IllegalStateException.class, () -> enc.decrypt(t));
  }

  @Test
  public void decrypt_tamperedProviderId_throws() {
    OAuthToken e = enc.encrypt(sample());
    OAuthToken t =
        new OAuthToken(
            e.getToken(), e.getSecret(), e.getRaw(), e.getExpiresAt(), "different:e-oauth");
    assertThrows(IllegalStateException.class, () -> enc.decrypt(t));
  }

  @Test
  public void decrypt_tamperedExpiresAt_throws() {
    OAuthToken e = enc.encrypt(sample());
    OAuthToken t =
        new OAuthToken(
            e.getToken(), e.getSecret(), e.getRaw(), e.getExpiresAt() + 1, e.getProviderId());
    assertThrows(IllegalStateException.class, () -> enc.decrypt(t));
  }

  @Test
  public void decrypt_swappedEncryptedFields_throws() {
    OAuthToken e = enc.encrypt(sample());
    OAuthToken t =
        new OAuthToken(
            e.getRaw(), e.getSecret(), e.getToken(), e.getExpiresAt(), e.getProviderId());
    assertThrows(IllegalStateException.class, () -> enc.decrypt(t));
  }

  @Test
  public void decrypt_corruptPayload_throwsIllegalStateException() {
    OAuthToken t = new OAuthToken("gcm:v1:not-base64", "secret", "raw", 12345L, "p:e-oauth");
    assertThrows(IllegalStateException.class, () -> enc.decrypt(t));
  }

  @Test
  public void decrypt_wrongKey_throws() {
    OAuthToken e = enc.encrypt(sample());
    OAuthTokenAesGcmEncrypter other =
        new OAuthTokenAesGcmEncrypter("ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ".getBytes(US_ASCII));
    assertThrows(IllegalStateException.class, () -> other.decrypt(e));
  }
}
