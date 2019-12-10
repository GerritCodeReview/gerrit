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

package com.google.gerrit.server.rules;

import org.eclipse.jgit.lib.Config;

/** Provides utility methods for configuring and running Prolog rules inside Gerrit. */
public class RuleUtil {

  /**
   * Returns the reduction limit to be applied to the Prolog machine to prevent infinite loops and
   * other forms of computational overflow.
   */
  public static int reductionLimit(Config gerritConfig) {
    int limit = gerritConfig.getInt("rules", null, "reductionLimit", 100000);
    return limit <= 0 ? Integer.MAX_VALUE : limit;
  }

  /**
   * Returns the compile reduction limit to be applied to the Prolog machine to prevent infinite
   * loops and other forms of computational overflow. The compiled reduction limit should be used
   * when user-provided Prolog code is compiled by the interpreter before the limit gets applied.
   */
  public static int compileReductionLimit(Config gerritConfig) {
    int limit =
        gerritConfig.getInt(
            "rules",
            null,
            "compileReductionLimit",
            (int) Math.min(10L * reductionLimit(gerritConfig), Integer.MAX_VALUE));
    return limit <= 0 ? Integer.MAX_VALUE : limit;
  }
}
