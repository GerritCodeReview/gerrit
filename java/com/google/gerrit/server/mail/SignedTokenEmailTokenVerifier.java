// Copyright (C) 2012 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.mail.send.RegisterNewEmailSender;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.util.Base64;

/** Verifies the token sent by {@link RegisterNewEmailSender}. */
@Singleton
public class SignedTokenEmailTokenVerifier implements EmailTokenVerifier {
  private final SignedToken emailRegistrationToken;

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(EmailTokenVerifier.class).to(SignedTokenEmailTokenVerifier.class);
    }
  }

  @Inject
  SignedTokenEmailTokenVerifier(AuthConfig config) {
    emailRegistrationToken = config.getEmailRegistrationToken();
  }

  @Override
  public String encode(Account.Id accountId, String emailAddress) {
    try {
      String payload = String.format("%s:%s", accountId, emailAddress);
      byte[] utf8 = payload.getBytes(UTF_8);
      String base64 = Base64.encodeBytes(utf8);
      return emailRegistrationToken.newToken(base64);
    } catch (XsrfException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public ParsedToken decode(String tokenString) throws InvalidTokenException {
    ValidToken token;
    try {
      token = emailRegistrationToken.checkToken(tokenString, null);
    } catch (XsrfException err) {
      throw new InvalidTokenException(err);
    }
    if (token == null || token.getData() == null || token.getData().isEmpty()) {
      throw new InvalidTokenException();
    }

    String payload = new String(Base64.decode(token.getData()), UTF_8);
    Matcher matcher = Pattern.compile("^([0-9]+):(.+@.+)$").matcher(payload);
    if (!matcher.matches()) {
      throw new InvalidTokenException();
    }

    Account.Id id;
    try {
      id = Account.Id.parse(matcher.group(1));
    } catch (IllegalArgumentException err) {
      throw new InvalidTokenException(err);
    }

    String newEmail = matcher.group(2);
    return new ParsedToken(id, newEmail);
  }
}
