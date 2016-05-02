// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.extensions.common.AgreementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Singleton
public class GetAgreements implements RestReadView<AccountResource> {
  private static final Logger log =
      LoggerFactory.getLogger(GetAgreements.class);

  private final Provider<IdentifiedUser> self;
  private final ProjectCache projectCache;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final boolean agreementsEnabled;

  @Inject
  GetAgreements(Provider<IdentifiedUser> self,
      ProjectCache projectCache,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @GerritServerConfig Config config) {
    this.self = self;
    this.projectCache = projectCache;
    this.identifiedUserFactory = identifiedUserFactory;
    this.agreementsEnabled =
        config.getBoolean("auth", "contributorAgreements", false);
  }

  @Override
  public List<AgreementInfo> apply(AccountResource resource)
      throws RestApiException {
    if (!agreementsEnabled) {
      throw new MethodNotAllowedException("contributor agreements disabled");
    }

    if (self.get() != resource.getUser()) {
      throw new AuthException("not allowed to get contributor agreements");
    }

    IdentifiedUser user =
        identifiedUserFactory.create(self.get().getAccountId());

    List<AgreementInfo> results = new ArrayList<>();
    Collection<ContributorAgreement> cas =
        projectCache.getAllProjects().getConfig().getContributorAgreements();
    for (ContributorAgreement ca : cas) {
      List<AccountGroup.UUID> groupIds = new ArrayList<>();
      for (PermissionRule rule : ca.getAccepted()) {
        if ((rule.getAction() == Action.ALLOW) && (rule.getGroup() != null)) {
          if (rule.getGroup().getUUID() != null) {
            groupIds.add(rule.getGroup().getUUID());
          } else {
            log.warn("group \"" + rule.getGroup().getName() + "\" does not " +
                " exist, referenced in CLA \"" + ca.getName() + "\"");
          }
        }
      }

      if (user.getEffectiveGroups().containsAnyOf(groupIds)) {
        AgreementInfo info = new AgreementInfo();
        info.name = ca.getName();
        info.description = ca.getDescription();
        info.url = ca.getAgreementUrl();
        results.add(info);
      }
    }
    return results;
  }
}
