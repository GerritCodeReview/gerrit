// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.Resources;
import com.google.gerrit.common.Nullable;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class IndexServlet extends HttpServlet {
  private SoyTofu.Renderer renderer;

  protected final Provider<String> canonicalURLProvider;
  protected final String cdnPrefix;

  IndexServlet(final Provider<String> canonicalURLProvider, @Nullable String cdnPrefix)
      throws ServletException {
    this.canonicalURLProvider = canonicalURLProvider;
    this.cdnPrefix = cdnPrefix;
    initRenderer();
  }

  protected void initRenderer() throws ServletException {
    String resourcePath = "com/google/gerrit/httpd/raw/index.html.soy";
    SoyFileSet.Builder builder = SoyFileSet.builder();
    builder.add(Resources.getResource(resourcePath));
    renderer = builder.build().compileToTofu()
        .newRenderer("com.google.gerrit.httpd.raw.Index")
        .setContentKind(SanitizedContent.ContentKind.HTML)
        .setData(new SoyMapData("assetPrefix", computeAssetPrefix()));
  }

  @Override
  protected void doGet(final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    rsp.setCharacterEncoding(UTF_8.name());
    rsp.setContentType("text/html");
    try (PrintWriter w = rsp.getWriter()) {
      w.write(renderer.render());
    }
  }

  protected SanitizedContent computeAssetPrefix() throws ServletException {
    String prefix = "";

    if (cdnPrefix != null) {
      prefix = cdnPrefix;
    } else {
      // If we serving from a sub-directory rather than root, determine the path
      // from the cannonical web URL.
      try {
        URI uri = new URI(canonicalURLProvider.get());
        prefix = uri.getPath().replaceAll("/$", "");
      } catch (URISyntaxException e) {
        throw new ServletException("Couldn't parse canonical web URL", e);
      }
    }

    // TODO(wyatta): Upgrade this to use an appropriate safe URL type.
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(prefix,
        SanitizedContent.ContentKind.TRUSTED_RESOURCE_URI);
  }
}
