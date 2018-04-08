// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.container;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.CanonicalWebUrl;
import com.google.gerrit.httpd.LoginUrlToken;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet bound to {@code /login/*} to redirect after client SSL certificate login.
 *
 * <p>When using client SSL certificate one should normally never see the sign in dialog. However,
 * this will happen if users session gets invalidated in some way. Like in other authentication
 * types, we need to force page to fully reload in order to initialize a new session and create a
 * valid xsrfKey.
 */
@Singleton
public class HttpsClientSslCertLoginServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private final Provider<String> urlProvider;

  @Inject
  public HttpsClientSslCertLoginServlet(
      @CanonicalWebUrl @Nullable final Provider<String> urlProvider) {
    this.urlProvider = urlProvider;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    final StringBuilder rdr = new StringBuilder();
    rdr.append(urlProvider.get());
    rdr.append(LoginUrlToken.getToken(req));

    CacheHeaders.setNotCacheable(rsp);
    rsp.sendRedirect(rdr.toString());
  }
}
