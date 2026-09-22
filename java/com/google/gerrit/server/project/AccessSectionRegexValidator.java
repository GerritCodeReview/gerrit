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

import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.server.permissions.RegexPermissionPolicy;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Validates newly added regular expression access sections. */
@Singleton
public class AccessSectionRegexValidator {
  private final Provider<RegexPermissionPolicy> regexQueryPolicyProvider;

  @Inject
  AccessSectionRegexValidator(Provider<RegexPermissionPolicy> regexQueryPolicyProvider) {
    this.regexQueryPolicyProvider = regexQueryPolicyProvider;
  }

  public void validateNewRegexes(
      Collection<AccessSection> existingSections, Collection<AccessSection> candidateSections)
      throws ConfigInvalidException {
    if (!isAllowed()
        && !getRegexRefNames(existingSections).containsAll(getRegexRefNames(candidateSections))) {
      throw new ConfigInvalidException(RegexPermissionPolicy.NOT_PERMITTED_MESSAGE);
    }
  }

  public boolean isAllowed() {
    return regexQueryPolicyProvider.get().isAllowed();
  }

  private static Set<String> getRegexRefNames(Collection<AccessSection> sections) {
    return sections.stream()
        .map(AccessSection::getName)
        .filter(RefPattern::isRE)
        .collect(Collectors.toSet());
  }
}
