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

  public static final String CHANGE_CANONICAL_URL = ".*/c/(?<project>.+)/\\+/(?<changeNum>\\d+)";
  public static final String BASE_PATCH_NUM_URL_PART = "(/(-?\\d+|edit)(\\.\\.(\\d+|edit))?)";
  public static final Pattern CHANGE_URL_PATTERN =
      Pattern.compile(CHANGE_CANONICAL_URL + BASE_PATCH_NUM_URL_PART + "?" + "/?$");
  public static final Pattern DIFF_URL_PATTERN =
      Pattern.compile(CHANGE_CANONICAL_URL + BASE_PATCH_NUM_URL_PART + "(/(.+))" + "/?$");
  public static final Pattern DASHBOARD_PATTERN = Pattern.compile(".*/dashboard/.+$");

  private static final Gson GSON = OutputFormat.JSON_COMPACT.newGson();

  public static String getDefaultChangeDetailHex() {
    Set<ListChangesOption> options =
        ImmutableSet.of(
            ListChangesOption.ALL_COMMITS,
            ListChangesOption.ALL_REVISIONS,
            ListChangesOption.CHANGE_ACTIONS,
            ListChangesOption.DETAILED_LABELS,
            ListChangesOption.DOWNLOAD_COMMANDS,
            ListChangesOption.MESSAGES,
            ListChangesOption.SUBMITTABLE,
            ListChangesOption.WEB_LINKS,
            ListChangesOption.SKIP_DIFFSTAT);

    return ListOption.toHex(options);
  }

  public static String getDefaultDiffDetailHex() {
    Set<ListChangesOption> options =
        ImmutableSet.of(
            ListChangesOption.ALL_COMMITS,
            ListChangesOption.ALL_REVISIONS,
            ListChangesOption.SKIP_DIFFSTAT);

    return ListOption.toHex(options);
  }

  public static String getDefaultDashboardHex() {
    Set<ListChangesOption> options =
        ImmutableSet.of(
            ListChangesOption.LABELS,
            ListChangesOption.DETAILED_ACCOUNTS,
            ListChangesOption.REVIEWED);
    //Todo add Attention
    // if (config && config.change && config.change.enable_attention_set) {
    //   options.push(ListChangesOption.DETAILED_LABELS);
    // } else {
    //   options.push(ListChangesOption.REVIEWED);
    // }

    return ListOption.toHex(options);
  }

  public static String computeChangeRequestsPath(String requestedURL, Pattern pattern) {
    Matcher matcher = pattern.matcher(requestedURL);
    if (matcher.matches()) {
      Integer changeId = Ints.tryParse(matcher.group("changeNum"));
      if (changeId != null) {
        return "changes/" + Url.encode(matcher.group("project")) + "~" + changeId;
      }
    }

    return null;
  }

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
      data.put("defaultChangeDetailHex", getDefaultChangeDetailHex());
      data.put("defaultDiffDetailHex", getDefaultDiffDetailHex());

      String changeRequestsPath = computeChangeRequestsPath(requestedURL, CHANGE_URL_PATTERN);
      if (changeRequestsPath != null) {
        data.put("preloadChangePage", "true");
      } else {
        changeRequestsPath = computeChangeRequestsPath(requestedURL, DIFF_URL_PATTERN);
        data.put("preloadDiffPage", "true");
      }

      if (changeRequestsPath != null) {
        data.put("changeRequestsPath", changeRequestsPath);
      } else {
        Matcher dashboardMatcher = DASHBOARD_PATTERN.matcher(requestedURL);
        if (dashboardMatcher.matches()) {
          data.put("defaultDashboardHex", getDefaultDashboardHex());
          data.put("dashboardQuery", computeDashboardQuery());
        }
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


  protected static final String DASHBOARD_HAS_UNPUBLISHED_DRAFTS_QUERY = "has:draft limit:10";
  protected static final String DASHBOARD_ASSIGNED_QUERY =
      "assignee:${user} (-is:wip OR " + "owner:self OR assignee:self) is:open -is:ignored limit:25";
  protected static final String DASHBOARD_WORK_IN_PROGRESS_QUERY = "is:open owner:${user} is:wip limit:25";
  protected static final String DASHBOARD_OUTGOING_QUERY =
      "is:open owner:${user} -is:wip -is:ignored limit:25";
  protected static final String DASHBOARD_INCOMING_QUERY =
      "is:open -owner:${user} -is:wip -is:ignored (reviewer:${user} OR assignee:${user}) limit:25";
  protected static final String CC_QUERY =
      "is:open -is:ignored cc:${user} limit:10";
  protected static final String DASHBOARD_RECENTLY_CLOSED_QUERY =
      "is:closed -is:ignored (-is:wip OR owner:self) "
          + "(owner:${user} OR reviewer:${user} OR assignee:${user} "
          + "OR cc:${user}) -age:4w limit:10";
  protected static final String NEW_USER =
          "owner:${user} limit:1"; 

  private static List<String> computeDashboardQuery() {
    List<String> list = new ArrayList<>();
    // &q=has:draft limit:10
    list.add(DASHBOARD_HAS_UNPUBLISHED_DRAFTS_QUERY);
    //&q=assignee:self (-is:wip OR owner:self OR assignee:self) is:open -is:ignored limit:25
    list.add(DASHBOARD_ASSIGNED_QUERY.replaceAll("\\$\\{user}", "self"));
    // // &q=is:open owner:self is:wip limit:25
    list.add(DASHBOARD_WORK_IN_PROGRESS_QUERY.replaceAll("\\$\\{user}", "self"));
    // // &q=is:open owner:self -is:wip -is:ignored limit:25
    list.add(DASHBOARD_OUTGOING_QUERY.replaceAll("\\$\\{user}", "self"));
    // // &q=is:open -owner:self -is:wip -is:ignored (reviewer:self OR assignee:self) limit:25
    list.add(DASHBOARD_INCOMING_QUERY.replaceAll("\\$\\{user}", "self"));
    // //&q=is:open -is:ignored cc:self limit:10
    list.add(CC_QUERY.replaceAll("\\$\\{user}", "self"));
    // //&q=is:closed -is:ignored (-is:wip OR owner:self) (owner:self OR reviewer:self OR assignee:self OR cc:self) -age:4w limit:10
    list.add(DASHBOARD_RECENTLY_CLOSED_QUERY.replaceAll("\\$\\{user}", "self"));
    // //&q=owner:self limit:1
    list.add(NEW_USER.replaceAll("\\$\\{user}", "self"));
    return list;
  }

  private IndexHtmlUtil() {}
}
