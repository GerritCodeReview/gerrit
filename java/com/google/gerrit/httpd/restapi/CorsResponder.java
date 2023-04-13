// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.httpd.restapi;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_MAX_AGE;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.ORIGIN;
import static com.google.common.net.HttpHeaders.VARY;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.util.http.CacheHeaders;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;

/** Provides methods for processing CORS requests. */
public class CorsResponder {
  private static final String PLAIN_TEXT = "text/plain";
  private static final String X_GERRIT_AUTH = "X-Gerrit-Auth";
  private static final String X_REQUESTED_WITH = "X-Requested-With";

  static final ImmutableSet<String> ALLOWED_CORS_METHODS =
      ImmutableSet.of("GET", "HEAD", "POST", "PUT", "DELETE");
  private static final ImmutableSet<String> ALLOWED_CORS_REQUEST_HEADERS =
      Stream.of(AUTHORIZATION, CONTENT_TYPE, X_GERRIT_AUTH, X_REQUESTED_WITH)
          .map(s -> s.toLowerCase(Locale.US))
          .collect(ImmutableSet.toImmutableSet());

  private static boolean isCorsPreflight(HttpServletRequest req) {
    return "OPTIONS".equals(req.getMethod())
        && !Strings.isNullOrEmpty(req.getHeader(ORIGIN))
        && !Strings.isNullOrEmpty(req.getHeader(ACCESS_CONTROL_REQUEST_METHOD));
  }

  @Nullable
  public static Pattern makeAllowOrigin(Config cfg) {
    String[] allow = cfg.getStringList("site", null, "allowOriginRegex");
    if (allow.length > 0) {
      return Pattern.compile(Joiner.on('|').join(allow));
    }
    return null;
  }

  @Nullable private final Pattern allowOrigin;

  public CorsResponder(@Nullable Pattern allowOrigin) {
    this.allowOrigin = allowOrigin;
  }

  /**
   * Responses to a CORS preflight request.
   *
   * <p>If the request is a CORS preflight request, the method writes a correct preflight response
   * and returns true. A further processing of the request is not required. Otherwise, the method
   * returns false without adding anything to the response.
   */
  public boolean filterCorsPreflight(HttpServletRequest req, HttpServletResponse res)
      throws BadRequestException {
    if (!isCorsPreflight(req)) {
      return false;
    }
    doCorsPreflight(req, res);
    return true;
  }

  /**
   * Processes CORS request and add required headers to the response.
   *
   * <p>The method checks if the incoming request is a CORS request and if so validates the
   * request's origin.
   */
  public void checkCors(HttpServletRequest req, HttpServletResponse res, boolean isXd)
      throws BadRequestException {
    String origin = req.getHeader(ORIGIN);
    if (isXd) {
      // Cross-domain, non-preflighted requests must come from an approved origin.
      if (Strings.isNullOrEmpty(origin) || !isOriginAllowed(origin)) {
        throw new BadRequestException("origin not allowed");
      }
      res.addHeader(VARY, ORIGIN);
      res.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
      res.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    } else if (!Strings.isNullOrEmpty(origin)) {
      // All other requests must be processed, but conditionally set CORS headers.
      if (allowOrigin != null) {
        res.addHeader(VARY, ORIGIN);
      }
      if (isOriginAllowed(origin)) {
        setCorsHeaders(res, origin);
      }
    }
  }

  private void doCorsPreflight(HttpServletRequest req, HttpServletResponse res)
      throws BadRequestException {
    CacheHeaders.setNotCacheable(res);
    setHeaderList(
        res,
        VARY,
        ImmutableList.of(ORIGIN, ACCESS_CONTROL_REQUEST_METHOD, ACCESS_CONTROL_REQUEST_HEADERS));

    String origin = req.getHeader(ORIGIN);
    if (Strings.isNullOrEmpty(origin) || !isOriginAllowed(origin)) {
      throw new BadRequestException("CORS not allowed");
    }

    String method = req.getHeader(ACCESS_CONTROL_REQUEST_METHOD);
    if (!ALLOWED_CORS_METHODS.contains(method)) {
      throw new BadRequestException(method + " not allowed in CORS");
    }

    String headers = req.getHeader(ACCESS_CONTROL_REQUEST_HEADERS);
    if (headers != null) {
      for (String reqHdr : Splitter.on(',').trimResults().split(headers)) {
        if (!ALLOWED_CORS_REQUEST_HEADERS.contains(reqHdr.toLowerCase(Locale.US))) {
          throw new BadRequestException(reqHdr + " not allowed in CORS");
        }
      }
    }

    res.setStatus(SC_OK);
    setCorsHeaders(res, origin);
    res.setContentType(PLAIN_TEXT);
    res.setContentLength(0);
  }

  private static void setCorsHeaders(HttpServletResponse res, String origin) {
    res.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    res.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    res.setHeader(ACCESS_CONTROL_MAX_AGE, "600");
    setHeaderList(
        res,
        ACCESS_CONTROL_ALLOW_METHODS,
        Iterables.concat(ALLOWED_CORS_METHODS, ImmutableList.of("OPTIONS")));
    setHeaderList(res, ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_CORS_REQUEST_HEADERS);
  }

  private static void setHeaderList(HttpServletResponse res, String name, Iterable<String> values) {
    res.setHeader(name, Joiner.on(", ").join(values));
  }

  private boolean isOriginAllowed(String origin) {
    return allowOrigin != null && allowOrigin.matcher(origin).matches();
  }
}
