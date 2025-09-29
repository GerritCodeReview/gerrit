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
import com.google.gerrit.httpd.raw.IndexPreloadingUtil.RequestedPage;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gson.Gson;
import com.google.template.soy.data.SanitizedContent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;

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
      ExperimentFeatures experimentFeatures,
      String canonicalURL,
      String cdnPath,
      String faviconPath,
      Map<String, String[]> urlParameterMap,
      Function<String, SanitizedContent> urlInScriptTagOrdainer,
      String requestedURL)
      throws URISyntaxException, RestApiException {
    ImmutableMap.Builder<String, Object> data = ImmutableMap.builder();
    boolean asyncSubmitRequirements =
        experimentFeatures.isFeatureEnabled(ExperimentFeaturesConstants.ASYNC_SUBMIT_REQUIREMENTS);
    data.putAll(
            staticTemplateData(
                canonicalURL, cdnPath, faviconPath, urlParameterMap, urlInScriptTagOrdainer))
        .putAll(
            dynamicTemplateData(gerritApi, requestedURL, canonicalURL, asyncSubmitRequirements));
    Set<String> enabledExperiments = new HashSet<>();
    enabledExperiments.addAll(experimentFeatures.getEnabledExperimentFeatures());
    // Add all experiments enabled through url
    enabledExperiments.addAll(IndexHtmlUtil.experimentData(urlParameterMap));
    if (!enabledExperiments.isEmpty()) {
      data.put("enabledExperiments", serializeObject(GSON, enabledExperiments).toString());
    }
    return data.build();
  }

  /**
   * Returns the basePatchNum that was specified in the URL when present. If no basePatchNum is
   * specified then it points to PARENT which is represented by 0
   */
  public static Integer computeBasePatchNum(@Nullable String requestedPath) {
    if (requestedPath == null) {
      return 0;
    }
    Matcher matcher = IndexPreloadingUtil.CHANGE_URL_PATTERN.matcher(requestedPath);
    String basePatchNum = null;
    if (matcher.matches()) {
      basePatchNum = matcher.group("basePatchNum");
    }
    if (basePatchNum == null) {
      return 0; // No match is found
    }
    Integer basePatchNumInt = Ints.tryParse(basePatchNum);
    if (basePatchNumInt == null) {
      return 0; // tryParse was unable to parse
    }
    return basePatchNumInt;
  }

  /** Returns dynamic parameters of {@code index.html}. */
  public static ImmutableMap<String, Object> dynamicTemplateData(
      GerritApi gerritApi,
      String requestedURL,
      String canonicalURL,
      boolean asyncSubmitRequirements)
      throws RestApiException, URISyntaxException {
    ImmutableMap.Builder<String, Object> data = ImmutableMap.builder();
    Map<String, SanitizedContent> initialData = new HashMap<>();
    Server serverApi = gerritApi.config().server();
    initialData.put(
        addCanonicalUrl("/config/server/info", canonicalURL),
        serializeObject(GSON, serverApi.getInfo()));
    initialData.put(
        addCanonicalUrl("/config/server/version", canonicalURL),
        serializeObject(GSON, serverApi.getVersion()));
    initialData.put(
        addCanonicalUrl("/config/server/top-menus", canonicalURL),
        serializeObject(GSON, serverApi.topMenus()));

    String requestedPath = IndexPreloadingUtil.getPath(requestedURL);
    IndexPreloadingUtil.RequestedPage page = IndexPreloadingUtil.parseRequestedPage(requestedPath);
    Integer basePatchNum = computeBasePatchNum(requestedPath);
    switch (page) {
      case CHANGE, DIFF -> {
        LinkedHashSet<ListChangesOption> changeDetailOptions =
            new LinkedHashSet<>(
                basePatchNum.equals(0)
                    ? IndexPreloadingUtil.CHANGE_DETAIL_OPTIONS_WITHOUT_PARENTS
                    : IndexPreloadingUtil.CHANGE_DETAIL_OPTIONS_WITH_PARENTS);
        if (asyncSubmitRequirements) {
          changeDetailOptions.remove(ListChangesOption.SUBMIT_REQUIREMENTS);
          changeDetailOptions.remove(ListChangesOption.SUBMITTABLE);
          data.put(
              "submitRequirementsHex",
              ListOption.toHex(
                  ImmutableSet.of(
                      ListChangesOption.SUBMIT_REQUIREMENTS, ListChangesOption.SUBMITTABLE)));
        }
        data.put("defaultChangeDetailHex", ListOption.toHex(changeDetailOptions));
        data.put(
            "changeRequestsPath",
            IndexPreloadingUtil.computeChangeRequestsPath(requestedPath, page).get());
        data.put("changeNum", IndexPreloadingUtil.computeChangeNum(requestedPath, page).get());
      }
      case PROFILE, DASHBOARD, PAGE_WITHOUT_PRELOADING -> {
        // Dashboard is preloaded queries are added later when we check user is
        // authenticated.
      }
    }

    try {
      AccountApi accountApi = gerritApi.accounts().self();
      initialData.put(
          addCanonicalUrl("/accounts/self/detail", canonicalURL),
          serializeObject(GSON, accountApi.get()));
      initialData.put(
          addCanonicalUrl("/accounts/self/preferences", canonicalURL),
          serializeObject(GSON, accountApi.getPreferences()));
      initialData.put(
          addCanonicalUrl("/accounts/self/preferences.diff", canonicalURL),
          serializeObject(GSON, accountApi.getDiffPreferences()));
      initialData.put(
          addCanonicalUrl("/accounts/self/preferences.edit", canonicalURL),
          serializeObject(GSON, accountApi.getEditPreferences()));
      data.put("userIsAuthenticated", true);
      if (page == RequestedPage.DASHBOARD) {
        data.put("defaultDashboardHex", ListOption.toHex(IndexPreloadingUtil.DASHBOARD_OPTIONS));
        data.put("dashboardQuery", IndexPreloadingUtil.computeDashboardQueryList());
      }
    } catch (AuthException e) {
      logger.atFine().log("Can't inline account-related data because user is unauthenticated");
      // Don't render data
    }

    data.put("gerritInitialData", initialData);
    return data.build();
  }

  private static String addCanonicalUrl(String key, String canonicalURL) throws URISyntaxException {
    String canonicalPath = computeCanonicalPath(canonicalURL);

    if (canonicalPath != null) {
      return String.format("\"%s\"", canonicalPath + key);
    }

    return String.format("\"%s\"", key);
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
  static ImmutableMap<String, Object> staticTemplateData(
      String canonicalURL,
      String cdnPath,
      String faviconPath,
      Map<String, String[]> urlParameterMap,
      Function<String, SanitizedContent> urlInScriptTagOrdainer)
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
