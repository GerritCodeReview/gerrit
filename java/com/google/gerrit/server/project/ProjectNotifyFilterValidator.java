// Copyright (C) 2026 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.NotifyConfig;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.index.RegexQueryPermissionChecker;
import com.google.gerrit.server.permissions.RegexPermissionPolicy;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Validates new or changed project notification filters. */
@Singleton
public class ProjectNotifyFilterValidator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final RegexQueryPermissionChecker regexPermissionChecker;
  private final Provider<RegexPermissionPolicy> regexPermissionPolicyProvider;

  @Inject
  ProjectNotifyFilterValidator(
      RegexQueryPermissionChecker regexPermissionChecker,
      Provider<RegexPermissionPolicy> regexPermissionPolicyProvider) {
    this.regexPermissionChecker = regexPermissionChecker;
    this.regexPermissionPolicyProvider = regexPermissionPolicyProvider;
  }

  public void validateNewOrChangedFilters(
      Collection<NotifyConfig> existingNotifyConfigs,
      Collection<NotifyConfig> candidateNotifyConfigs)
      throws ConfigInvalidException {
    if (isAllowed()) {
      return;
    }

    if (!regexFilters(existingNotifyConfigs).containsAll(regexFilters(candidateNotifyConfigs))) {
      throw new ConfigInvalidException(RegexPermissionPolicy.NOT_PERMITTED_MESSAGE);
    }
  }

  public boolean isAllowed() {
    return regexPermissionPolicyProvider.get().isAllowed();
  }

  private Set<Map.Entry<String, String>> regexFilters(Collection<NotifyConfig> notifyConfigs) {
    return notifyConfigs.stream()
        .filter(this::hasRegex)
        .collect(Collectors.toMap(NotifyConfig::getName, NotifyConfig::getFilter))
        .entrySet();
  }

  private boolean hasRegex(NotifyConfig config) {
    try {
      return config.getFilter() != null
          && regexPermissionChecker.containsRegexInQuery(config.getFilter());
    } catch (QueryParseException e) {
      logger.atWarning().withCause(e).log("Invalid filter expression: %s", config.getFilter());
      return false;
    }
  }
}
