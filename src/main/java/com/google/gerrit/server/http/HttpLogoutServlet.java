// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.http;

import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.Nullable;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class HttpLogoutServlet extends HttpServlet {
  private final Provider<WebSession> webSession;
  private final Provider<String> urlProvider;
  private final String logoutUrl;

  @Inject
  HttpLogoutServlet(final AuthConfig authConfig,
      final Provider<WebSession> webSession,
      @CanonicalWebUrl @Nullable final Provider<String> urlProvider,
      final AccountManager accountManager) {
    this.webSession = webSession;
    this.urlProvider = urlProvider;
    this.logoutUrl = authConfig.getLogoutURL();
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    webSession.get().logout();
    if (logoutUrl != null) {
      rsp.sendRedirect(logoutUrl);
    } else {
      rsp.sendRedirect(urlProvider.get());
    }
  }
}
