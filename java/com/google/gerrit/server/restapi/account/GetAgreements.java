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

package com.google.gerrit.server.restapi.account;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.ContributorAgreement;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.PermissionRule.Action;
import com.google.gerrit.extensions.common.AgreementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.restapi.config.AgreementJson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.lib.Config;

/**
 * REST endpoint to get all contributor agreements that have been signed by an account.
 *
 * <p>This REST endpoint handles {@code GET /accounts/<account-identifier>/agreements} requests.
 *
 * <p>Contributor agreements are only available if contributor agreements have been enabled in
 * {@code gerrit.config} (see {@code auth.contributorAgreements}).
 */
@Singleton
public class GetAgreements implements RestReadView<AccountResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> self;
  private final ProjectCache projectCache;
  private final AgreementJson agreementJson;
  private final boolean agreementsEnabled;
  private final PermissionBackend permissionBackend;

  @Inject
  GetAgreements(
      Provider<CurrentUser> self,
      ProjectCache projectCache,
      AgreementJson agreementJson,
      PermissionBackend permissionBackend,
      @GerritServerConfig Config config) {
    this.self = self;
    this.projectCache = projectCache;
    this.agreementJson = agreementJson;
    this.agreementsEnabled = config.getBoolean("auth", "contributorAgreements", false);
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<List<AgreementInfo>> apply(AccountResource resource)
      throws RestApiException, PermissionBackendException {
    if (!agreementsEnabled) {
      throw new MethodNotAllowedException("contributor agreements disabled");
    }

    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("not allowed to get contributor agreements");
    }

    IdentifiedUser user = self.get().asIdentifiedUser();
    if (user != resource.getUser()) {
      try {
        permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
      } catch (AuthException e) {
        throw new AuthException("not allowed to get contributor agreements", e);
      }
    }

    List<AgreementInfo> results = new ArrayList<>();
    Collection<ContributorAgreement> cas =
        projectCache.getAllProjects().getConfig().getContributorAgreements().values();
    for (ContributorAgreement ca : cas) {
      List<AccountGroup.UUID> groupIds = new ArrayList<>();
      for (PermissionRule rule : ca.getAccepted()) {
        if ((rule.getAction() == Action.ALLOW) && (rule.getGroup() != null)) {
          if (rule.getGroup().getUUID() != null) {
            groupIds.add(rule.getGroup().getUUID());
          } else {
            logger.atWarning().log(
                "group \"%s\" does not exist, referenced in CLA \"%s\"",
                rule.getGroup().getName(), ca.getName());
          }
        }
      }

      if (user.getEffectiveGroups().containsAnyOf(groupIds)) {
        results.add(agreementJson.format(ca));
      }
    }
    return Response.ok(results);
  }
}
