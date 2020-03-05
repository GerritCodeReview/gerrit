// Copyright 2020 Google Inc.
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
package com.google.gerrit.acceptance.server.mail;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.server.mail.EmailTokenVerifier;
import com.google.gerrit.server.mail.SignedToken;
import com.google.gerrit.server.mail.SignedTokenEmailTokenVerifier;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class SignedTokenEmailTokenVerifierIT extends AbstractDaemonTest {

  private TestAccount user;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setString("auth", null, "registerEmailPrivateKey", SignedToken.generateRandomKey());
    return cfg;
  }

  private SignedTokenEmailTokenVerifier signedTokenEmailTokenVerifier;

  @Before
  public void setUp() throws Exception {
    user = accountCreator.user();
    signedTokenEmailTokenVerifier =
        server
            .getTestInjector()
            .getBinding(SignedTokenEmailTokenVerifier.class)
            .getProvider()
            .get();
  }

  /** Test encode */
  @Test
  public void encodeTest() throws Exception {
    String tokenString = signedTokenEmailTokenVerifier.encode(user.id(), user.email());
    int index = tokenString.indexOf("$");
    String Key = tokenString.substring(0, index);
    String text = tokenString.substring(index + 1);
    String textDecoded = new String(BaseEncoding.base64Url().decode(text), StandardCharsets.UTF_8);
    int pos = textDecoded.indexOf(":");

    assertEquals(user.id().toString(), textDecoded.substring(0, pos));
    assertEquals(user.email(), textDecoded.substring(pos + 1));
  }

  /**
   * Test decode
   */
  @Test
  public void Test() throws Exception {
    String tokenString = signedTokenEmailTokenVerifier.encode(user.id(), user.email());
    String tokenKey = tokenString.substring(0, tokenString.indexOf("$"));

    String text = user.id() + ":" + user.email();
    String invalidTokenString = tokenKey + "$" + BaseEncoding.base64Url().encode(text.getBytes(StandardCharsets.UTF_8));
    EmailTokenVerifier.ParsedToken parsedToken = signedTokenEmailTokenVerifier.decode(invalidTokenString);

    assertEquals(user.id(), parsedToken.getAccountId());
    assertEquals(user.email(), parsedToken.getEmailAddress());
  }

  /**
   * Test token format is wrong(without '$' to split key and text)
   */
  @Test(expected = EmailTokenVerifier.InvalidTokenException.class)
  public void invalidFormatTest() throws Exception {
    String tokenStringWithoutDollar = "Invalid token";
    EmailTokenVerifier.ParsedToken parsedToken = signedTokenEmailTokenVerifier.decode(tokenStringWithoutDollar);
  }

  /**
   * Test token format is right but key is an illegal BASE64 string
   */
  @Test(expected = EmailTokenVerifier.InvalidTokenException.class)
  public void illegalTokenKeyTest() throws Exception {
    String tokenString = "Illegal token key$....";
    EmailTokenVerifier.ParsedToken parsedToken = signedTokenEmailTokenVerifier.decode(tokenString);
  }

  /**
   * Test token format and key is right but text is invalid
   */
  @Test(expected = EmailTokenVerifier.InvalidTokenException.class)
  public void illegalTokenTextTest() throws Exception {
    String tokenString = signedTokenEmailTokenVerifier.encode(user.id(), user.email());
    String tokenKey = tokenString.substring(0, tokenString.indexOf("$"));
    String invalidTokenString = tokenKey + "$Invalid token text, not BASE64";
    EmailTokenVerifier.ParsedToken parsedToken = signedTokenEmailTokenVerifier.decode(invalidTokenString);
  }

  @Inject
  protected AccountCreator accountCreator;
}
