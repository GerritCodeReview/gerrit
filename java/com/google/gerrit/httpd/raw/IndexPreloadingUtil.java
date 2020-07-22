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

package com.google.gerrit.httpd.raw;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Helper for generating preloading parts of {@code index.html}. */
@UsedAt(Project.GOOGLE)
public class IndexPreloadingUtil {
  public static final String CHANGE_PAGE = "changePage";
  public static final String DIFF_PAGE = "diffPage";
  public static final String DASHBOARD_PAGE = "dashboardPage";

  public static final String CHANGE_CANONICAL_URL = ".*/c/(?<project>.+)/\\+/(?<changeNum>\\d+)";
  public static final String BASE_PATCH_NUM_URL_PART = "(/(-?\\d+|edit)(\\.\\.(\\d+|edit))?)";
  public static final Pattern CHANGE_URL_PATTERN =
      Pattern.compile(CHANGE_CANONICAL_URL + BASE_PATCH_NUM_URL_PART + "?" + "/?$");
  public static final Pattern DIFF_URL_PATTERN =
      Pattern.compile(CHANGE_CANONICAL_URL + BASE_PATCH_NUM_URL_PART + "(/(.+))" + "/?$");
  public static final Pattern DASHBOARD_PATTERN = Pattern.compile(".*/dashboard/.+$");

  // These queries must be kept in sync with PolyGerrit:
  // polygerrit-ui/app/elements/change-list/gr-dashboard-view/gr-dashboard-view.js
  public static final String DASHBOARD_HAS_UNPUBLISHED_DRAFTS_QUERY = "has:draft limit:10";
  public static final String YOUR_TURN = "attention:${user} limit:25";
  public static final String DASHBOARD_ASSIGNED_QUERY =
      "assignee:${user} (-is:wip OR " + "owner:self OR assignee:self) is:open -is:ignored limit:25";
  public static final String DASHBOARD_WORK_IN_PROGRESS_QUERY =
      "is:open owner:${user} is:wip limit:25";
  public static final String DASHBOARD_OUTGOING_QUERY =
      "is:open owner:${user} -is:wip -is:ignored limit:25";
  public static final String DASHBOARD_INCOMING_QUERY =
      "is:open -owner:${user} -is:wip -is:ignored (reviewer:${user} OR assignee:${user}) limit:25";
  public static final String CC_QUERY = "is:open -is:ignored cc:${user} limit:10";
  public static final String DASHBOARD_RECENTLY_CLOSED_QUERY =
      "is:closed -is:ignored (-is:wip OR owner:self) "
          + "(owner:${user} OR reviewer:${user} OR assignee:${user} "
          + "OR cc:${user}) -age:4w limit:10";
  public static final String NEW_USER = "owner:${user} limit:1";

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

  public static String getDefaultDashboardHex(Server serverApi) throws RestApiException {
    Set<ListChangesOption> options = EnumSet.noneOf(ListChangesOption.class);
    options.add(ListChangesOption.LABELS);
    options.add(ListChangesOption.DETAILED_ACCOUNTS);

    if (isEnabledAttentionSet(serverApi)) {
      options.add(ListChangesOption.DETAILED_LABELS);
    } else {
      options.add(ListChangesOption.REVIEWED);
    }
    return ListOption.toHex(options);
  }

  public static String route(String requestedURL) {
    if (requestedURL == null) {
      return null;
    }

    String changeRequestsPath = computeChangeRequestsPath(requestedURL, CHANGE_PAGE);
    if (changeRequestsPath != null) {
      return CHANGE_PAGE;
    }

    changeRequestsPath = computeChangeRequestsPath(requestedURL, DIFF_PAGE);
    if (changeRequestsPath != null) {
      return DIFF_PAGE;
    }

    Matcher dashboardMatcher = IndexPreloadingUtil.DASHBOARD_PATTERN.matcher(requestedURL);
    if (dashboardMatcher.matches()) {
      return DASHBOARD_PAGE;
    }

    return null;
  }

  public static String computeChangeRequestsPath(String requestedURL, String page) {
    Matcher matcher;
    if (CHANGE_PAGE.equals(page)) {
      matcher = CHANGE_URL_PATTERN.matcher(requestedURL);
    } else if (DIFF_PAGE.equals(page)) {
      matcher = DIFF_URL_PATTERN.matcher(requestedURL);
    } else {
      return null;
    }

    if (matcher.matches()) {
      Integer changeId = Ints.tryParse(matcher.group("changeNum"));
      if (changeId != null) {
        return "changes/" + Url.encode(matcher.group("project")) + "~" + changeId;
      }
    }
    return null;
  }

  public static List<String> computeDashboardQueryList(Server serverApi) throws RestApiException {
    List<String> queryList = new ArrayList<>();
    queryList.add(DASHBOARD_HAS_UNPUBLISHED_DRAFTS_QUERY);
    if (isEnabledAttentionSet(serverApi)) {
      queryList.add(YOUR_TURN);
    }
    if (isEnabledAssignee(serverApi)) {
      queryList.add(DASHBOARD_ASSIGNED_QUERY);
    }

    queryList.addAll(
        Arrays.asList(
            DASHBOARD_WORK_IN_PROGRESS_QUERY,
            DASHBOARD_OUTGOING_QUERY,
            DASHBOARD_INCOMING_QUERY,
            CC_QUERY,
            DASHBOARD_RECENTLY_CLOSED_QUERY,
            NEW_USER));

    return queryList.stream()
        .map(query -> query.replaceAll("\\$\\{user}", "self"))
        .collect(Collectors.toList());
  }

  private static boolean isEnabledAttentionSet(Server serverApi) throws RestApiException {
    return serverApi.getInfo() != null
        && serverApi.getInfo().change != null
        && serverApi.getInfo().change.enableAttentionSet != null
        && serverApi.getInfo().change.enableAttentionSet;
  }

  private static boolean isEnabledAssignee(Server serverApi) throws RestApiException {
    return serverApi.getInfo() != null
        && serverApi.getInfo().change != null
        && serverApi.getInfo().change.enableAssignee != null
        && serverApi.getInfo().change.enableAssignee;
  }

  private IndexPreloadingUtil() {}
}
