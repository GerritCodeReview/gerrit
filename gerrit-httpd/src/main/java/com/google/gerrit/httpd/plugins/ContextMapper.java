// Copyright (C) 2014 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.httpd.plugins;

package com.google.gerrit.httpd.plugins;

import com.google.common.base.Strings;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

class ContextMapper {
  private static final String PLUGINS_PREFIX = "/plugins/";
  private static final String AUTHORIZED_PREFIX = "/a" + PLUGINS_PREFIX;
  private final String base;
  private final String authorizedBase;

  ContextMapper(String contextPath) {
    base = Strings.nullToEmpty(contextPath) + PLUGINS_PREFIX;
    authorizedBase = Strings.nullToEmpty(contextPath) + AUTHORIZED_PREFIX;
  }

  private static boolean isAuthorizedCall(HttpServletRequest req) {
    return !Strings.isNullOrEmpty(req.getServletPath())
        && req.getServletPath().startsWith(AUTHORIZED_PREFIX);
  }

  HttpServletRequest create(HttpServletRequest req, String name) {
    String contextPath = (isAuthorizedCall(req) ? authorizedBase : base) + name;

    return new WrappedRequest(req, contextPath);
  }

  public String getFullPath(String name) {
    return base + name;
  }

  private static class WrappedRequest extends HttpServletRequestWrapper {
    private final String contextPath;
    private final String pathInfo;

    private WrappedRequest(HttpServletRequest req, String contextPath) {
      super(req);
      this.contextPath = contextPath;
      this.pathInfo = getRequestURI().substring(contextPath.length());
    }

    @Override
    public String getServletPath() {
      return "";
    }

    @Override
    public String getContextPath() {
      return contextPath;
    }

    @Override
    public String getPathInfo() {
      return pathInfo;
    }
  }
}
