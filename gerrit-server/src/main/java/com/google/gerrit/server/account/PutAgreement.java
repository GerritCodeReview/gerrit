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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.extensions.common.AgreementInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.AgreementSignup;
import com.google.gerrit.server.group.AddMembers;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class PutAgreement implements RestModifyView<AccountResource, AgreementInput> {
  private final ProjectCache projectCache;
  private final GroupCache groupCache;
  private final Provider<IdentifiedUser> self;
  private final AgreementSignup agreementSignup;
  private final AddMembers addMembers;
  private final boolean agreementsEnabled;

  @Inject
  PutAgreement(
      ProjectCache projectCache,
      GroupCache groupCache,
      Provider<IdentifiedUser> self,
      AgreementSignup agreementSignup,
      AddMembers addMembers,
      @GerritServerConfig Config config) {
    this.projectCache = projectCache;
    this.groupCache = groupCache;
    this.self = self;
    this.agreementSignup = agreementSignup;
    this.addMembers = addMembers;
    this.agreementsEnabled = config.getBoolean("auth", "contributorAgreements", false);
  }

  @Override
  public Response<String> apply(AccountResource resource, AgreementInput input)
      throws IOException, OrmException, RestApiException {
    if (!agreementsEnabled) {
      throw new MethodNotAllowedException("contributor agreements disabled");
    }

    if (self.get() != resource.getUser()) {
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

    AccountGroup group = groupCache.get(uuid);
    if (group == null) {
      throw new ResourceConflictException("autoverify group not found");
    }

    Account account = self.get().getAccount();
    addMembers.addMembers(group.getId(), ImmutableList.of(account.getId()));
    agreementSignup.fire(account, agreementName);

    return Response.ok(agreementName);
  }
}
