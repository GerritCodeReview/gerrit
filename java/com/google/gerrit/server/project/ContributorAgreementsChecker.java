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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.config.CanonicalWebUrl;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Singleton
public class ContributorAgreementsChecker {

  private final String canonicalWebUrl;
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
      @CanonicalWebUrl @Nullable String canonicalWebUrl,
      ProjectCache projectCache,
      Metrics metrics) {
    this.canonicalWebUrl = canonicalWebUrl;
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

    ProjectState projectState = projectCache.checkedGet(project);
    if (projectState == null) {
      throw new IOException("Can't load All-Projects");
    }

    if (!projectState.is(BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS)) {
      return;
    }

    if (!user.isIdentifiedUser()) {
      throw new AuthException("Must be logged in to verify Contributor Agreement");
    }

    IdentifiedUser iUser = user.asIdentifiedUser();
    Collection<ContributorAgreement> contributorAgreements =
        projectCache.getAllProjects().getConfig().getContributorAgreements();
    List<UUID> okGroupIds = new ArrayList<>();
    for (ContributorAgreement ca : contributorAgreements) {
      List<AccountGroup.UUID> groupIds;
      groupIds = okGroupIds;

      for (PermissionRule rule : ca.getAccepted()) {
        if ((rule.getAction() == Action.ALLOW)
            && (rule.getGroup() != null)
            && (rule.getGroup().getUUID() != null)) {
          groupIds.add(new AccountGroup.UUID(rule.getGroup().getUUID().get()));
        }
      }
    }

    if (!iUser.getEffectiveGroups().containsAnyOf(okGroupIds)) {
      final StringBuilder msg = new StringBuilder();
      msg.append("A Contributor Agreement must be completed before uploading");
      if (canonicalWebUrl != null) {
        msg.append(":\n\n  ");
        msg.append(canonicalWebUrl);
        msg.append("#");
        msg.append(PageLinks.SETTINGS_AGREEMENTS);
        msg.append("\n");
      } else {
        msg.append(".");
      }
      throw new AuthException(msg.toString());
    }
  }
}
