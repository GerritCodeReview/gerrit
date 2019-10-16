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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.plugincontext.PluginSetEntryContext;
import com.google.gerrit.server.quota.QuotaResponse.Aggregated;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class DefaultQuotaBackend implements QuotaBackend {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> userProvider;
  private final PluginSetContext<QuotaEnforcer> quotaEnforcers;

  @Inject
  DefaultQuotaBackend(
      Provider<CurrentUser> userProvider, PluginSetContext<QuotaEnforcer> quotaEnforcers) {
    this.userProvider = userProvider;
    this.quotaEnforcers = quotaEnforcers;
  }

  @Override
  public WithUser currentUser() {
    return new WithUser(quotaEnforcers, userProvider.get());
  }

  @Override
  public WithUser user(CurrentUser user) {
    return new WithUser(quotaEnforcers, user);
  }

  private static QuotaResponse.Aggregated request(
      PluginSetContext<QuotaEnforcer> quotaEnforcers,
      String quotaGroup,
      QuotaRequestContext requestContext,
      long numTokens,
      boolean deduct) {
    checkState(numTokens > 0, "numTokens must be a positive, non-zero long");

    // PluginSets can change their content when plugins (de-)register. Copy the currently registered
    // plugins so that we can iterate twice on a stable list.
    List<PluginSetEntryContext<QuotaEnforcer>> enforcers = ImmutableList.copyOf(quotaEnforcers);
    List<QuotaResponse> responses = new ArrayList<>(enforcers.size());
    for (PluginSetEntryContext<QuotaEnforcer> enforcer : enforcers) {
      try {
        if (deduct) {
          responses.add(enforcer.call(p -> p.requestTokens(quotaGroup, requestContext, numTokens)));
        } else {
          responses.add(enforcer.call(p -> p.dryRun(quotaGroup, requestContext, numTokens)));
        }
      } catch (RuntimeException e) {
        // Roll back the quota request for all enforcers that deducted the quota. Rethrow the
        // exception to adhere to the API contract.
        if (deduct) {
          refillAfterErrorOrException(enforcers, responses, quotaGroup, requestContext, numTokens);
        }
        throw e;
      }
    }

    if (deduct && responses.stream().anyMatch(r -> r.status().isError())) {
      // Roll back the quota request for all enforcers that deducted the quota (= the request
      // succeeded). Don't touch failed enforcers as the interface contract said that failed
      // requests should not be deducted.
      refillAfterErrorOrException(enforcers, responses, quotaGroup, requestContext, numTokens);
    }

    logger.atFine().log(
        "Quota request for %s with %s (deduction=%s) for %s token returned %s",
        quotaGroup,
        requestContext,
        deduct ? "(deduction=yes)" : "(deduction=no)",
        numTokens,
        responses);
    return QuotaResponse.Aggregated.create(ImmutableList.copyOf(responses));
  }

  private static QuotaResponse.Aggregated availableTokens(
      PluginSetContext<QuotaEnforcer> quotaEnforcers,
      String quotaGroup,
      QuotaRequestContext requestContext) {
    // PluginSets can change their content when plugins (de-)register. Copy the currently registered
    // plugins so that we can iterate twice on a stable list.
    List<PluginSetEntryContext<QuotaEnforcer>> enforcers = ImmutableList.copyOf(quotaEnforcers);
    List<QuotaResponse> responses = new ArrayList<>(enforcers.size());
    for (PluginSetEntryContext<QuotaEnforcer> enforcer : enforcers) {
      responses.add(enforcer.call(p -> p.availableTokens(quotaGroup, requestContext)));
    }
    return QuotaResponse.Aggregated.create(responses);
  }

  private static void refillAfterErrorOrException(
      List<PluginSetEntryContext<QuotaEnforcer>> enforcers,
      List<QuotaResponse> collectedResponses,
      String quotaGroup,
      QuotaRequestContext requestContext,
      long numTokens) {
    for (int i = 0; i < collectedResponses.size(); i++) {
      if (collectedResponses.get(i).status().isOk()) {
        enforcers.get(i).run(p -> p.refill(quotaGroup, requestContext, numTokens));
      }
    }
  }

  static class WithUser extends WithResource implements QuotaBackend.WithUser {
    WithUser(PluginSetContext<QuotaEnforcer> quotaEnforcers, CurrentUser user) {
      super(quotaEnforcers, QuotaRequestContext.builder().user(user).build());
    }

    @Override
    public QuotaBackend.WithResource account(Account.Id account) {
      QuotaRequestContext ctx = requestContext.toBuilder().account(account).build();
      return new WithResource(quotaEnforcers, ctx);
    }

    @Override
    public QuotaBackend.WithResource project(Project.NameKey project) {
      QuotaRequestContext ctx = requestContext.toBuilder().project(project).build();
      return new WithResource(quotaEnforcers, ctx);
    }

    @Override
    public QuotaBackend.WithResource change(Change.Id change, Project.NameKey project) {
      QuotaRequestContext ctx = requestContext.toBuilder().change(change).project(project).build();
      return new WithResource(quotaEnforcers, ctx);
    }
  }

  static class WithResource implements QuotaBackend.WithResource {
    protected final QuotaRequestContext requestContext;
    protected final PluginSetContext<QuotaEnforcer> quotaEnforcers;

    private WithResource(
        PluginSetContext<QuotaEnforcer> quotaEnforcers, QuotaRequestContext quotaRequestContext) {
      this.quotaEnforcers = quotaEnforcers;
      this.requestContext = quotaRequestContext;
    }

    @Override
    public QuotaResponse.Aggregated requestTokens(String quotaGroup, long numTokens) {
      return DefaultQuotaBackend.request(
          quotaEnforcers, quotaGroup, requestContext, numTokens, true);
    }

    @Override
    public QuotaResponse.Aggregated dryRun(String quotaGroup, long numTokens) {
      return DefaultQuotaBackend.request(
          quotaEnforcers, quotaGroup, requestContext, numTokens, false);
    }

    @Override
    public Aggregated availableTokens(String quotaGroup) {
      return DefaultQuotaBackend.availableTokens(quotaEnforcers, quotaGroup, requestContext);
    }
  }
}
