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
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;

@Singleton
public class AllowRenderInFrameFilter extends AllRequestFilter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  static final String X_FRAME_OPTIONS_HEADER_NAME = "X-Frame-Options";

  enum X_FRAME_OPTIONS {
    ALLOW,
    SAMEORIGIN,
    DENY;

    public static List<String> names() {
      return Arrays.stream(X_FRAME_OPTIONS.values()).map(Enum::name).collect(Collectors.toList());
    }
  }

  private X_FRAME_OPTIONS xframeOption;
  private boolean canLoadInIFrame;

  public static Module module() {
    return new AbstractModule() {

      @Override
      protected void configure() {
        DynamicSet.bind(binder(), AllRequestFilter.class)
            .to(AllowRenderInFrameFilter.class)
            .in(Scopes.SINGLETON);
      }
    };
  }

  @Inject
  public AllowRenderInFrameFilter(@GerritServerConfig Config cfg) {
    canLoadInIFrame = cfg.getBoolean("gerrit", "canLoadInIFrame", false);
    String frameOptionValue = cfg.getString("gerrit", null, "xframeOption");

    if (!canLoadInIFrame) {
      xframeOption = X_FRAME_OPTIONS.DENY;
    } else {
      try {
        xframeOption =
            Optional.ofNullable(frameOptionValue)
                .map(value -> X_FRAME_OPTIONS.valueOf(value.toUpperCase()))
                .orElse(X_FRAME_OPTIONS.ALLOW);

        if (X_FRAME_OPTIONS.DENY.equals(xframeOption)) {
          logger.atWarning().log(
              "xframeOption cannot be set to DENY when gerrit.canLoadInIFrame is set to true. Ignoring xframeOption.");
          xframeOption = X_FRAME_OPTIONS.ALLOW;
        }
      } catch (IllegalArgumentException e) {
        logger.atWarning().log(
            "Invalid xframeOption value, expected one of: [%s] but was: %s. xframeOption will be ignored.",
            String.join(",", X_FRAME_OPTIONS.names()), frameOptionValue);
        xframeOption = X_FRAME_OPTIONS.ALLOW;
      }
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (response instanceof HttpServletResponse && !X_FRAME_OPTIONS.ALLOW.equals(xframeOption)) {
      HttpServletResponse httpResponse = (HttpServletResponse) response;
      httpResponse.addHeader(X_FRAME_OPTIONS_HEADER_NAME, xframeOption.name());
      chain.doFilter(request, httpResponse);
    } else {
      chain.doFilter(request, response);
    }
  }
}
