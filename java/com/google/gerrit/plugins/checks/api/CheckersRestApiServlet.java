// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.api;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class CheckersRestApiServlet extends RestApiServlet {
  private static final long serialVersionUID = 1L;

  @Inject
  CheckersRestApiServlet(RestApiServlet.Globals globals, Provider<CheckersCollection> checkers) {
    super(globals, checkers);
  }

  // TODO(dborowitz): Consider making
  // RestApiServlet#service(HttpServletRequest, HttpServletResponse) non-final of overriding the
  // non-HTTP overload.
  @Override
  public void service(ServletRequest servletRequest, ServletResponse servletResponse)
      throws ServletException, IOException {
    // This is...unfortunate. HttpPluginServlet (and/or ContextMapper) doesn't properly set the
    // servlet path on the wrapped request. Based on what RestApiServlet produces for non-plugin
    // requests, it should be:
    //   contextPath = "/plugins/checks"
    //   servletPath = "/checkers/"
    //   pathInfo = checkerUuid
    // Instead it does:
    //   contextPath = "/plugins/checks"
    //   servletPath = ""
    //   pathInfo = "/checkers/" + checkerUuid
    // This results in RestApiServlet splitting the pathInfo into ["", "checkers", checkerUuid], and
    // it passes the "" to CheckersCollection#parse, which understandably, but unfortunately, fails.
    //
    // This frankly seems like a bug that should be fixed, but it would quite likely break existing
    // plugins in confusing ways. So, we work around it by introducing our own request wrapper with
    // the correct paths.
    HttpServletRequest req = (HttpServletRequest) servletRequest;

    String pathInfo = req.getPathInfo();
    String correctServletPath = "/checkers/";

    // Ensure actual request object matches the format explained above.
    checkState(
        req.getContextPath().endsWith("/checks"),
        "unexpected context path: %s",
        req.getContextPath());
    checkState(req.getServletPath().isEmpty(), "unexpected servlet path: %s", req.getServletPath());
    checkState(
        req.getPathInfo().startsWith(correctServletPath),
        "unexpected servlet path: %s",
        req.getServletPath());

    String fixedPathInfo = pathInfo.substring(correctServletPath.length());
    HttpServletRequestWrapper wrapped =
        new HttpServletRequestWrapper(req) {
          @Override
          public String getServletPath() {
            return correctServletPath;
          }

          @Override
          public String getPathInfo() {
            return fixedPathInfo;
          }
        };

    super.service(wrapped, (HttpServletResponse) servletResponse);
  }
}
