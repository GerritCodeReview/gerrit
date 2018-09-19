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
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.base.Strings;
import com.google.common.io.Resources;
import com.google.gerrit.common.Nullable;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.tofu.SoyTofu;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class IndexServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  protected final byte[] indexSource;

  IndexServlet(
      @Nullable String canonicalURL, @Nullable String cdnPath, @Nullable String faviconPath)
      throws URISyntaxException {
    String resourcePath = "com/google/gerrit/httpd/raw/PolyGerritIndexHtml.soy";
    SoyFileSet.Builder builder = SoyFileSet.builder();
    builder.add(Resources.getResource(resourcePath));
    SoyTofu.Renderer renderer =
        builder
            .build()
            .compileToTofu()
            .newRenderer("com.google.gerrit.httpd.raw.Index")
            .setContentKind(SanitizedContent.ContentKind.HTML)
            .setData(getTemplateData(canonicalURL, cdnPath, faviconPath));
    indexSource = renderer.render().getBytes(UTF_8);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    rsp.setCharacterEncoding(UTF_8.name());
    rsp.setContentType("text/html");
    rsp.setStatus(SC_OK);
    try (OutputStream w = rsp.getOutputStream()) {
      w.write(indexSource);
    }
  }

  static String computeCanonicalPath(@Nullable String canonicalURL) throws URISyntaxException {
    if (Strings.isNullOrEmpty(canonicalURL)) {
      return "";
    }

    // If we serving from a sub-directory rather than root, determine the path
    // from the cannonical web URL.
    URI uri = new URI(canonicalURL);
    return uri.getPath().replaceAll("/$", "");
  }

  static SoyMapData getTemplateData(String canonicalURL, String cdnPath, String faviconPath)
      throws URISyntaxException {
    String canonicalPath = computeCanonicalPath(canonicalURL);

    String staticPath = "";
    if (cdnPath != null) {
      staticPath = cdnPath;
    } else if (canonicalPath != null) {
      staticPath = canonicalPath;
    }

    // The resource path must be typed as safe for use in a script src.
    // TODO(wyatta): Upgrade this to use an appropriate safe URL type.
    SanitizedContent sanitizedStaticPath =
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            staticPath, SanitizedContent.ContentKind.TRUSTED_RESOURCE_URI);

    return new SoyMapData(
        "canonicalPath", canonicalPath,
        "staticResourcePath", sanitizedStaticPath,
        "faviconPath", faviconPath);
  }
}
