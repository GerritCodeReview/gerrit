// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.entities.Account;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * Represents a configuration on request level that matches requests by request type, URI pattern,
 * caller and/or project pattern.
 */
@AutoValue
public abstract class RequestConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static ImmutableList<RequestConfig> parseConfigs(Config cfg, String section) {
    ImmutableList.Builder<RequestConfig> requestConfigs = ImmutableList.builder();

    for (String id : cfg.getSubsections(section)) {
      try {
        RequestConfig.Builder requestConfig = RequestConfig.builder(cfg, section, id);
        requestConfig.requestTypes(parseRequestTypes(cfg, section, id));
        requestConfig.requestUriPatterns(parseRequestUriPatterns(cfg, section, id));
        requestConfig.excludedRequestUriPatterns(parseExcludedRequestUriPatterns(cfg, section, id));
        requestConfig.requestQueryStringPatterns(parseRequestQueryStringPatterns(cfg, section, id));
        requestConfig.accountIds(parseAccounts(cfg, section, id));
        requestConfig.projectPatterns(parseProjectPatterns(cfg, section, id));
        requestConfigs.add(requestConfig.build());
      } catch (ConfigInvalidException e) {
        logger.atWarning().log("Ignoring invalid %s configuration:\n %s", section, e.getMessage());
      }
    }

