// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.util.http.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Offers a dedicated endpoint for checking if a user is still logged in. Returns {@code 204
 * NO_CONTENT} for logged-in users, {@code 403 FORBIDDEN} otherwise.
 *
 * <p>Mainly used by PolyGerrit to check if a user is still logged in.
 */
@Singleton
public class AuthorizationCheckServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private final Provider<CurrentUser> user;

  @Inject
  AuthorizationCheckServlet(Provider<CurrentUser> user) {
    this.user = user;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    CacheHeaders.setNotCacheable(res);
    if (user.get().isIdentifiedUser()) {
      if (req.getRequestURI().endsWith(".svg")) {
        String responseToClient =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\"/>";
        res.setContentType("image/svg+xml");
        res.setCharacterEncoding(UTF_8.name());
        res.setStatus(HttpServletResponse.SC_OK);
        PrintWriter writer = res.getWriter();
        writer.write(responseToClient);
        writer.flush();
      } else {
        res.setContentLength(0);
        res.setStatus(HttpServletResponse.SC_NO_CONTENT);
      }
    } else {
      res.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
  }
}
