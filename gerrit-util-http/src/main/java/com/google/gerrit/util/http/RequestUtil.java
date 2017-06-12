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
// limitations under the License.

package com.google.gerrit.util.http;

import javax.servlet.http.HttpServletRequest;

/** Utilities for manipulating HTTP request objects. */
public class RequestUtil {
  /** HTTP request attribute for storing the Throwable that caused an error condition. */
  private static final String ATTRIBUTE_ERROR_TRACE =
      RequestUtil.class.getName() + "/ErrorTraceThrowable";

  public static void setErrorTraceAttribute(HttpServletRequest req, Throwable t) {
    req.setAttribute(ATTRIBUTE_ERROR_TRACE, t);
  }

  public static Throwable getErrorTraceAttribute(HttpServletRequest req) {
    return (Throwable) req.getAttribute(ATTRIBUTE_ERROR_TRACE);
  }

  /**
   * @return the same value as {@link HttpServletRequest#getPathInfo()}, but without decoding
   *     URL-encoded characters.
   */
  public static String getEncodedPathInfo(HttpServletRequest req) {
    // CS IGNORE LineLength FOR NEXT 3 LINES. REASON: URL.
    // Based on com.google.guice.ServletDefinition$1#getPathInfo() from:
    // https://github.com/google/guice/blob/41c126f99d6309886a0ded2ac729033d755e1593/extensions/servlet/src/com/google/inject/servlet/ServletDefinition.java
    String servletPath = req.getServletPath();
    int servletPathLength = servletPath.length();
    String requestUri = req.getRequestURI();
    String pathInfo =
        requestUri.substring(req.getContextPath().length()).replaceAll("[/]{2,}", "/");
    if (pathInfo.startsWith(servletPath)) {
      pathInfo = pathInfo.substring(servletPathLength);
      // Corner case: when servlet path & request path match exactly (without
      // trailing '/'), then pathinfo is null.
      if (pathInfo.isEmpty() && servletPathLength > 0) {
        pathInfo = null;
      }
    } else {
      pathInfo = null;
    }
    return pathInfo;
  }

  private RequestUtil() {}
}
