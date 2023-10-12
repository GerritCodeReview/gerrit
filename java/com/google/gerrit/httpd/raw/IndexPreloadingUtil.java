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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.restapi.Url;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Helper for generating preloading parts of {@code index.html}. */
@UsedAt(Project.GOOGLE)
public class IndexPreloadingUtil {
  enum RequestedPage {
    CHANGE,
    DIFF,
    DASHBOARD,
    PROFILE,
    PAGE_WITHOUT_PRELOADING,
  }

  public static final String CHANGE_CANONICAL_PATH = "/c/(?<project>.+)/\\+/(?<changeNum>\\d+)";
  public static final String BASE_PATCH_NUM_PATH_PART = "(/(-?\\d+|edit)(\\.\\.(\\d+|edit))?)";
  public static final Pattern CHANGE_URL_PATTERN =
      Pattern.compile(CHANGE_CANONICAL_PATH + BASE_PATCH_NUM_PATH_PART + "?" + "/?$");
  public static final Pattern DIFF_URL_PATTERN =
      Pattern.compile(CHANGE_CANONICAL_PATH + BASE_PATCH_NUM_PATH_PART + "(/(.+))" + "/?$");
  public static final Pattern DASHBOARD_PATTERN = Pattern.compile("/dashboard/self$");
  public static final Pattern PROFILE_PATTERN = Pattern.compile("/profile/self$");
  public static final String ROOT_PATH = "/";

  // These queries should be kept in sync with PolyGerrit:
  // polygerrit-ui/app/elements/core/gr-navigation/gr-navigation.ts
  public static final String DASHBOARD_HAS_UNPUBLISHED_DRAFTS_QUERY = "has:draft limit:10";
  public static final String YOUR_TURN = "attention:${user} limit:25";
  public static final String DASHBOARD_WORK_IN_PROGRESS_QUERY =
      "is:open owner:${user} is:wip limit:25";
  public static final String DASHBOARD_OUTGOING_QUERY = "is:open owner:${user} -is:wip limit:25";
  public static final String DASHBOARD_INCOMING_QUERY =
      "is:open -owner:${user} -is:wip reviewer:${user} limit:25";
  public static final String CC_QUERY = "is:open -is:wip cc:${user} limit:10";
  public static final String DASHBOARD_RECENTLY_CLOSED_QUERY =
      "is:closed (-is:wip OR owner:self) "
          + "(owner:${user} OR reviewer:${user} OR cc:${user}) "
          + "-age:4w limit:10";
  public static final String NEW_USER = "owner:${user} limit:1";

  public static final String SELF_DASHBOARD_HAS_UNPUBLISHED_DRAFTS_QUERY =
      DASHBOARD_HAS_UNPUBLISHED_DRAFTS_QUERY.replaceAll("\\$\\{user}", "self");
  public static final String SELF_YOUR_TURN = YOUR_TURN.replaceAll("\\$\\{user}", "self");
  public static final ImmutableList<String> SELF_DASHBOARD_QUERIES =
      Stream.of(
              DASHBOARD_WORK_IN_PROGRESS_QUERY,
              DASHBOARD_OUTGOING_QUERY,
              DASHBOARD_INCOMING_QUERY,
              CC_QUERY,
              DASHBOARD_RECENTLY_CLOSED_QUERY,
              NEW_USER)
          .map(query -> query.replaceAll("\\$\\{user}", "self"))
          .collect(toImmutableList());
  public static final ImmutableSet<ListChangesOption> DASHBOARD_OPTIONS =
      ImmutableSet.of(
          ListChangesOption.LABELS,
          ListChangesOption.DETAILED_ACCOUNTS,
          ListChangesOption.SUBMIT_REQUIREMENTS,
          ListChangesOption.STAR);

