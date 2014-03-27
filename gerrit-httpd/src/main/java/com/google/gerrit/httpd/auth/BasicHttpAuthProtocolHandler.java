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

import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

@Singleton
public class BasicHttpAuthProtocolHandler implements HttpAuthProtocolHandler {
  private static final String BASIC_AUTH_PREFIX = "Basic ";
  private static final String AUTHORIZATION = "Authorization";

  @Override
  public boolean canHandle(HttpServletRequest req) {
    String hdr = req.getHeader(AUTHORIZATION);
    return hdr != null && hdr.startsWith(BASIC_AUTH_PREFIX);
  }

  @Override
  public HttpAuthRequest handle(HttpServletRequest req, HttpServletResponse resp)
      throws AuthProtocolException {
    String hdr = req.getHeader(AUTHORIZATION);
    final byte[] decoded =
        Base64.decodeBase64(hdr.substring(BASIC_AUTH_PREFIX.length()));

    String usernamePassword;
    try {
      usernamePassword = new String(decoded, encoding(req));
    } catch (UnsupportedEncodingException e) {
      // The SC_UNAUTHORIZED error code will be set by the HttpAuthorizer.
      new Response(resp).status(HttpServletResponse.SC_UNAUTHORIZED);
      throw new AuthProtocolException("Invalid header: " + AUTHORIZATION + ": " + hdr, e);
    }

    String username, password;
    int splitPos = usernamePassword.indexOf(':');
    if (splitPos < 0) {
      username = usernamePassword;
      password = null;
    } else {
      username = usernamePassword.substring(0, splitPos);
      password = usernamePassword.substring(splitPos + 1);
    }

    return new HttpAuthRequest(username, password, req, new Response(resp));
  }

  private String encoding(HttpServletRequest req) {
    return Objects.firstNonNull(req.getCharacterEncoding(), "UTF-8");
  }

  private static class Response extends HttpServletResponseWrapper {
    private static final String LIT_BASIC = "Basic ";
    private static final String REALM_NAME = "Gerrit Code Review";
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    Response(HttpServletResponse resp) {
      super(resp);
    }

    private void status(int sc) {
      if (sc == SC_UNAUTHORIZED) {
        StringBuilder v = new StringBuilder();
        v.append(LIT_BASIC);
        v.append("realm=\"").append(REALM_NAME).append("\"");
        setHeader(WWW_AUTHENTICATE, v.toString());
      } else if (containsHeader(WWW_AUTHENTICATE)) {
        setHeader(WWW_AUTHENTICATE, null);
      }
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      status(sc);
      super.sendError(sc, msg);
    }

    @Override
    public void sendError(int sc) throws IOException {
      status(sc);
      super.sendError(sc);
    }

    @Override
    @Deprecated
    public void setStatus(int sc, String sm) {
      status(sc);
      super.setStatus(sc, sm);
    }

    @Override
    public void setStatus(int sc) {
      status(sc);
      super.setStatus(sc);
    }
  }
}
