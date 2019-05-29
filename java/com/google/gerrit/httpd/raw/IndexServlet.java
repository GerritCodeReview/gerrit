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

import com.google.common.io.Resources;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.tofu.SoyTofu;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.function.Function;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class IndexServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Nullable private final String canonicalUrl;
  @Nullable private final String cdnPath;
  @Nullable private final String faviconPath;
  private final GerritApi gerritApi;
  private final SoyTofu soyTofu;
  private final Function<String, SanitizedContent> urlOrdainer;

  IndexServlet(
      @Nullable String canonicalUrl,
      @Nullable String cdnPath,
      @Nullable String faviconPath,
      GerritApi gerritApi) {
    this.canonicalUrl = canonicalUrl;
    this.cdnPath = cdnPath;
    this.faviconPath = faviconPath;
    this.gerritApi = gerritApi;
    this.soyTofu =
        SoyFileSet.builder()
            .add(Resources.getResource("com/google/gerrit/httpd/raw/PolyGerritIndexHtml.soy"))
            .build()
            .compileToTofu();
    this.urlOrdainer =
        (s) ->
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                s, SanitizedContent.ContentKind.TRUSTED_RESOURCE_URI);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    SoyTofu.Renderer renderer;
    try {
      // TODO(hiesel): Remove URL ordainer as parameter once Soy is consistent
      renderer =
          soyTofu
              .newRenderer("com.google.gerrit.httpd.raw.Index")
              .setContentKind(SanitizedContent.ContentKind.HTML)
              .setData(
                  IndexHtmlUtil.templateData(
                      gerritApi, canonicalUrl, cdnPath, faviconPath, urlOrdainer));
    } catch (URISyntaxException | RestApiException e) {
      throw new IOException(e);
    }

    rsp.setCharacterEncoding(UTF_8.name());
    rsp.setContentType("text/html");
    rsp.setStatus(SC_OK);
    try (OutputStream w = rsp.getOutputStream()) {
      w.write(renderer.render().getBytes(UTF_8));
    }
  }
}
