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

package com.google.gerrit.httpd.auth;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;

import com.google.common.base.Objects;
import com.google.gerrit.server.auth.Credentials;
import com.google.gerrit.server.auth.PasswordCredentials;

import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class BasicHttpAuthProtocol
    implements HttpAuthProtocol.CredentialsExtractor<PasswordCredentials>,
        HttpAuthProtocol.ProgrammaticLoginHandler {
  private static final String BASIC_AUTH_PREFIX = "Basic ";
  private static final String REALM_NAME = "Gerrit Code Review";

  @Override
  public PasswordCredentials extractCredentials(HttpServletRequest req)
      throws AuthProtocolException {
    final String hdr = req.getHeader(AUTHORIZATION);
    if (hdr == null || !hdr.startsWith(BASIC_AUTH_PREFIX)) {
      return null;
    }

    String usernamePassword;
    try {
      usernamePassword = new String(
          Base64.decodeBase64(hdr.substring(BASIC_AUTH_PREFIX.length())),
          Objects.firstNonNull(req.getCharacterEncoding(), "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new AuthProtocolException("Invalid header: " + AUTHORIZATION + ": " + hdr, e);
    }

    int splitPos = usernamePassword.indexOf(':');
    if (splitPos < 0) {
      return new PasswordCredentials(usernamePassword, null);
    } else {
      return new PasswordCredentials(
          usernamePassword.substring(0, splitPos),
          usernamePassword.substring(splitPos + 1));
    }
  }

  @Override
  public void loginProgrammatic(HttpServletRequest req,
      HttpServletResponse resp, @Nullable Credentials creds) {
    resp.setHeader(WWW_AUTHENTICATE, BASIC_AUTH_PREFIX + "realm=\"" + REALM_NAME + "\"");
  }
}
