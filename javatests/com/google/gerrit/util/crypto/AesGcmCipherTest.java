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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.Test;

public class AesGcmCipherTest {
  private static final byte[] INFO = "aes-gcm-cipher-test".getBytes(UTF_8);

  private final AesGcmCipher cipher =
      new AesGcmCipher("0123456789abcdef0123456789abcdef".getBytes(US_ASCII), INFO);

  private static byte[] aad() {
    return "aad".getBytes(UTF_8);
  }

  @Test
  public void hkdfSha256_matchesRfc5869NoSaltNoInfoTestVector() throws Exception {
    byte[] ikm = new byte[22];
    Arrays.fill(ikm, (byte) 0x0b);

    assertThat(HexFormat.of().formatHex(AesGcmCipher.hkdfSha256(ikm, new byte[0], 42)))
        .isEqualTo(
            "8da4e775a563c18f715f802a063c5a31"
                + "b8a11f5c5ee1879ec3454e5f3c738d2d"
                + "9d201395faa4b61a96c8");
  }

  @Test
  public void sealThenOpen_roundTrips() {
    String sealed = cipher.seal(aad(), "hello");
    assertThat(cipher.isSealed(sealed)).isTrue();
    assertThat(cipher.open(aad(), sealed)).isEqualTo("hello");
  }

  @Test
  public void seal_usesFreshIv_soCiphertextDiffers() {
    assertThat(cipher.seal(aad(), "hello")).isNotEqualTo(cipher.seal(aad(), "hello"));
  }

  @Test
  public void open_wrongAad_throws() {
    String sealed = cipher.seal(aad(), "hello");
    assertThrows(
        IllegalStateException.class, () -> cipher.open("different".getBytes(UTF_8), sealed));
  }

  @Test
  public void open_tampered_throws() {
    String sealed = cipher.seal(aad(), "hello");
    int i = sealed.length() / 2;
    String tampered =
        sealed.substring(0, i) + (sealed.charAt(i) == 'A' ? 'B' : 'A') + sealed.substring(i + 1);
    assertThrows(IllegalStateException.class, () -> cipher.open(aad(), tampered));
  }

  @Test
  public void open_corruptPayload_throws() {
    assertThrows(IllegalStateException.class, () -> cipher.open(aad(), "gcm:v1:not-base64"));
  }

  @Test
  public void open_unsealedPayload_throws() {
    assertThrows(IllegalStateException.class, () -> cipher.open(aad(), "plain"));
  }

  @Test
  public void isSealed_recognizesPrefix() {
    assertThat(cipher.isSealed(null)).isFalse();
    assertThat(cipher.isSealed("plain")).isFalse();
    assertThat(cipher.isSealed(cipher.seal(aad(), "hello"))).isTrue();
  }
}
