// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;

public class AllowRenderInFrameFilter extends AllRequestFilter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String X_FRAME_OPTIONS_HEADER_NAME = "X-Frame-Options";
  static final String CONTENT_SECURITY_POLICY_HEADER_NAME = "Content-Security-Policy";

  // Kept for back-compat mapping only; no longer used as a primary config.
  public static enum XFrameOption {
    ALLOW,
    SAMEORIGIN;
  }

  private final String cspFrameAncestors;
  private final String xFrameOptionsValue; // null means do not emit XFO

  @Inject
  public AllowRenderInFrameFilter(@GerritServerConfig Config cfg) {
    String[] frameAncestorsValues = cfg.getStringList("gerrit", null, "frameAncestors");

    List<String> origins;
    if (frameAncestorsValues.length > 0) {
      // New config wins outright; no deprecation warning.
      origins = Arrays.asList(frameAncestorsValues);
    } else if (cfg.getString("gerrit", null, "canLoadInIFrame") != null
        || cfg.getString("gerrit", null, "xframeOption") != null) {
      // Legacy keys explicitly set — map them and warn.
      boolean canLoadInIFrame = cfg.getBoolean("gerrit", "canLoadInIFrame", false);
      XFrameOption xframeOption =
          cfg.getEnum("gerrit", null, "xframeOption", XFrameOption.SAMEORIGIN);

      if (!canLoadInIFrame) {
        origins = List.of("'none'");
      } else if (xframeOption == XFrameOption.ALLOW) {
        origins = List.of("*");
      } else {
        origins = List.of("'self'");
      }

      logger.atWarning().log(
          "gerrit.canLoadInIFrame and gerrit.xframeOption are deprecated and will be removed in"
              + " the next release; migrate to gerrit.frameAncestors (resolved value: %s). See"
              + " Documentation/config-gerrit.txt for details.",
          String.join(" ", origins));
    } else {
      origins = List.of("'self'");
    }

    validateOrigins(origins);

    cspFrameAncestors = "frame-ancestors " + String.join(" ", origins);

    if (origins.equals(List.of("'self'"))) {
      xFrameOptionsValue = "SAMEORIGIN";
    } else if (origins.equals(List.of("'none'"))) {
      xFrameOptionsValue = "DENY";
    } else {
      xFrameOptionsValue = null;
    }
  }

  private static void validateOrigins(List<String> origins) {
    if (origins.isEmpty()) {
      throw new IllegalArgumentException("gerrit.frameAncestors must contain at least one value");
    }
    for (String origin : origins) {
      if (origin.isEmpty()) {
        throw new IllegalArgumentException("gerrit.frameAncestors contains an empty value");
      }
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    httpResponse.addHeader(CONTENT_SECURITY_POLICY_HEADER_NAME, cspFrameAncestors);
    if (xFrameOptionsValue != null) {
      httpResponse.addHeader(X_FRAME_OPTIONS_HEADER_NAME, xFrameOptionsValue);
    }
    chain.doFilter(request, httpResponse);
  }
}
