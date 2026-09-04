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

package com.google.gerrit.server.git.validators;

import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.permissions.RegexPermissionPolicy;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Validates newly added regular expressions in project configuration. */
@Singleton
public final class ProjectConfigRegexValidator {
  private final Provider<RegexPermissionPolicy> regexPermissionPolicyProvider;

  @Inject
  ProjectConfigRegexValidator(Provider<RegexPermissionPolicy> regexPermissionPolicyProvider) {
    this.regexPermissionPolicyProvider = regexPermissionPolicyProvider;
  }

  public boolean isAllowed(CommitReceivedEvent receiveEvent) {
    return !RefNames.REFS_CONFIG.equals(receiveEvent.command.getRefName()) || isAllowed();
  }

  public boolean isAllowed() {
    return regexPermissionPolicyProvider.get().isAllowed();
  }

  public void assertNoAdditionalRegexes(
      Collection<?> existingRegexes, Collection<?> candidateRegexes) throws ConfigInvalidException {
    if (!existingRegexes.containsAll(candidateRegexes)) {
      throw new ConfigInvalidException(RegexPermissionPolicy.NOT_PERMITTED_MESSAGE);
    }
  }
}
