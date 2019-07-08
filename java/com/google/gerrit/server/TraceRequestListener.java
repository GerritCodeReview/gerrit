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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.RequestId;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
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
  private final ImmutableList<TraceConfig> traceConfigs;

  @Inject
  TraceRequestListener(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
    this.traceConfigs = parseTraceConfigs();
  }

  @Override
  public void onRequest(RequestInfo requestInfo) {
    requestInfo.project().ifPresent(p -> requestInfo.traceContext().addTag("project", p));
    traceConfigs.stream()
        .filter(traceConfig -> traceConfig.matches(requestInfo))
        .forEach(
            traceConfig ->
                requestInfo
                    .traceContext()
                    .forceLogging()
                    .addTag(RequestId.Type.TRACE_ID, traceConfig.traceId()));
  }

  private ImmutableList<TraceConfig> parseTraceConfigs() {
    ImmutableList.Builder<TraceConfig> traceConfigs = ImmutableList.builder();

    for (String traceId : cfg.getSubsections("tracing")) {
      try {
        TraceConfig.Builder traceConfig = TraceConfig.builder();
        traceConfig.traceId(traceId);
        traceConfig.requestTypes(parseRequestTypes(traceId));
        traceConfig.requestUriPatterns(parseRequestUriPatterns(traceId));
        traceConfig.accountIds(parseAccounts(traceId));
        traceConfig.projectPatterns(parseProjectPatterns(traceId));
        traceConfigs.add(traceConfig.build());
      } catch (IllegalArgumentException e) {
        logger.atWarning().log("Ignoring invalid tracing configuration:\n %s", e.getMessage());
      }
    }

    return traceConfigs.build();
  }

  private ImmutableSet<String> parseRequestTypes(String traceId) {
    return ImmutableSet.copyOf(cfg.getStringList("tracing", traceId, "requestType"));
  }

  private ImmutableSet<Pattern> parseRequestUriPatterns(String traceId) {
    return parsePatterns(traceId, "requestUriPattern");
  }

  private ImmutableSet<Account.Id> parseAccounts(String traceId) {
    ImmutableSet.Builder<Account.Id> accountIds = ImmutableSet.builder();
    String[] accounts = cfg.getStringList("tracing", traceId, "account");
    for (String account : accounts) {
      Optional<Account.Id> accountId = Account.Id.tryParse(account);
      if (!accountId.isPresent()) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid tracing config ('tracing.%s.account = %s'): invalid account ID",
                traceId, account));
      }
      accountIds.add(accountId.get());
    }
    return accountIds.build();
  }

  private ImmutableSet<Pattern> parseProjectPatterns(String traceId) {
    return parsePatterns(traceId, "projectPattern");
  }

  private ImmutableSet<Pattern> parsePatterns(String traceId, String name) {
    ImmutableSet.Builder<Pattern> patterns = ImmutableSet.builder();
    String[] patternRegExs = cfg.getStringList("tracing", traceId, name);
    for (String patternRegEx : patternRegExs) {
      try {
        patterns.add(Pattern.compile(patternRegEx));
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid tracing config ('tracing.%s.%s = %s'): %s",
                traceId, name, patternRegEx, e.getMessage()));
      }
    }
    return patterns.build();
  }

  @AutoValue
  abstract static class TraceConfig {
    /** ID for the trace */
    abstract String traceId();

    /** request types that should be traced */
    abstract ImmutableSet<String> requestTypes();

    /** pattern matching request URIs */
    abstract ImmutableSet<Pattern> requestUriPatterns();

    /** accounts IDs matching calling user */
    abstract ImmutableSet<Account.Id> accountIds();

    /** pattern matching projects names */
    abstract ImmutableSet<Pattern> projectPatterns();

    static Builder builder() {
      return new AutoValue_TraceRequestListener_TraceConfig.Builder();
    }

    boolean matches(RequestInfo requestInfo) {
      if (!requestTypes().isEmpty()
          && !requestTypes().stream()
              .anyMatch(type -> type.equalsIgnoreCase(requestInfo.requestType()))) {
        return false;
      }

      if (!requestUriPatterns().isEmpty()) {
        if (!requestInfo.requestUri().isPresent()) {
          // request has no request URI
          return false;
        }

        if (!requestUriPatterns().stream()
            .anyMatch(p -> p.matcher(requestInfo.requestUri().get()).matches())) {
          return false;
        }
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

      abstract Builder requestTypes(ImmutableSet<String> requestTypes);

      abstract Builder requestUriPatterns(ImmutableSet<Pattern> requestUriPatterns);

      abstract Builder accountIds(ImmutableSet<Account.Id> accountIds);

      abstract Builder projectPatterns(ImmutableSet<Pattern> projectPatterns);

      abstract TraceConfig build();
    }
  }
}
