// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.oauth;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.CanonicalWebUrl;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.HttpLogoutServlet;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.audit.AuditService;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class OAuthLogoutServlet extends HttpLogoutServlet {
  private static final long serialVersionUID = 1L;

  private final Provider<OAuthSession> oauthSession;

  @Inject
  OAuthLogoutServlet(
      AuthConfig authConfig,
      DynamicItem<WebSession> webSession,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      AuditService audit,
      Provider<OAuthSession> oauthSession) {
    super(authConfig, webSession, urlProvider, audit);
    this.oauthSession = oauthSession;
  }

  @Override
  protected void doLogout(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    super.doLogout(req, rsp);
    if (req.getSession(false) != null) {
      oauthSession.get().logout();
    }
  }
}
