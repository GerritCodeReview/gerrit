// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jgit.lib.Config;

/**
 * Request listener that sets additional logging tags and enables tracing automatically if the
 * request matches any tracing configuration in gerrit.config (see description of
 * 'tracing.<trace-id>' subsection in config-gerrit.txt).
 */
@Singleton
public class TraceRequestListener implements RequestListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Config cfg;
  private final Provider<InternalAccountQuery> accountQueryProvider;

  private ImmutableList<TraceConfig> traceConfigs;

  @Inject
  TraceRequestListener(
      @GerritServerConfig Config cfg, Provider<InternalAccountQuery> accountQueryProvider) {
    this.cfg = cfg;
    this.accountQueryProvider = accountQueryProvider;
  }

  @Override
  public void onRequest(RequestInfo requestInfo) {
    requestInfo.project().ifPresent(p -> requestInfo.traceContext().addTag("project", p));
    getTraceConfigs().stream()
        .filter(traceConfig -> traceConfig.matches(requestInfo))
        .forEach(
            traceConfig ->
                requestInfo
                    .traceContext()
                    .forceLogging()
                    .addTag(RequestId.Type.TRACE_ID, traceConfig.traceId()));
  }

  private ImmutableList<TraceConfig> getTraceConfigs() {
    if (traceConfigs == null) {
      traceConfigs = parseTraceConfigs();
    }
    return traceConfigs;
  }

  private ImmutableList<TraceConfig> parseTraceConfigs() {
    ImmutableList.Builder<TraceConfig> traceConfigs = ImmutableList.builder();

    for (String traceId : cfg.getSubsections("tracing")) {
      try {
        TraceConfig.Builder traceConfig = TraceConfig.builder();
        traceConfig.traceId(traceId);
        traceConfig.requestTypes(parseRequestTypes(traceId));
        traceConfig.accountIds(parseAccounts(traceId));
        traceConfig.projectPatterns(parseProjectPatterns(traceId));
        traceConfigs.add(traceConfig.build());
      } catch (IllegalArgumentException e) {
        logger.atWarning().log("Ignoring invalid tracing configuration:\n %s", e.getMessage());
      }
    }

    return traceConfigs.build();
  }

  private ImmutableSet<RequestInfo.RequestType> parseRequestTypes(String traceId) {
    ImmutableSet.Builder<RequestInfo.RequestType> requestTypes = ImmutableSet.builder();
    String[] types = cfg.getStringList("tracing", traceId, "requestType");
    for (String type : types) {
      try {
        RequestInfo.RequestType requestType =
            RequestInfo.RequestType.valueOf(type.toUpperCase(Locale.US));
        requestTypes.add(requestType);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid tracing config ('tracing.%s.requestType = %s'): request type not known",
                traceId, type));
      }
    }
    return requestTypes.build();
  }

  private ImmutableSet<Account.Id> parseAccounts(String traceId) {
    ImmutableSet.Builder<Account.Id> accountIds = ImmutableSet.builder();
    String[] users = cfg.getStringList("tracing", traceId, "user");
    for (String user : users) {
      List<AccountState> accountStates =
          accountQueryProvider
              .get()
              .setLimit(2) // limit to 2 so that we know if there is more than 1 match
              .byDefault(user);
      if (accountStates.size() == 0) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid tracing config ('tracing.%s.user = %s'): user not found", traceId, user));
      }
      if (accountStates.size() > 1) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid tracing config ('tracing.%s.user = %s'): user is ambiguous",
                traceId, user));
      }
      accountIds.add(accountStates.get(0).getAccount().getId());
    }
    return accountIds.build();
  }

  private ImmutableSet<Pattern> parseProjectPatterns(String traceId) {
    ImmutableSet.Builder<Pattern> projectPatterns = ImmutableSet.builder();
    String[] projectPatternRegExs = cfg.getStringList("tracing", traceId, "projectPattern");
    for (String projectPatternRegEx : projectPatternRegExs) {
      try {
        projectPatterns.add(Pattern.compile(projectPatternRegEx));
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid tracing config ('tracing.%s.projectPattern = %s'): %s",
                traceId, projectPatternRegEx, e.getMessage()));
      }
    }
    return projectPatterns.build();
  }

  @AutoValue
  abstract static class TraceConfig {
    /** ID for the trace */
    abstract String traceId();

    /** request types that should be traced */
    abstract ImmutableSet<RequestInfo.RequestType> requestTypes();

    /** accounts IDs matching calling user */
    abstract ImmutableSet<Account.Id> accountIds();

    /** pattern matching projects names */
    abstract ImmutableSet<Pattern> projectPatterns();

    static Builder builder() {
      return new AutoValue_TraceRequestListener_TraceConfig.Builder();
    }

    boolean matches(RequestInfo requestInfo) {
      if (!requestTypes().isEmpty()
          && !requestTypes().stream().anyMatch(type -> type.equals(requestInfo.requestType()))) {
        return false;
      }

      if (!accountIds().isEmpty()) {
        try {
          if (!accountIds().stream()
              .anyMatch(id -> id.equals(requestInfo.callingUser().getAccountId()))) {
            return false;
          }
        } catch (UnsupportedOperationException e) {
          // calling user is not logged in
          return false;
        }
      }

      if (!projectPatterns().isEmpty()) {
        if (!requestInfo.project().isPresent()) {
          // request is not for a project
          return false;
        }

        if (!projectPatterns().stream()
            .anyMatch(p -> p.matcher(requestInfo.project().get().get()).matches())) {
          return false;
        }
      }

      return true;
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder traceId(String traceId);

      abstract Builder requestTypes(ImmutableSet<RequestInfo.RequestType> requestTypes);

      abstract Builder accountIds(ImmutableSet<Account.Id> accountIds);

      abstract Builder projectPatterns(ImmutableSet<Pattern> projectPatterns);

      abstract TraceConfig build();
    }
  }
}
