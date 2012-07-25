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

package com.google.gerrit.httpd;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;

import org.eclipse.jgit.util.Base64;

import java.io.UnsupportedEncodingException;

/** Verifies the token sent by {@link RestApiServlet}. */
public class SignedTokenRestTokenVerifier implements RestTokenVerifier {
  private final SignedToken restToken;

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(RestTokenVerifier.class).to(SignedTokenRestTokenVerifier.class);
    }
  }

  @Inject
  SignedTokenRestTokenVerifier(AuthConfig config) {
    restToken = config.getRestToken();
  }

  @Override
  public String sign(Account.Id user, String url) {
    try {
      String payload = String.format("%s:%s", user, url);
      byte[] utf8 = payload.getBytes("UTF-8");
      String base64 = Base64.encodeBytes(utf8);
      return restToken.newToken(base64);
    } catch (XsrfException e) {
      throw new IllegalArgumentException(e);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public void verify(Account.Id user, String url, String tokenString)
      throws InvalidTokenException {
    ValidToken token;
    try {
      token = restToken.checkToken(tokenString, null);
    } catch (XsrfException err) {
      throw new InvalidTokenException(err);
    }
    if (token == null || token.getData() == null || token.getData().isEmpty()) {
      throw new InvalidTokenException();
    }

    String payload;
    try {
      payload = new String(Base64.decode(token.getData()), "UTF-8");
    } catch (UnsupportedEncodingException err) {
      throw new InvalidTokenException(err);
    }

    int colonPos = payload.indexOf(':');
    if (colonPos == -1) {
      throw new InvalidTokenException();
    }

    Account.Id tokenUser;
    try {
      tokenUser = Account.Id.parse(payload.substring(0, colonPos));
    } catch (IllegalArgumentException err) {
      throw new InvalidTokenException(err);
    }

    String tokenUrl = payload.substring(colonPos+1);

    if (!tokenUser.equals(user) || !tokenUrl.equals(url)) {
      throw new InvalidTokenException();
    }
  }
}
