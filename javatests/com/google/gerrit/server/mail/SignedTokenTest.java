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
import static org.junit.Assert.assertThrows;

import java.util.Random;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Test;

public class SignedTokenTest {

  private static final Pattern URL_UNSAFE_CHARS = Pattern.compile("(\\+|/)");
  private static final String REGISTER_EMAIL_PRIVATE_KEY = "TGMv3/bTC42jUKQndTQrXyHhHYMP0t69i/4=";
  private static final int maxAge = 5;
  private static final String TEXT = "This is a text";
  private static final String FORGED_TEXT = "This is a forged text";
  private static final String FORGED_TOKEN = String.format("Zm9yZ2VkJTIwa2V5$%s", TEXT);

  private SignedToken signedToken;

  @Before
  public void setUp() throws Exception {
    signedToken = new SignedToken(maxAge, REGISTER_EMAIL_PRIVATE_KEY);
  }

  /** Test new token: the key is a normal BASE64 string that can be used for URL safely */
  @Test
  public void newTokenKeyDoesNotContainUnsafeChar() throws Exception {
    assertThat(signedToken.newToken(TEXT)).doesNotContainMatch(URL_UNSAFE_CHARS);
  }

  /** Test new token: the key is an URL unsafe BASE64 string with index of '62'(+) */
  @Test
  public void newTokenWithUrlUnsafeBase64Plus() throws Exception {
    String token = "+" + signedToken.newToken(TEXT);
    CheckTokenException thrown =
        assertThrows(CheckTokenException.class, () -> signedToken.checkToken(token, TEXT));

    assertThat(thrown).hasMessageThat().contains("decoding failed");

    assertThat(thrown)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo(
            "com.google.common.io.BaseEncoding$DecodingException: Unrecognized character: +");
  }

  /** Test new token: the key is an URL unsafe BASE64 string with '63'(/) */
  @Test
  public void newTokenWithUrlUnsafeBase64Slash() throws Exception {
    String token = "/" + signedToken.newToken(TEXT);
    CheckTokenException thrown =
        assertThrows(CheckTokenException.class, () -> signedToken.checkToken(token, TEXT));

    assertThat(thrown).hasMessageThat().contains("decoding failed");

    assertThat(thrown)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo(
            "com.google.common.io.BaseEncoding$DecodingException: Unrecognized character: /");
  }

  /** Test check token: BASE64 encoding and decoding in a safe URL way */
  @Test
  public void checkToken() throws Exception {
    String token = signedToken.newToken(TEXT);
    ValidToken validToken = signedToken.checkToken(token, TEXT);
    assertThat(validToken).isNotNull();
    assertThat(validToken.getData()).isEqualTo(TEXT);
  }

  /** Test check token: input token string is null */
  @Test
  public void checkTokenInputTokenNull() throws Exception {
    CheckTokenException thrown =
        assertThrows(CheckTokenException.class, () -> signedToken.checkToken(null, TEXT));

    assertThat(thrown).hasMessageThat().isEqualTo("Empty token");
  }

  /** Test check token: input token string is empty */
  @Test
  public void checkTokenInputTokenEmpty() throws Exception {
    CheckTokenException thrown =
        assertThrows(CheckTokenException.class, () -> signedToken.checkToken("", TEXT));

    assertThat(thrown).hasMessageThat().isEqualTo("Empty token");
  }

  /** Test check token: token string is not illegal with no '$' character */
  @Test
  public void checkTokenInputTokenNoDollarSplitChar() throws Exception {
    String token = signedToken.newToken(TEXT).replace("$", "¥");
    CheckTokenException thrown =
        assertThrows(CheckTokenException.class, () -> signedToken.checkToken(token, TEXT));

    assertThat(thrown).hasMessageThat().isEqualTo("Token does not contain character '$'");
  }

  /** Test check token: token string length is match but is not a legal BASE64 string */
  @Test
  public void checkTokenInputTokenKeyBase64DecodeFail() throws Exception {
    String token = signedToken.newToken(TEXT);
    String key = randomString(token.indexOf("$") + 1);
    String illegalBase64Token = key + "$" + TEXT;
    CheckTokenException thrown =
        assertThrows(
            CheckTokenException.class, () -> signedToken.checkToken(illegalBase64Token, TEXT));

    assertThat(thrown).hasMessageThat().isEqualTo("Base64 decoding failed");
  }

  /** Test check token: token is illegal with a forged key */
  @Test
  public void checkTokenForgedKey() throws Exception {
    CheckTokenException thrown =
        assertThrows(CheckTokenException.class, () -> signedToken.checkToken(FORGED_TOKEN, TEXT));

    assertThat(thrown).hasMessageThat().isEqualTo("Token length mismatch");
  }

  /** Test check token: token is illegal with a forged text */
  @Test
  public void checkTokenForgedText() throws Exception {
    CheckTokenException thrown =
        assertThrows(
            CheckTokenException.class,
            () -> {
              String token = signedToken.newToken(TEXT);
              signedToken.checkToken(token, FORGED_TEXT);
            });

    assertThat(thrown).hasMessageThat().isEqualTo("Token text mismatch");
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
