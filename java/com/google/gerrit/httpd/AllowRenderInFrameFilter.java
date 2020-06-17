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

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;

public class AllowRenderInFrameFilter extends AllRequestFilter {
  static final String X_FRAME_OPTIONS_HEADER_NAME = "X-Frame-Options";

  public static enum XFrameOption {
    ALLOW,
    SAMEORIGIN;
  }

  private final String xframeOptionString;
  private final boolean skipXFrameOption;

  @Inject
  public AllowRenderInFrameFilter(@GerritServerConfig Config cfg) {
    XFrameOption xframeOption =
        cfg.getEnum("gerrit", null, "xframeOption", XFrameOption.SAMEORIGIN);
    skipXFrameOption = xframeOption.equals(XFrameOption.ALLOW);

    xframeOptionString =
        cfg.getBoolean("gerrit", "canLoadInIFrame", false) ? xframeOption.name() : "DENY";
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (skipXFrameOption) {
      chain.doFilter(request, response);
    } else {
      HttpServletResponse httpResponse = (HttpServletResponse) response;
      httpResponse.addHeader(X_FRAME_OPTIONS_HEADER_NAME, xframeOptionString);
      chain.doFilter(request, httpResponse);
    }
  }
}
