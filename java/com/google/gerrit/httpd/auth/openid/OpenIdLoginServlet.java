// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.openid;

import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Handles the {@code /OpenID} URL for web based single-sign-on. */
@SuppressWarnings("serial")
@Singleton
class OpenIdLoginServlet extends HttpServlet {
  private final OpenIdServiceImpl impl;

  @Inject
  OpenIdLoginServlet(OpenIdServiceImpl i) {
    impl = i;
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    doPost(req, rsp);
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    try {
      CacheHeaders.setNotCacheable(rsp);
      impl.doAuth(req, rsp);
    } catch (Exception e) {
      getServletContext().log("Unexpected error during authentication", e);
      rsp.reset();
      rsp.sendError(500);
    }
  }
}
