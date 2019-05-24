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

import static com.google.template.soy.data.ordainers.GsonOrdainer.serializeObject;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.base.Strings;
import com.google.common.io.Resources;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.json.OutputFormat;
import com.google.gson.Gson;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.tofu.SoyTofu;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class IndexServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Nullable private final String canonicalURL;
  @Nullable private final String cdnPath;
  @Nullable private final String faviconPath;
  private final GerritApi gerritApi;
  private final SoyTofu soyTofu;

  IndexServlet(
      @Nullable String canonicalURL,
      @Nullable String cdnPath,
      @Nullable String faviconPath,
      GerritApi gerritApi) {
    this.canonicalURL = canonicalURL;
    this.cdnPath = cdnPath;
    this.faviconPath = faviconPath;
    this.gerritApi = gerritApi;
    this.soyTofu =
        SoyFileSet.builder()
            .add(Resources.getResource("com/google/gerrit/httpd/raw/PolyGerritIndexHtml.soy"))
            .build()
            .compileToTofu();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    SoyTofu.Renderer renderer;
    try {
      SoyMapData templateData = getStaticTemplateData(canonicalURL, cdnPath, faviconPath);
      templateData.put("gerritInitialData", getInitialData());
      renderer =
          soyTofu
              .newRenderer("com.google.gerrit.httpd.raw.Index")
              .setContentKind(SanitizedContent.ContentKind.HTML)
              .setData(templateData);
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

  static String computeCanonicalPath(@Nullable String canonicalURL) throws URISyntaxException {
    if (Strings.isNullOrEmpty(canonicalURL)) {
      return "";
    }

    // If we serving from a sub-directory rather than root, determine the path
    // from the cannonical web URL.
    URI uri = new URI(canonicalURL);
    return uri.getPath().replaceAll("/$", "");
  }

  static SoyMapData getStaticTemplateData(String canonicalURL, String cdnPath, String faviconPath)
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

  private Map<String, SanitizedContent> getInitialData() throws RestApiException {
    Gson gson = OutputFormat.JSON_COMPACT.newGson();
    Map<String, SanitizedContent> initialData = new HashMap<>();
    Server serverApi = gerritApi.config().server();
    initialData.put("\"/config/server/info\"", serializeObject(gson, serverApi.getInfo()));
    initialData.put("\"/config/server/version\"", serializeObject(gson, serverApi.getVersion()));
    initialData.put("\"/config/server/top-menus\"", serializeObject(gson, serverApi.topMenus()));

    try {
      AccountApi accountApi = gerritApi.accounts().self();
      initialData.put("\"/accounts/self/detail\"", serializeObject(gson, accountApi.get()));
      initialData.put(
          "\"/accounts/self/preferences\"", serializeObject(gson, accountApi.getPreferences()));
      initialData.put(
          "\"/accounts/self/preferences.diff\"",
          serializeObject(gson, accountApi.getDiffPreferences()));
      initialData.put(
          "\"/accounts/self/preferences.edit\"",
          serializeObject(gson, accountApi.getEditPreferences()));
    } catch (AuthException e) {
      // Don't render data
      // TODO(hiesel): Tell the client that the user is not authenticated so that it doesn't have to
      // fetch anyway. This requires more client side modifications.
    }
    return initialData;
  }
}
