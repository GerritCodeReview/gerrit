// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.quota;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class DefaultQuotaBackend implements QuotaBackend {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> userProvider;
  private final DynamicSet<QuotaEnforcer> quotaEnforcers;

  @Inject
  DefaultQuotaBackend(
      Provider<CurrentUser> userProvider, DynamicSet<QuotaEnforcer> quotaEnforcers) {
    this.userProvider = userProvider;
    this.quotaEnforcers = quotaEnforcers;
  }

  @Override
  public WithUser currentUser() {
    return new WithUser(userProvider.get());
  }

  @Override
  public WithUser user(CurrentUser user) {
    return new WithUser(user);
  }

  class WithUser extends WithResource implements QuotaBackend.WithUser {
    private final CurrentUser user;

    WithUser(CurrentUser user) {
      super(user, Optional.empty(), Optional.empty(), Optional.empty());
      this.user = user;
    }

    @Override
    public QuotaBackend.WithResource account(Account.Id account) {
      return new WithResource(user, Optional.empty(), Optional.empty(), Optional.of(account));
    }

    @Override
    public QuotaBackend.WithResource project(NameKey project) {
      return new WithResource(user, Optional.of(project), Optional.empty(), Optional.empty());
    }

    @Override
    public QuotaBackend.WithResource change(Change.Id change, NameKey project) {
      return new WithResource(user, Optional.of(project), Optional.of(change), Optional.empty());
    }
  }

  class WithResource implements QuotaBackend.WithResource {
    private final QuotaRequestContext requestContext;

    private WithResource(
        CurrentUser user,
        Optional<Project.NameKey> project,
        Optional<Change.Id> change,
        Optional<Account.Id> account) {
      requestContext =
          QuotaRequestContext.builder()
              .user(user)
              .project(project)
              .change(change)
              .account(account)
              .build();
    }

    @Override
    public QuotaResponse.Aggregated request(String quotaGroup, long numTokens) {
      checkState(numTokens > 0, "numTokens must be a positive, non-zero long");
      return DefaultQuotaBackend.this.request(quotaGroup, requestContext, numTokens, true);
    }

    @Override
    public QuotaResponse.Aggregated requestNoDeduction(String quotaGroup, long numTokens) {
      checkState(numTokens > 0, "numTokens must be a positive, non-zero long");
      return DefaultQuotaBackend.this.request(quotaGroup, requestContext, numTokens, false);
    }
  }

  private QuotaResponse.Aggregated request(
      String quotaGroup, QuotaRequestContext requestContext, long numTokens, boolean deduct) {
    List<QuotaEnforcer> enforcers = ImmutableList.copyOf(quotaEnforcers);
    List<QuotaResponse> responses = new ArrayList<>(enforcers.size());
    for (QuotaEnforcer enforcer : enforcers) {
      if (deduct) {
        responses.add(enforcer.request(quotaGroup, requestContext, numTokens));
      } else {
        responses.add(enforcer.requestNoDeduction(quotaGroup, requestContext, numTokens));
      }
    }

    if (deduct && responses.stream().anyMatch(r -> !r.status().isOk())) {
      // Roll back the quota request for all enforcer that deducted the quote (= the request
      // succeeded). Don't touch failed enforcers as the interface contract said that failed
      // requests should not be deducted.
      for (int i = 0; i < responses.size(); i++) {
        if (responses.get(i).status().isOk()) {
          enforcers.get(i).refill(quotaGroup, requestContext, numTokens);
        }
      }
    }

    logger.atInfo().log(
        "Quota request for %s with %s %s for %s token returned %s",
        quotaGroup,
        requestContext,
        deduct ? "(deduction=yes)" : "(deduction=no)",
        numTokens,
        responses);
    return new QuotaResponse.Aggregated(responses);
  }
}
