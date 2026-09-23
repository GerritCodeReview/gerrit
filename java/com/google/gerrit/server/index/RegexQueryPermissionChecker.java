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

package com.google.gerrit.server.index;

import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.permissions.RegexPermissionPolicy;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/** Checks if the supplied query that contains a regular expression. */
@Singleton
public class RegexQueryPermissionChecker {
  protected final Provider<RegexPermissionPolicy> regexPermissionPolicyProvider;

  @Inject
  protected RegexQueryPermissionChecker(
      Provider<RegexPermissionPolicy> regexPermissionPolicyProvider) {
    this.regexPermissionPolicyProvider = regexPermissionPolicyProvider;
  }

  public boolean containsRegexInQuery(String queryString) throws QueryParseException {
    return QueryBuilder.findTextInParsedQuery(queryString, (leafText) -> leafText.startsWith("^"));
  }

  public void check(String query) throws QueryParseException {
    if (containsRegexInQuery(query)) {
      regexPermissionPolicyProvider.get().check();
    }
  }

  public boolean isAllowed() {
    return regexPermissionPolicyProvider.get().isAllowed();
  }
}
