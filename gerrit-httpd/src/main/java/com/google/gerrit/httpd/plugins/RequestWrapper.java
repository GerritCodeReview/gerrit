package com.google.gerrit.httpd.plugins;

import com.google.common.base.Strings;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class RequestWrapper {
  private static final String PLUGINS_PREFIX = "/plugins/";
  static final String AUTHORIZED_PREFIX = "/a" + PLUGINS_PREFIX;
  private final String base;
  private final String authorizedBase;

  public RequestWrapper(String contextPath) {
    base = Strings.nullToEmpty(contextPath) + PLUGINS_PREFIX;
    authorizedBase = Strings.nullToEmpty(contextPath) + AUTHORIZED_PREFIX;
  }

  static boolean isAuthorizedCall(HttpServletRequest req) {
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

  private class WrappedRequest extends HttpServletRequestWrapper {
    private static final String SERVLET_PATH = "/";
    private final String contextPath;
    private final String pathInfo;

    private WrappedRequest(HttpServletRequest req, String contextPath) {
      super(req);
      this.contextPath = contextPath;
      this.pathInfo =
          getRequestURI().substring(
              contextPath.length() + SERVLET_PATH.length());
    }

    @Override
    public String getServletPath() {
      return SERVLET_PATH;
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
