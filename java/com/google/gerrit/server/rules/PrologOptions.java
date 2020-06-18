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

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import java.util.Optional;

@AutoValue
public abstract class PrologOptions {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static PrologOptions defaultOptions() {
    return new AutoValue_PrologOptions.Builder().logErrors(true).skipFilters(false).build();
  }

  public static PrologOptions dryRunOptions(String ruleToTest, boolean skipFilters) {
    return new AutoValue_PrologOptions.Builder()
        .logErrors(logger.atFine().isEnabled())
        .skipFilters(skipFilters)
        .rule(ruleToTest)
        .build();
  }

  /** Whether errors should be logged. */
  abstract boolean logErrors();

  /** Whether Prolog filters from parent projects should be skipped. */
  abstract boolean skipFilters();

  /**
   * Prolog rule that should be run. If not given, the Prolog rule that is configured for the
   * project is used (the rule from rules.pl in refs/meta/config).
   */
  abstract Optional<String> rule();

  @AutoValue.Builder
  abstract static class Builder {
    abstract PrologOptions.Builder logErrors(boolean logErrors);

    abstract PrologOptions.Builder skipFilters(boolean skipFilters);

    abstract PrologOptions.Builder rule(@Nullable String rule);

    abstract PrologOptions build();
  }
}
