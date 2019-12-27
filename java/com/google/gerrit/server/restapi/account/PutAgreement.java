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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.api.accounts.AgreementInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.AgreementSignup;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.restapi.group.AddMembers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * REST endpoint to sign a contributor agreement for an account.
 *
 * <p>This REST endpoint handles {@code PUT /accounts/<account-identifier>/agreements} requests.
 */
@Singleton
public class PutAgreement implements RestModifyView<AccountResource, AgreementInput> {
  private final ProjectCache projectCache;
  private final Provider<IdentifiedUser> self;
  private final AgreementSignup agreementSignup;
  private final AddMembers addMembers;
  private final boolean agreementsEnabled;

  @Inject
  PutAgreement(
      ProjectCache projectCache,
      Provider<IdentifiedUser> self,
      AgreementSignup agreementSignup,
      AddMembers addMembers,
      @GerritServerConfig Config config) {
    this.projectCache = projectCache;
    this.self = self;
    this.agreementSignup = agreementSignup;
    this.addMembers = addMembers;
    this.agreementsEnabled = config.getBoolean("auth", "contributorAgreements", false);
  }

  @Override
  public Response<String> apply(AccountResource resource, AgreementInput input)
      throws IOException, RestApiException, ConfigInvalidException {
    if (!agreementsEnabled) {
      throw new MethodNotAllowedException("contributor agreements disabled");
    }

    if (!self.get().hasSameAccountId(resource.getUser())) {
      throw new AuthException("not allowed to enter contributor agreement");
    }

    String agreementName = Strings.nullToEmpty(input.name);
    ContributorAgreement ca =
        projectCache.getAllProjects().getConfig().getContributorAgreement(agreementName);
    if (ca == null) {
      throw new UnprocessableEntityException("contributor agreement not found");
    }

    if (ca.getAutoVerify() == null) {
      throw new BadRequestException("cannot enter a non-autoVerify agreement");
    }

    AccountGroup.UUID uuid = ca.getAutoVerify().getUUID();
    if (uuid == null) {
      throw new ResourceConflictException("autoverify group uuid not found");
    }

    AccountState accountState = self.get().state();
    try {
      addMembers.addMembers(uuid, ImmutableSet.of(accountState.account().id()));
    } catch (NoSuchGroupException e) {
      throw new ResourceConflictException("autoverify group not found");
    }
    agreementSignup.fire(accountState, agreementName);

    return Response.ok(agreementName);
  }
}
