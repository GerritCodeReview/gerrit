// Copyright (C) 2020 The Android Open Source Project
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
package com.google.gerrit.server.mail;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import java.util.Random;
import org.junit.Before;
import org.junit.Test;

public class SignedTokenTest {

  private static final String REGISTER_EMAIL_PRIVATE_KEY =
      "R2Vycml0JTIwcmVnaXN0ZXJFbWFpbFByaXZhdGVLZXk=";
  private static final String URL_SAFE_REGISTER_EMAIL_PRIVATE_KEY =
      REGISTER_EMAIL_PRIVATE_KEY.replaceFirst("R2", "_-");
  private static final String URL_UNSAFE_REGISTER_EMAIL_PRIVATE_KEY_WITH_PLUS =
      REGISTER_EMAIL_PRIVATE_KEY.replaceFirst("R", "+");
  private static final String URL_UNSAFE_REGISTER_EMAIL_PRIVATE_KEY_WITH_SLASH =
      REGISTER_EMAIL_PRIVATE_KEY.replaceFirst("R", "/");

  private static final int maxAge = 5;
  private static final String TEXT = "This is a text";
  private static final String FORGED_TEXT = "This is a forged text";
  private static final String FORGED_TOKEN = String.format("Zm9yZ2VkJTIwa2V5$%s", TEXT);

  private SignedToken signedToken;

  @Before
  public void setUp() throws Exception {
    signedToken = new SignedToken(maxAge, REGISTER_EMAIL_PRIVATE_KEY);
  }

  /**
   * Test new token: the key is a normal BASE64 string without index of '62'(+ or _) or '63'(/ or -)
   */
  @Test
  public void newTokenKeyNotContainsUnsafeCharTest() throws Exception {
    new SignedToken(maxAge, REGISTER_EMAIL_PRIVATE_KEY);
  }

  /** Test new token: the key is an URL safe BASE64 string with indexes of '62'(_) and '63'(-) */
  @Test
  public void newTokenWithUrlSafeBase64Test() throws Exception {
    new SignedToken(maxAge, URL_SAFE_REGISTER_EMAIL_PRIVATE_KEY);
  }

  /** Test new token: the key is an URL unsafe BASE64 string with index of '62'(+) */
  @Test
  public void newTokenWithUrlUnsafeBase64PlusTest() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> new SignedToken(maxAge, URL_UNSAFE_REGISTER_EMAIL_PRIVATE_KEY_WITH_PLUS));

    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            "com.google.common.io.BaseEncoding$DecodingException: Unrecognized character: +");
  }

  /** Test new token: the key is an URL unsafe BASE64 string with '63'(/) */
  @Test
  public void newTokenWithUrlUnsafeBase64SlashTest() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> new SignedToken(maxAge, URL_UNSAFE_REGISTER_EMAIL_PRIVATE_KEY_WITH_SLASH));

    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            "com.google.common.io.BaseEncoding$DecodingException: Unrecognized character: /");
  }

  /** Test check token: BASE64 encoding and decoding in a safe URL way */
  @Test
  public void checkTokenTest() throws Exception {
    String token = signedToken.newToken(TEXT);
    ValidToken validToken = signedToken.checkToken(token, TEXT);
    assertThat(validToken).isNotNull();
    assertThat(validToken.getData()).isEqualTo(TEXT);
  }

  /** Test check token: input token string is null */
  @Test
  public void checkTokenInputTokenNullTest() {
    CheckTokenException thrown =
        assertThrows(CheckTokenException.class, () -> signedToken.checkToken(null, TEXT));

    assertThat(thrown).hasMessageThat().isEqualTo("Invalid token string! Input token is blank!");
  }

  /** Test check token: input token string is empty */
  @Test
  public void checkTokenInputTokenEmptyTest() {
    CheckTokenException thrown =
        assertThrows(CheckTokenException.class, () -> signedToken.checkToken("", TEXT));

    assertThat(thrown).hasMessageThat().isEqualTo("Invalid token string! Input token is blank!");
  }

  /** Test check token: token string is not illegal with no '$' character */
  @Test
  public void checkTokenInputTokenNoDollarSplitCharTest() {
    CheckTokenException thrown =
        assertThrows(
            CheckTokenException.class,
            () -> {
              String token = signedToken.newToken(TEXT);
              token = token.replace("$", "¥");
              signedToken.checkToken(token, TEXT);
            });

    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Invalid token string! Input token string does not contain character '$' !");
  }

  /** Test check token: token string length is match but is not a legal BASE64 string */
  @Test
  public void checkTokenInputTokenKeyBase64DecodeFailTest() {
    CheckTokenException thrown =
        assertThrows(
            CheckTokenException.class,
            () -> {
              String token = signedToken.newToken(TEXT);
              String key = randomString(token.indexOf("$") + 1);
              String illegalBase64Token = key + "$" + TEXT;
              signedToken.checkToken(illegalBase64Token, TEXT);
            });

    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Invalid token string! Decode token key with BASE64 fails!");
  }

  /** Test check token: token is illegal with a forged key */
  @Test
  public void checkTokenForgedKeyTest() {
    CheckTokenException thrown =
        assertThrows(CheckTokenException.class, () -> signedToken.checkToken(FORGED_TOKEN, TEXT));

    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Invalid token string! Token key length not match!");
  }

  /** Test check token: token is illegal with a forged text */
  @Test
  public void checkTokenForgedTextTest() {
    CheckTokenException thrown =
        assertThrows(
            CheckTokenException.class,
            () -> {
              String token = signedToken.newToken(TEXT);
              signedToken.checkToken(token, FORGED_TEXT);
            });

    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Invalid token string! Text of token string not match!");
  }

  private static String randomString(int length) {
    String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    Random random = new Random();
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < length; i++) {
      int number = random.nextInt(62);
      sb.append(str.charAt(number));
    }
    return sb.toString();
  }
}
