// Copyright (C) 2019 The Android Open Source Project
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
import static java.util.stream.Collectors.toSet;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.json.OutputFormat;
import com.google.gson.Gson;
import com.google.template.soy.data.SanitizedContent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helper for generating parts of {@code index.html}. */
@UsedAt(Project.GOOGLE)
public class IndexHtmlUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Gson GSON = OutputFormat.JSON_COMPACT.newGson();
  /**
   * Returns both static and dynamic parameters of {@code index.html}. The result is to be used when
   * rendering the soy template.
   */
  public static ImmutableMap<String, Object> templateData(
      GerritApi gerritApi,
      String canonicalURL,
      String cdnPath,
      String faviconPath,
      Map<String, String[]> urlParameterMap,
      Function<String, SanitizedContent> urlInScriptTagOrdainer,
      String requestedURL)
      throws URISyntaxException, RestApiException {
    ImmutableMap.Builder<String, Object> data = ImmutableMap.builder();
    data.putAll(
            staticTemplateData(
                canonicalURL,
                cdnPath,
                faviconPath,
                urlParameterMap,
                urlInScriptTagOrdainer,
                requestedURL))
        .putAll(dynamicTemplateData(gerritApi));

    Set<String> enabledExperiments = experimentData(urlParameterMap);
    if (!enabledExperiments.isEmpty()) {
      data.put("enabledExperiments", serializeObject(GSON, enabledExperiments));
    }
    return data.build();
  }

  /** Returns dynamic parameters of {@code index.html}. */
  public static ImmutableMap<String, Object> dynamicTemplateData(GerritApi gerritApi)
      throws RestApiException {
    ImmutableMap.Builder<String, Object> data = ImmutableMap.builder();
    Map<String, SanitizedContent> initialData = new HashMap<>();
    Server serverApi = gerritApi.config().server();
    initialData.put("\"/config/server/info\"", serializeObject(GSON, serverApi.getInfo()));
    initialData.put("\"/config/server/version\"", serializeObject(GSON, serverApi.getVersion()));
    initialData.put("\"/config/server/top-menus\"", serializeObject(GSON, serverApi.topMenus()));

    try {
      AccountApi accountApi = gerritApi.accounts().self();
      initialData.put("\"/accounts/self/detail\"", serializeObject(GSON, accountApi.get()));
      initialData.put(
          "\"/accounts/self/preferences\"", serializeObject(GSON, accountApi.getPreferences()));
      initialData.put(
          "\"/accounts/self/preferences.diff\"",
          serializeObject(GSON, accountApi.getDiffPreferences()));
      initialData.put(
          "\"/accounts/self/preferences.edit\"",
          serializeObject(GSON, accountApi.getEditPreferences()));
      data.put("userIsAuthenticated", true);
    } catch (AuthException e) {
      logger.atFine().log("Can't inline account-related data because user is unauthenticated");
      // Don't render data
    }

    data.put("gerritInitialData", initialData);
    return data.build();
  }

  /** Returns experimentData to be used in {@code index.html}. */
  public static Set<String> experimentData(Map<String, String[]> urlParameterMap) {
    // Allow enable experiments with url
    // ?experiment=a&experiment=b should result in:
    // "experiment" => [a,b]
    if (urlParameterMap.containsKey("experiment")) {
      return Arrays.asList(urlParameterMap.get("experiment")).stream().collect(toSet());
    }

    return Collections.emptySet();
  }

  /** Returns all static parameters of {@code index.html}. */
  static Map<String, Object> staticTemplateData(
      String canonicalURL,
      String cdnPath,
      String faviconPath,
      Map<String, String[]> urlParameterMap,
      Function<String, SanitizedContent> urlInScriptTagOrdainer,
      String requestedURL)
      throws URISyntaxException {
    String canonicalPath = computeCanonicalPath(canonicalURL);

    String staticPath = "";
    if (cdnPath != null) {
      staticPath = cdnPath;
    } else if (canonicalPath != null) {
      staticPath = canonicalPath;
    }

    SanitizedContent sanitizedStaticPath = urlInScriptTagOrdainer.apply(staticPath);
    ImmutableMap.Builder<String, Object> data = ImmutableMap.builder();

    if (canonicalPath != null) {
      data.put("canonicalPath", canonicalPath);
    }
    if (sanitizedStaticPath != null) {
      data.put("staticResourcePath", sanitizedStaticPath);
    }
    if (faviconPath != null) {
      data.put("faviconPath", faviconPath);
    }
    if (requestedURL != null) {
      String page = IndexPreloadingUtil.route(requestedURL);
      if (IndexPreloadingUtil.CHANGE_PAGE.equals(page)) {
        data.put("defaultChangeDetailHex", IndexPreloadingUtil.getDefaultChangeDetailHex());
        data.put("changeRequestsPath", IndexPreloadingUtil.computeChangeRequestsPath(requestedURL, page));
      } else if (IndexPreloadingUtil.DIFF_PAGE.equals(page)) {
        data.put("defaultDiffDetailHex", IndexPreloadingUtil.getDefaultDiffDetailHex());
        data.put("changeRequestsPath", IndexPreloadingUtil.computeChangeRequestsPath(requestedURL, page));
      } else if (IndexPreloadingUtil.DASHBOARD_PAGE.equals(page)) {
        data.put("defaultDashboardHex", IndexPreloadingUtil.getDefaultDashboardHex());
        data.put("dashboardQuery", IndexPreloadingUtil.computeDashboardQueryList());
      }
    }

    if (urlParameterMap.containsKey("ce")) {
      data.put("polyfillCE", "true");
    }
    if (urlParameterMap.containsKey("gf")) {
      data.put("useGoogleFonts", "true");
    }

    return data.build();
  }

  private static String computeCanonicalPath(@Nullable String canonicalURL)
      throws URISyntaxException {
    if (Strings.isNullOrEmpty(canonicalURL)) {
      return "";
    }

    // If we serving from a sub-directory rather than root, determine the path
    // from the cannonical web URL.
    URI uri = new URI(canonicalURL);
    return uri.getPath().replaceAll("/$", "");
  }

  private IndexHtmlUtil() {}
}
