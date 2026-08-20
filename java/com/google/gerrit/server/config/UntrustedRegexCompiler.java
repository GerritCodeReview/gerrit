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

package com.google.gerrit.server.config;

import com.google.gerrit.server.ioutil.RegexCompiler;
import com.google.gerrit.server.permissions.RegexPermissionPolicy;
import com.google.inject.Inject;
import com.google.inject.Provider;
import dk.brics.automaton.Automaton;

/** Checks the current user before compiling a regular expression. */
public final class UntrustedRegexCompiler implements RegexCompiler {
  /**
   * Carries a query error through {@link RegexCompiler}, which cannot throw checked exceptions.
   *
   * <p>This is a conscious choice for the stable branch: it avoids changing the compiler API and
   * all its existing callers just to propagate a permission failure. On master, this should be
   * revisited so permission failures can be represented and propagated explicitly.
   */
  public static final class RegexPermissionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private RegexPermissionException(RegexPermissionPolicy.RegexNotAllowedException cause) {
      super(cause.getMessage(), cause);
    }
  }

  private final RegexCompiler trustedCompiler;
  private final Provider<RegexPermissionPolicy> regexSearchPolicyProvider;

  @Inject
  UntrustedRegexCompiler(
      @TrustedRegex RegexCompiler trustedCompiler,
      Provider<RegexPermissionPolicy> regexSearchPolicyProvider) {
    this.trustedCompiler = trustedCompiler;
    this.regexSearchPolicyProvider = regexSearchPolicyProvider;
  }

  @Override
  public Automaton toAutomaton(String pattern) {
    if (!regexSearchPolicyProvider.get().isAllowed()) {
      throw new RegexPermissionException(new RegexPermissionPolicy.RegexNotAllowedException());
    }
    return trustedCompiler.toAutomaton(pattern);
  }
}
