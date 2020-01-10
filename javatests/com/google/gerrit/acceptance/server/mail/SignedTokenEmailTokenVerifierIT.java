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

package com.google.gerrit.acceptance.server.mail;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.server.mail.EmailTokenVerifier;
import com.google.gerrit.server.mail.EmailTokenVerifier.InvalidTokenException;
import com.google.gerrit.server.mail.SignedToken;
import com.google.gerrit.server.mail.SignedTokenEmailTokenVerifier;
import com.google.gerrit.testing.ConfigSuite;
import java.nio.charset.StandardCharsets;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class SignedTokenEmailTokenVerifierIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setString("auth", null, "registerEmailPrivateKey", SignedToken.generateRandomKey());
    return cfg;
  }

  private SignedTokenEmailTokenVerifier signedTokenEmailTokenVerifier;

  @Before
  public void setUp() throws Exception {
    signedTokenEmailTokenVerifier =
        server
            .getTestInjector()
            .getBinding(SignedTokenEmailTokenVerifier.class)
            .getProvider()
            .get();
  }

  /** Test encode */
  @Test
  public void encode() throws Exception {
    String tokenString = signedTokenEmailTokenVerifier.encode(user.id(), user.email());
    int index = tokenString.indexOf("$");
    String text = tokenString.substring(index + 1);
    String textDecoded = new String(BaseEncoding.base64Url().decode(text), StandardCharsets.UTF_8);
    int pos = textDecoded.indexOf(":");
    assertThat(textDecoded.substring(0, pos)).isEqualTo(user.id().toString());
    assertThat(textDecoded.substring(pos + 1)).isEqualTo(user.email());
  }

  /** Test decode */
  @Test
  public void decode() throws Exception {
    String tokenString = signedTokenEmailTokenVerifier.encode(user.id(), user.email());
    String tokenKey = tokenString.substring(0, tokenString.indexOf("$"));
    String text = user.id() + ":" + user.email();
    String invalidTokenString =
        tokenKey + "$" + BaseEncoding.base64Url().encode(text.getBytes(StandardCharsets.UTF_8));
    EmailTokenVerifier.ParsedToken parsedToken =
        signedTokenEmailTokenVerifier.decode(invalidTokenString);
    assertThat(parsedToken.getAccountId()).isEqualTo(user.id());
    assertThat(parsedToken.getEmailAddress()).isEqualTo(user.email());
  }

  /** Test token format is wrong(without '$' to split key and text) */
  @Test
  public void invalidFormat() throws Exception {
    InvalidTokenException thrown =
        assertThrows(
            InvalidTokenException.class,
            () -> signedTokenEmailTokenVerifier.decode("Invalid token"));
    assertThat(thrown)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("Token does not contain character '$'");
  }

  /** Test input token string is empty or null */
  @Test
  public void emptyInput() throws Exception {
    InvalidTokenException thrownWithNull =
        assertThrows(InvalidTokenException.class, () -> signedTokenEmailTokenVerifier.decode(null));
    InvalidTokenException thrownWithEmpty =
        assertThrows(InvalidTokenException.class, () -> signedTokenEmailTokenVerifier.decode(""));
    assertThat(thrownWithNull).hasCauseThat().hasMessageThat().isEqualTo("Empty token");
    assertThat(thrownWithEmpty).hasCauseThat().hasMessageThat().isEqualTo("Empty token");
  }

  /** Test token format is right but key is an illegal BASE64 string */
  @Test
  public void illegalTokenKey() throws Exception {
    InvalidTokenException thrown =
        assertThrows(
            InvalidTokenException.class,
            () -> signedTokenEmailTokenVerifier.decode("Illegal token key$...."));
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("Base64 decoding failed");
  }

  /** Test token text not match the required pattern */
  @Test
  public void tokenTextPatternMismatch() throws Exception {
    String tokenString = signedTokenEmailTokenVerifier.encode(user.id(), user.email());
    String tokenKey = tokenString.substring(0, tokenString.indexOf("$"));
    String pattern = user.id() + ":" + user.email();
    String invalidTokenTextPattern = tokenKey + "$" + pattern.replace(":", "");
    InvalidTokenException thrown =
        assertThrows(
            InvalidTokenException.class,
            () -> signedTokenEmailTokenVerifier.decode(invalidTokenTextPattern));
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("Token text mismatch");
  }
}