    return requestConfigs.build();
  }

  private static ImmutableSet<String> parseRequestTypes(Config cfg, String section, String id) {
    return ImmutableSet.copyOf(cfg.getStringList(section, id, "requestType"));
  }

  private static ImmutableSet<Pattern> parseRequestUriPatterns(
      Config cfg, String section, String id) throws ConfigInvalidException {
    return parsePatterns(cfg, section, id, "requestUriPattern");
  }

  private static ImmutableSet<Pattern> parseExcludedRequestUriPatterns(
      Config cfg, String section, String id) throws ConfigInvalidException {
    return parsePatterns(cfg, section, id, "excludedRequestUriPattern");
  }

  private static ImmutableSet<Pattern> parseRequestQueryStringPatterns(
      Config cfg, String section, String id) throws ConfigInvalidException {
    return parsePatterns(cfg, section, id, "requestQueryStringPattern");
  }

  private static ImmutableSet<Account.Id> parseAccounts(Config cfg, String section, String id)
      throws ConfigInvalidException {
    ImmutableSet.Builder<Account.Id> accountIds = ImmutableSet.builder();
    String[] accounts = cfg.getStringList(section, id, "account");
    for (String account : accounts) {
      Optional<Account.Id> accountId = Account.Id.tryParse(account);
      if (!accountId.isPresent()) {
        throw new ConfigInvalidException(
            String.format(
                "Invalid request config ('%s.%s.account = %s'): invalid account ID",
                section, id, account));
      }
      accountIds.add(accountId.get());
    }
    return accountIds.build();
  }

  private static ImmutableSet<Pattern> parseProjectPatterns(Config cfg, String section, String id)
      throws ConfigInvalidException {
    return parsePatterns(cfg, section, id, "projectPattern");
  }

  private static ImmutableSet<Pattern> parsePatterns(
      Config cfg, String section, String id, String name) throws ConfigInvalidException {
    ImmutableSet.Builder<Pattern> patterns = ImmutableSet.builder();
    String[] patternRegExs = cfg.getStringList(section, id, name);
    for (String patternRegEx : patternRegExs) {
      try {
        patterns.add(Pattern.compile(patternRegEx));
      } catch (PatternSyntaxException e) {
        throw new ConfigInvalidException(
            String.format(
                "Invalid request config ('%s.%s.%s = %s'): %s",
                section, id, name, patternRegEx, e.getMessage()));
      }
    }
    return patterns.build();
  }

  /** the config from which this request config was read */
  abstract Config cfg();

  /** the section from which this request config was read */
  abstract String section();

  /** ID of the config, also the subsection from which this request config was read */
  abstract String id();

  /** request types that should be matched */
  abstract ImmutableSet<String> requestTypes();

  /** pattern matching request URIs */
  abstract ImmutableSet<Pattern> requestUriPatterns();

  /** pattern matching request URIs to be excluded */
  abstract ImmutableSet<Pattern> excludedRequestUriPatterns();

  /** pattern matching request query strings */
  abstract ImmutableSet<Pattern> requestQueryStringPatterns();

  /** accounts IDs matching calling user */
  abstract ImmutableSet<Account.Id> accountIds();

  /** pattern matching projects names */
  abstract ImmutableSet<Pattern> projectPatterns();

  private static Builder builder(Config cfg, String section, String id) {
    return new AutoValue_RequestConfig.Builder().cfg(cfg).section(section).id(id);
  }

  /**
   * Whether this request config matches a given request.
   *
   * @param requestInfo request info
   * @return whether this request config matches
   */
  boolean matches(RequestInfo requestInfo) {
    // If in the request config request types are set and none of them matches, then the request is
    // not matched.
    if (!requestTypes().isEmpty()
        && requestTypes().stream()
            .noneMatch(type -> type.equalsIgnoreCase(requestInfo.requestType()))) {
      return false;
    }

    // If in the request config request URI patterns are set and none of them matches, then the
    // request is not matched.
    if (!requestUriPatterns().isEmpty()) {
      if (!requestInfo.requestUri().isPresent()) {
        // The request has no request URI, hence it cannot match a request URI pattern.
        return false;
      }

      if (requestUriPatterns().stream()
          .noneMatch(p -> p.matcher(requestInfo.requestUri().get()).matches())) {
        return false;
      }
    }

    // If the request URI matches an excluded request URI pattern, then the request is not matched.
    if (requestInfo.requestUri().isPresent()
        && excludedRequestUriPatterns().stream()
            .anyMatch(p -> p.matcher(requestInfo.requestUri().get()).matches())) {
      return false;
    }

    // If in the request config request query string patterns are set and none of them matches,
    // then the request is not matched.
    if (!requestQueryStringPatterns().isEmpty()) {
      if (!requestInfo.requestQueryString().isPresent()) {
        // The request has no request query string, hence it cannot match a request query string
        // pattern.
        return false;
      }

      if (requestQueryStringPatterns().stream()
          .noneMatch(p -> p.matcher(requestInfo.requestQueryString().get()).matches())) {
        return false;
      }
    }

    // If in the request config accounts are set and none of them matches, then the request is not
    // matched.
    if (!accountIds().isEmpty()) {
      try {
        if (accountIds().stream()
            .noneMatch(id -> id.equals(requestInfo.callingUser().getAccountId()))) {
          return false;
        }
      } catch (UnsupportedOperationException e) {
        // The calling user is not logged in, hence it cannot match an account.
        return false;
      }
    }

    // If in the request config project patterns are set and none of them matches, then the request
    // is not matched.
    if (!projectPatterns().isEmpty()) {
      if (!requestInfo.project().isPresent()) {
        // The request is not for a project, hence it cannot match a project pattern.
        return false;
      }

      if (projectPatterns().stream()
          .noneMatch(p -> p.matcher(requestInfo.project().get().get()).matches())) {
        return false;
      }
    }

    // For any match criteria (request type, request URI pattern, request query string pattern,
    // account, project pattern) that was specified in the request config, at least one of the
    // configured value matched the request.
    return true;
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder cfg(Config cfg);

    abstract Builder section(String section);

    abstract Builder id(String id);

    abstract Builder requestTypes(ImmutableSet<String> requestTypes);

    abstract Builder requestUriPatterns(ImmutableSet<Pattern> requestUriPatterns);

    abstract Builder excludedRequestUriPatterns(ImmutableSet<Pattern> excludedRequestUriPatterns);

    abstract Builder requestQueryStringPatterns(ImmutableSet<Pattern> requestQueryStringPatterns);

    abstract Builder accountIds(ImmutableSet<Account.Id> accountIds);

    abstract Builder projectPatterns(ImmutableSet<Pattern> projectPatterns);

    abstract RequestConfig build();
  }
}
