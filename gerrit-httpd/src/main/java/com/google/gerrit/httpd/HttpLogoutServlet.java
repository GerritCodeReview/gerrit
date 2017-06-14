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

package com.google.gerrit.httpd;

import com.google.common.base.Strings;
import com.google.gerrit.audit.AuditEvent;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class HttpLogoutServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private final DynamicItem<WebSession> webSession;
  private final Provider<String> urlProvider;
  private final String logoutUrl;
  private final AuditService audit;

  @Inject
  protected HttpLogoutServlet(
      AuthConfig authConfig,
      DynamicItem<WebSession> webSession,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      AuditService audit) {
    this.webSession = webSession;
    this.urlProvider = urlProvider;
    this.logoutUrl = authConfig.getLogoutURL();
    this.audit = audit;
  }

  protected void doLogout(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    webSession.get().logout();
    if (logoutUrl != null) {
      rsp.sendRedirect(logoutUrl);
    } else {
      String url = urlProvider.get();
      if (Strings.isNullOrEmpty(url)) {
        url = req.getContextPath();
      }
      if (Strings.isNullOrEmpty(url)) {
        url = "/";
      }
      if (!url.endsWith("/")) {
        url += "/";
      }
      rsp.sendRedirect(url);
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {

    final String sid = webSession.get().getSessionId();
    final CurrentUser currentUser = webSession.get().getUser();
    final String what = "sign out";
    final long when = TimeUtil.nowMs();

    try {
      doLogout(req, rsp);
    } finally {
      audit.dispatch(new AuditEvent(sid, currentUser, what, when, null, null));
    }
  }
}
