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

package com.google.gwtexpui.server;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Forces GWT resources to cache for a very long time.
 * <p>
 * GWT compiled JavaScript and ImageBundles can be cached indefinitely by a
 * browser and/or an edge proxy, as they never contain user-specific data and
 * are named by a unique checksum. If their content is ever modified then the
 * URL changes, so user agents would request a different resource. We force
 * these resources to have very long expiration times.
 * <p>
 * To use, add the following block to your <code>web.xml</code>:
 * 
 * <pre>
 * &lt;filter&gt;
 *     &lt;filter-name&gt;CacheControl&lt;/filter-name&gt;
 *     &lt;filter-class&gt;com.google.gwtexpui.server.CacheControlFilter&lt;/filter-class&gt;
 *   &lt;/filter&gt;
 *   &lt;filter-mapping&gt;
 *     &lt;filter-name&gt;CacheControl&lt;/filter-name&gt;
 *     &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *   &lt;/filter-mapping&gt;
 * </pre>
 */
public class CacheControlFilter implements Filter {
  public void init(final FilterConfig config) {
  }

  public void destroy() {
  }

  public void doFilter(final ServletRequest sreq, final ServletResponse srsp,
      final FilterChain chain) throws IOException, ServletException {
    final HttpServletRequest req = (HttpServletRequest) sreq;
    final HttpServletResponse rsp = (HttpServletResponse) srsp;
    final String pathInfo = pathInfo(req);

    if (cacheForever(pathInfo)) {
      final long now = System.currentTimeMillis();
      rsp.setHeader("Cache-Control", "max-age=31536000,public");
      rsp.setDateHeader("Expires", now + 31536000000L);
      rsp.setDateHeader("Date", now);

    } else if (nocache(pathInfo)) {
      rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
      rsp.setHeader("Pragma", "no-cache");
      rsp.setHeader("Cache-Control", "no-cache, must-revalidate");
      rsp.setDateHeader("Date", System.currentTimeMillis());
    }

    chain.doFilter(req, rsp);
  }

  private static boolean cacheForever(final String pathInfo) {
    if (pathInfo.endsWith(".cache.html")) {
      return true;
    } else if (pathInfo.endsWith(".cache.gif")) {
      return true;
    } else if (pathInfo.endsWith(".cache.png")) {
      return true;
    } else if (pathInfo.endsWith(".cache.css")) {
      return true;
    } else if (pathInfo.endsWith(".cache.jar")) {
      return true;
    } else if (pathInfo.endsWith(".cache.swf")) {
      return true;
    }
    return false;
  }

  private static boolean nocache(final String pathInfo) {
    if (pathInfo.endsWith(".nocache.js")) {
      return true;
    }
    return false;
  }

  private static String pathInfo(final HttpServletRequest req) {
    final String uri = req.getRequestURI();
    final String ctx = req.getContextPath();
    return uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
  }
}
