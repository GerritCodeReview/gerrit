// Copyright (C) 2013 The Android Open Source Project
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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Utilities to manage HTTP caching directives in responses. */
public class CacheHeaders {
  private static final long MAX_CACHE_DURATION = DAYS.toSeconds(365);

  /**
   * Do not cache the response, anywhere.
   *
   * @param res response being returned.
   */
  public static void setNotCacheable(HttpServletResponse res) {
    String cc = "no-cache, no-store, max-age=0, must-revalidate";
    res.setHeader("Cache-Control", cc);
    res.setHeader("Pragma", "no-cache");
    res.setHeader("Expires", "Mon, 01 Jan 1990 00:00:00 GMT");
    res.setDateHeader("Date", System.currentTimeMillis());
  }

  /**
   * Permit caching the response for up to the age specified.
   *
   * <p>If the request is on a secure connection (e.g. SSL) private caching is used. This allows the
   * user-agent to cache the response, but requests intermediate proxies to not cache. This may
   * offer better protection for Set-Cookie headers.
   *
   * <p>If the request is on plaintext (insecure), public caching is used. This may allow an
   * intermediate proxy to cache the response, including any Set-Cookie header that may have also
   * been included.
   *
   * @param req current request.
   * @param res response being returned.
   * @param age how long the response can be cached.
   * @param unit time unit for age, usually {@link TimeUnit#SECONDS}.
   */
  public static void setCacheable(
      HttpServletRequest req, HttpServletResponse res, long age, TimeUnit unit) {
    setCacheable(req, res, age, unit, false);
  }

  /**
   * Permit caching the response for up to the age specified.
   *
   * <p>If the request is on a secure connection (e.g. SSL) private caching is used. This allows the
   * user-agent to cache the response, but requests intermediate proxies to not cache. This may
   * offer better protection for Set-Cookie headers.
   *
   * <p>If the request is on plaintext (insecure), public caching is used. This may allow an
   * intermediate proxy to cache the response, including any Set-Cookie header that may have also
   * been included.
   *
   * @param req current request.
   * @param res response being returned.
   * @param age how long the response can be cached.
   * @param unit time unit for age, usually {@link TimeUnit#SECONDS}.
   * @param mustRevalidate true if the client must validate the cached entity.
   */
  public static void setCacheable(
      HttpServletRequest req,
      HttpServletResponse res,
      long age,
      TimeUnit unit,
      boolean mustRevalidate) {
    if (req.isSecure()) {
      setCacheablePrivate(res, age, unit, mustRevalidate);
    } else {
      setCacheablePublic(res, age, unit, mustRevalidate);
    }
  }

  /**
   * Allow the response to be cached by proxies and user-agents.
   *
   * <p>If the response includes a Set-Cookie header the cookie may be cached by a proxy and
   * returned to multiple browsers behind the same proxy. This is insecure for authenticated
   * connections.
   *
   * @param res response being returned.
   * @param age how long the response can be cached.
   * @param unit time unit for age, usually {@link TimeUnit#SECONDS}.
   * @param mustRevalidate true if the client must validate the cached entity.
   */
  public static void setCacheablePublic(
      HttpServletResponse res, long age, TimeUnit unit, boolean mustRevalidate) {
    long now = System.currentTimeMillis();
    long sec = maxAgeSeconds(age, unit);

    res.setDateHeader("Expires", now + SECONDS.toMillis(sec));
    res.setDateHeader("Date", now);
    cache(res, "public", age, unit, mustRevalidate);
  }

  /**
   * Allow the response to be cached only by the user-agent.
   *
   * @param res response being returned.
   * @param age how long the response can be cached.
   * @param unit time unit for age, usually {@link TimeUnit#SECONDS}.
   * @param mustRevalidate true if the client must validate the cached entity.
   */
  public static void setCacheablePrivate(
      HttpServletResponse res, long age, TimeUnit unit, boolean mustRevalidate) {
    long now = System.currentTimeMillis();
    res.setDateHeader("Expires", now);
    res.setDateHeader("Date", now);
    cache(res, "private", age, unit, mustRevalidate);
  }

  public static boolean hasCacheHeader(HttpServletResponse res) {
    return res.containsHeader("Cache-Control") || res.containsHeader("Expires");
  }

  private static void cache(
      HttpServletResponse res, String type, long age, TimeUnit unit, boolean revalidate) {
    res.setHeader(
        "Cache-Control",
        String.format(
            "%s, max-age=%d%s",
            type, maxAgeSeconds(age, unit), revalidate ? ", must-revalidate" : ""));
  }

  private static long maxAgeSeconds(long age, TimeUnit unit) {
    return Math.min(unit.toSeconds(age), MAX_CACHE_DURATION);
  }

  private CacheHeaders() {}
}
