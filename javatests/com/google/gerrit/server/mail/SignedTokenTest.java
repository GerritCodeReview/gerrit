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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

public class SignedTokenTest {

  private static final String REGISTER_EMAIL_PRIVATE_KEY =
      "R2Vycml0JTIwcmVnaXN0ZXJFbWFpbFByaXZhdGVLZXk=";
  private static final int maxAge = 5;
  private static final String TEXT = "This is a text";
  private static final String FORGED_TEXT = "This is a forged text";
  private static final String FORGED_TOKEN = String.format("Zm9yZ2VkJTIwa2V5$%s", TEXT);

  private SignedToken signedToken;

  @Before
  public void setUp() throws Exception {
    signedToken = new SignedToken(maxAge, REGISTER_EMAIL_PRIVATE_KEY);
  }

  /** Test check token (BASE64 encoding and decoding in a safe URL way) */
  @Test
  public void checkTokenTest() throws XsrfException, CheckTokenException {
    String token = signedToken.newToken(TEXT);
    ValidToken validToken = signedToken.checkToken(token, TEXT);
    assertNotNull(validToken);
    assertEquals(TEXT, validToken.getData());
  }

  /** Test check token: input token is null */
  @Test(expected = CheckTokenException.class)
  public void checkTokenInputTokenNullTest() throws XsrfException, CheckTokenException {
    String token = signedToken.newToken(TEXT);
    signedToken.checkToken(null, TEXT);
  }

  /** Test check token: input token is empty */
  @Test(expected = CheckTokenException.class)
  public void checkTokenInputTokenEmptyTest() throws XsrfException, CheckTokenException {
    String token = signedToken.newToken(TEXT);
    signedToken.checkToken("", TEXT);
  }

  /** Test check token: token is not illegal with no '$' character */
  @Test(expected = CheckTokenException.class)
  public void checkTokenInputTokenNoDollarSplitorTest() throws XsrfException, CheckTokenException {
    String token = signedToken.newToken(TEXT);
    token = token.replace("$", "¥");
    signedToken.checkToken(token, TEXT);
  }

  /** Test check token: token is not illegal with BASE64 decoding error */
  @Test(expected = CheckTokenException.class)
  public void checkTokenInputTokenKeyBase64DecodeFailTest()
      throws XsrfException, CheckTokenException {
    String token = signedToken.newToken(TEXT);
    token = "A" + token;
    signedToken.checkToken(token, TEXT);
  }

  /** Test check token: token is not illegal with a forged key */
  @Test(expected = CheckTokenException.class)
  public void checkTokenForgedKeyTest() throws XsrfException, CheckTokenException {
    String token = signedToken.newToken(TEXT);
    signedToken.checkToken(FORGED_TOKEN, TEXT);
  }

  /** Test check token: token is not illegal with a forged text */
  @Test(expected = CheckTokenException.class)
  public void checkTokenForgedTextTest()
      throws XsrfException, CheckTokenException, InterruptedException {
    String token = signedToken.newToken(TEXT);
    signedToken.checkToken(token, FORGED_TEXT);
  }
}