  public static final ImmutableSet<ListChangesOption> CHANGE_DETAIL_OPTIONS =
      ImmutableSet.of(
          ListChangesOption.ALL_COMMITS,
          ListChangesOption.ALL_REVISIONS,
          ListChangesOption.CHANGE_ACTIONS,
          ListChangesOption.DETAILED_ACCOUNTS,
          ListChangesOption.DETAILED_LABELS,
          ListChangesOption.DOWNLOAD_COMMANDS,
          ListChangesOption.MESSAGES,
          ListChangesOption.SUBMITTABLE,
          ListChangesOption.WEB_LINKS,
          ListChangesOption.SKIP_DIFFSTAT,
          ListChangesOption.SUBMIT_REQUIREMENTS,
          ListChangesOption.PARENTS);

  @Nullable
  public static String getPath(@Nullable String requestedURL) throws URISyntaxException {
    if (requestedURL == null) {
      return null;
    }
    URI uri = new URI(requestedURL);
    return uri.getPath();
  }

  public static RequestedPage parseRequestedPage(@Nullable String requestedPath) {
    if (requestedPath == null) {
      return RequestedPage.PAGE_WITHOUT_PRELOADING;
    }

    Optional<String> changeRequestsPath =
        computeChangeRequestsPath(requestedPath, RequestedPage.CHANGE);
    if (changeRequestsPath.isPresent()) {
      return RequestedPage.CHANGE;
    }

    changeRequestsPath = computeChangeRequestsPath(requestedPath, RequestedPage.DIFF);
    if (changeRequestsPath.isPresent()) {
      return RequestedPage.DIFF;
    }

    Matcher dashboardMatcher = IndexPreloadingUtil.DASHBOARD_PATTERN.matcher(requestedPath);
    if (dashboardMatcher.matches()) {
      return RequestedPage.DASHBOARD;
    }

    Matcher profileMatcher = IndexPreloadingUtil.PROFILE_PATTERN.matcher(requestedPath);
    if (profileMatcher.matches()) {
      return RequestedPage.PROFILE;
    }

    if (ROOT_PATH.equals(requestedPath)) {
      return RequestedPage.DASHBOARD;
    }

    return RequestedPage.PAGE_WITHOUT_PRELOADING;
  }

  public static Optional<String> computeChangeRequestsPath(
      String requestedURL, RequestedPage page) {
    Matcher matcher;
    switch (page) {
      case CHANGE:
        matcher = CHANGE_URL_PATTERN.matcher(requestedURL);
        break;
      case DIFF:
        matcher = DIFF_URL_PATTERN.matcher(requestedURL);
        break;
      case DASHBOARD:
      case PROFILE:
      case PAGE_WITHOUT_PRELOADING:
      default:
        return Optional.empty();
    }

    if (matcher.matches()) {
      Integer changeId = Ints.tryParse(matcher.group("changeNum"));
      if (changeId != null) {
        return Optional.of("changes/" + Url.encode(matcher.group("project")) + "~" + changeId);
      }
    }
    return Optional.empty();
  }

  public static Optional<Integer> computeChangeNum(String requestedURL, RequestedPage page) {
    Matcher matcher;
    switch (page) {
      case CHANGE:
        matcher = CHANGE_URL_PATTERN.matcher(requestedURL);
        break;
      case DIFF:
        matcher = DIFF_URL_PATTERN.matcher(requestedURL);
        break;
      case DASHBOARD:
      case PROFILE:
      case PAGE_WITHOUT_PRELOADING:
      default:
        return Optional.empty();
    }

    if (matcher.matches()) {
      Integer changeNum = Ints.tryParse(matcher.group("changeNum"));
      if (changeNum != null) {
        return Optional.of(changeNum);
      }
    }
    return Optional.empty();
  }

  public static List<String> computeDashboardQueryList() {
    List<String> queryList = new ArrayList<>();
    queryList.add(SELF_DASHBOARD_HAS_UNPUBLISHED_DRAFTS_QUERY);
    queryList.add(SELF_YOUR_TURN);
    queryList.addAll(SELF_DASHBOARD_QUERIES);

    return queryList;
  }

  private IndexPreloadingUtil() {}
}
