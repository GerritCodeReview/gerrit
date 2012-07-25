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

import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.AbstractModule;

import org.eclipse.jgit.util.Base64;

import java.io.UnsupportedEncodingException;

/** Verifies the token sent by {@link RestApiServlet}. */
public class SignedTokenRestTokenVerifier implements RestTokenVerifier {
  private static final SignedToken restToken;

  static {
    SignedToken myToken;
    try {
      myToken = new SignedToken(60, SignedToken.generateRandomKey());
    } catch (XsrfException e) {
      myToken = null;
    }
    restToken = myToken;
  }

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(RestTokenVerifier.class).to(SignedTokenRestTokenVerifier.class);
    }
  }

  @Override
  public String encode(String user, String url) {
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

  public ParsedToken decode(String tokenString) throws InvalidTokenException {
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

    String user;
    try {
      user = payload.substring(0, colonPos);
    } catch (IllegalArgumentException err) {
      throw new InvalidTokenException(err);
    }

    String url = payload.substring(colonPos+1);
    return new ParsedToken(user, url);
  }
}
