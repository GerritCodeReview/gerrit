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

import com.google.common.base.Objects;
import com.google.gerrit.server.auth.AuthRequest;

import org.apache.commons.codec.binary.Base64;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

/**
 * Username/password authentication on HTTP Basic Auth requests.
 *
 * Callers that are trying to instantiate this class should be
 * checking the authorization header up-front.
 */
public class HttpBasicAuthRequest extends AuthRequest {
  private static final String LIT_BASIC = "Basic ";
  private static final String AUTHORIZATION = "Authorization";

  private final String username;
  private final String password;
  private final HttpServletRequest httpClientRequest;

  public HttpServletRequest getHttpClientRequest() {
    return httpClientRequest;
  }

  public HttpBasicAuthRequest(HttpServletRequest req) throws IOException {
    this.httpClientRequest = req;

    final String hdr = req.getHeader(AUTHORIZATION);
    if (hdr == null) {
      username = password = null;
      return;
    }

    String usernamePassword =
        new String(Base64.decodeBase64(hdr.substring(LIT_BASIC.length())),
            Objects.firstNonNull(req.getCharacterEncoding(), "UTF-8"));
    int splitPos = usernamePassword.indexOf(':');
    if (splitPos < 1) {
      username = usernamePassword;
      password = null;
    } else {
      username = usernamePassword.substring(0, splitPos);
      password = usernamePassword.substring(splitPos + 1);
    }
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public String getPassword() {
    return password;
  }
}
