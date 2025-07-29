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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

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

  IndexServlet(
      @Nullable String canonicalUrl,
      @Nullable String cdnPath,
      @Nullable String faviconPath,
      GerritApi gerritApi,
      ExperimentFeatures experimentFeatures) {
    this.canonicalUrl = canonicalUrl;
    this.cdnPath = cdnPath;
    this.faviconPath = faviconPath;
    this.gerritApi = gerritApi;
    this.experimentFeatures = experimentFeatures;
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
    ImmutableMap<String, Object> templateData;
    try {
      Map<String, String[]> parameterMap = req.getParameterMap();
      // TODO(hiesel): Remove URL ordainer as parameter once Soy is consistent
      templateData =
          IndexHtmlUtil.templateData(
              gerritApi,
              experimentFeatures,
              canonicalUrl,
              cdnPath,
              faviconPath,
              parameterMap,
              urlOrdainer,
              getRequestUrl(req));
    } catch (URISyntaxException | RestApiException e) {
      throw new IOException(e);
    }

    sendEarlyHints(req, templateData);

    SoySauce.Renderer renderer =
        soySauce.renderTemplate("com.google.gerrit.httpd.raw.Index").setData(templateData);

    rsp.setCharacterEncoding(UTF_8.name());
    rsp.setContentType("text/html");
    rsp.setStatus(SC_OK);
    try (OutputStream w = rsp.getOutputStream()) {
      w.write(renderer.renderHtml().get().toString().getBytes(UTF_8));
    }
  }

  private void addLink(List<String> links, String url, String as) {
    links.add(String.format("<%s>; rel=preload; as=%s", url, as));
  }

  private void addLinkWithCors(List<String> links, String url, String as) {
    links.add(String.format("<%s>; rel=preload; as=%s; crossorigin=anonymous", url, as));
  }

  private void sendEarlyHints(HttpServletRequest req, Map<String, Object> templateData) {
    List<String> links = new ArrayList<>();

    String canonicalPath = (String) templateData.get("canonicalPath");
    if (canonicalPath == null) {
      canonicalPath = "";
    }
    String staticResourcePath = (String) templateData.get("staticResourcePath");
    if (staticResourcePath == null) {
      staticResourcePath = "";
    }

    if (templateData.containsKey("changeRequestsPath")) {
      String changeRequestsPath = (String) templateData.get("changeRequestsPath");
      boolean userIsAuthenticated = (Boolean) templateData.getOrDefault("userIsAuthenticated", false);

      if (templateData.containsKey("defaultChangeDetailHex")) {
        String defaultChangeDetailHex = (String) templateData.get("defaultChangeDetailHex");
        addLinkWithCors(
            links,
            String.format(
                "%s/%s/detail?O=%s", canonicalPath, changeRequestsPath, defaultChangeDetailHex),
            "fetch");
        if (userIsAuthenticated) {
          addLinkWithCors(
              links,
              String.format("%s/%s/edit/?download-commands=true", canonicalPath, changeRequestsPath),
              "fetch");
        }
      }
      addLinkWithCors(
          links,
          String.format(
              "%s/%s/comments?enable-context=true&context-padding=3",
              canonicalPath, changeRequestsPath),
          "fetch");
      if (templateData.containsKey("changeNum")) {
        Object changeNum = templateData.get("changeNum");
        addLinkWithCors(
            links, String.format("%s/changes/?q=change:%s", canonicalPath, changeNum), "fetch");
      }
      if (userIsAuthenticated) {
        addLinkWithCors(
            links,
            String.format(
                "%s/%s/drafts?enable-context=true&context-padding=3",
                canonicalPath, changeRequestsPath),
            "fetch");
      }
    }

    boolean userIsAuthenticated = (Boolean) templateData.getOrDefault("userIsAuthenticated", false);
    if (userIsAuthenticated
        && templateData.containsKey("defaultDashboardHex")
        && templateData.containsKey("dashboardQuery")) {
      String defaultDashboardHex = (String) templateData.get("defaultDashboardHex");
      Object rawDashboardQuery = templateData.get("dashboardQuery");
      String queryParams = "";
      if (rawDashboardQuery instanceof List) {
        @SuppressWarnings("unchecked")
        List<String> dashboardQuery = (List<String>) rawDashboardQuery;
        queryParams = dashboardQuery.stream().map(q -> "&q=" + q).collect(Collectors.joining());
      }
      if (!queryParams.isEmpty()) {
        String url =
            String.format(
                "%s/changes/?O=%s&S=0%s&allow-incomplete-results=true",
                canonicalPath, defaultDashboardHex, queryParams);
        addLinkWithCors(links, url, "fetch");
      }
    }

    boolean useGoogleFonts = (Boolean) templateData.getOrDefault("useGoogleFonts", false);
    if (useGoogleFonts) {
      addLink(
          links,
          "https://fonts.googleapis.com/css?family=Roboto+Mono:400,500,700|Roboto:400,500,700|Open+Sans:400,500,600,700&display=swap",
          "style");
      addLink(
          links,
          "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0..1,0",
          "style");
    } else {
      String[] fonts = {
        "opensans-latin-400",
        "opensans-latin-500",
        "opensans-latin-600",
        "opensans-latin-700",
        "opensans-latin-ext-400",
        "opensans-latin-ext-500",
        "opensans-latin-ext-600",
        "opensans-latin-ext-700",
        "roboto-latin-400",
        "roboto-latin-500",
        "roboto-latin-700",
        "roboto-latin-ext-400",
        "roboto-latin-ext-500",
        "roboto-latin-ext-700",
        "roboto-mono-latin-400",
        "roboto-mono-latin-500",
        "roboto-mono-latin-700",
        "roboto-mono-latin-ext-400",
        "roboto-mono-latin-ext-500",
        "roboto-mono-latin-ext-700"
      };
      for (String font : fonts) {
        addLinkWithCors(links, String.format("%s/fonts/%s.woff2", staticResourcePath, font), "font");
      }
      addLinkWithCors(
          links, String.format("%s/fonts/material-icons.woff2", staticResourcePath), "font");
      addLink(links, String.format("%s/styles/fonts.css", staticResourcePath), "style");
      addLink(links, String.format("%s/styles/material-icons.css", staticResourcePath), "style");
    }
    addLink(links, String.format("%s/styles/main.css", staticResourcePath), "style");

    if (!links.isEmpty()) {
      Request jettyRequest = Request.getBaseRequest(req);
      if (jettyRequest != null) {
        Response jettyResponse = jettyRequest.getResponse();
        try {
          jettyResponse.sendEarlyHints(links);
        } catch (UnsupportedOperationException e) {
          // Ignored if not supported by the server.
        }
      }
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
