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

package com.google.gerrit.server.project;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.AccountGroup.UUID;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.ContributorAgreement;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.PermissionRule.Action;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Singleton
public class ContributorAgreementsChecker {

  private final DynamicItem<UrlFormatter> urlFormatter;
  private final ProjectCache projectCache;
  private final Metrics metrics;

  @Singleton
  protected static class Metrics {
    final Counter0 claCheckCount;

    @Inject
    Metrics(MetricMaker metricMaker) {
      claCheckCount =
          metricMaker.newCounter(
              "license/cla_check_count",
              new Description("Total number of CLA check requests").setRate().setUnit("requests"));
    }
  }

  @Inject
  ContributorAgreementsChecker(
      DynamicItem<UrlFormatter> urlFormatter, ProjectCache projectCache, Metrics metrics) {
    this.urlFormatter = urlFormatter;
    this.projectCache = projectCache;
    this.metrics = metrics;
  }

  /**
   * Checks if the user has signed a contributor agreement for the project.
   *
   * @throws AuthException if the user has not signed a contributor agreement for the project
   * @throws IOException if project states could not be loaded
   */
  public void check(Project.NameKey project, CurrentUser user) throws IOException, AuthException {
    metrics.claCheckCount.increment();

    ProjectState projectState =
        projectCache.get(project).orElseThrow(() -> new IOException("Can't load " + project));
    if (!projectState.is(BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS)) {
      return;
    }

    if (!user.isIdentifiedUser()) {
      throw new AuthException("Must be logged in to verify Contributor Agreement");
    }

    IdentifiedUser iUser = user.asIdentifiedUser();
    Collection<ContributorAgreement> contributorAgreements =
        projectCache.getAllProjects().getConfig().getContributorAgreements().values();
    List<UUID> okGroupIds = new ArrayList<>();
    for (ContributorAgreement ca : contributorAgreements) {
      List<AccountGroup.UUID> groupIds;
      groupIds = okGroupIds;

      // matchProjects defaults to match all projects when missing.
      List<String> matchProjectsRegexes = ca.getMatchProjectsRegexes();
      if (!matchProjectsRegexes.isEmpty()
          && !projectMatchesAnyPattern(project.get(), matchProjectsRegexes)) {
        // Doesn't match, isn't checked.
        continue;
      }
      // excludeProjects defaults to exclude no projects when missing.
      List<String> excludeProjectsRegexes = ca.getExcludeProjectsRegexes();
      if (!excludeProjectsRegexes.isEmpty()
          && projectMatchesAnyPattern(project.get(), excludeProjectsRegexes)) {
        // Matches, isn't checked.
        continue;
      }
      for (PermissionRule rule : ca.getAccepted()) {
        if ((rule.getAction() == Action.ALLOW)
            && (rule.getGroup() != null)
            && (rule.getGroup().getUUID() != null)) {
          groupIds.add(AccountGroup.uuid(rule.getGroup().getUUID().get()));
        }
      }
    }

    if (!okGroupIds.isEmpty() && !iUser.getEffectiveGroups().containsAnyOf(okGroupIds)) {
      final StringBuilder msg = new StringBuilder();
      msg.append("No Contributor Agreement on file for user ")
          .append(iUser.getNameEmail())
          .append(" (id=")
          .append(iUser.getAccountId())
          .append(")");

      msg.append(urlFormatter.get().getSettingsUrl("Agreements").orElse(""));
      throw new AuthException(msg.toString());
    }
  }

  private boolean projectMatchesAnyPattern(String projectName, List<String> regexes) {
    requireNonNull(regexes);
    checkArgument(!regexes.isEmpty());
    for (String patternString : regexes) {
      Pattern pattern;
      try {
        pattern = Pattern.compile(patternString);
      } catch (PatternSyntaxException e) {
        // Should never happen: Regular expressions validated when reading project.config.
        throw new IllegalStateException(
            "Invalid matchProjects or excludeProjects clause in project.config", e);
      }
      if (pattern.matcher(projectName).find()) {
        return true;
      }
    }
    return false;
  }
}
