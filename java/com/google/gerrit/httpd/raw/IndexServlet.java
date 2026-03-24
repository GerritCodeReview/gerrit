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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.jbcsrc.api.SoySauce;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.function.Function;
import com.google.common.cache.Cache;
import java.util.concurrent.ExecutionException;
import com.google.gerrit.extensions.common.ServerInfo;
import java.util.List;
import com.google.gerrit.extensions.webui.TopMenu;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class IndexServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final String POLY_GERRIT_INDEX_HTML_SOY =
      "com/google/gerrit/httpd/raw/PolyGerritIndexHtml.soy";

  @Nullable private final String canonicalUrl;
  @Nullable private final String cdnPath;
  @Nullable private final String faviconPath;
  private final GerritApi gerritApi;
  private final ExperimentFeatures experimentFeatures;
  private final SoySauce soySauce;
  private final Function<String, SanitizedContent> urlOrdainer;
  private final Cache<String, ServerInfo> serverInfoCache;
  private final Cache<String, String> serverVersionCache;
  private final Cache<String, List<TopMenu.MenuEntry>> topMenusCache;

  IndexServlet(
      @Nullable String canonicalUrl,
      @Nullable String cdnPath,
      @Nullable String faviconPath,
      GerritApi gerritApi,
      ExperimentFeatures experimentFeatures,
      Cache<String, ServerInfo> serverInfoCache,
      Cache<String, String> serverVersionCache,
      Cache<String, List<TopMenu.MenuEntry>> topMenusCache) {
    this.canonicalUrl = canonicalUrl;
    this.cdnPath = cdnPath;
    this.faviconPath = faviconPath;
    this.gerritApi = gerritApi;
    this.experimentFeatures = experimentFeatures;
    this.serverInfoCache = serverInfoCache;
    this.serverVersionCache = serverVersionCache;
    this.topMenusCache = topMenusCache;
    this.soySauce =
        SoyFileSet.builder()
            .add(Resources.getResource(POLY_GERRIT_INDEX_HTML_SOY), POLY_GERRIT_INDEX_HTML_SOY)
            .build()
            .compileTemplates();
    this.urlOrdainer =
        (s) ->
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                s, SanitizedContent.ContentKind.TRUSTED_RESOURCE_URI);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    SoySauce.Renderer renderer;
    try {
      ServerInfo serverInfo = serverInfoCache.get("server_info", () -> gerritApi.config().server().getInfo());
      String serverVersion = serverVersionCache.get("server_version", () -> gerritApi.config().server().getVersion());
      List<TopMenu.MenuEntry> topMenus = topMenusCache.get("server_top_menus", () -> gerritApi.config().server().topMenus());

      Map<String, String[]> parameterMap = req.getParameterMap();
      // TODO(hiesel): Remove URL ordainer as parameter once Soy is consistent
      ImmutableMap<String, Object> templateData =
          IndexHtmlUtil.templateData(
              gerritApi,
              experimentFeatures,
              canonicalUrl,
              cdnPath,
              faviconPath,
              parameterMap,
              urlOrdainer,
              getRequestUrl(req),
              serverInfo,
              serverVersion,
              topMenus);
      renderer = soySauce.renderTemplate("com.google.gerrit.httpd.raw.Index").setData(templateData);
    } catch (URISyntaxException | RestApiException | ExecutionException e) {
      throw new IOException(e);
    }

    rsp.setCharacterEncoding(UTF_8.name());
    rsp.setContentType("text/html");
    rsp.setStatus(SC_OK);
    try (OutputStream w = rsp.getOutputStream()) {
      w.write(renderer.renderHtml().get().toString().getBytes(UTF_8));
    }
  }

  @SuppressWarnings("JdkObsolete")
  @Nullable
  private static String getRequestUrl(HttpServletRequest req) {
    if (req.getRequestURL() == null) {
      return null;
    }
    return req.getRequestURL().toString();
  }
}
