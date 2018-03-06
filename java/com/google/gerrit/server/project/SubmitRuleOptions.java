// Copyright (C) 2016 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;

/**
 * Stable identifier for options passed to a particular submit rule evaluator.
 *
 * <p>Used to test whether it is ok to reuse a cached list of submit records. Does not include a
 * change or patch set ID; callers are responsible for checking those on their own.
 */
@AutoValue
public abstract class SubmitRuleOptions {
  private static final SubmitRuleOptions defaults =
      new AutoValue_SubmitRuleOptions.Builder()
          .allowClosed(false)
          .skipFilters(false)
          .logErrors(true)
          .rule(null)
          .build();

  public static SubmitRuleOptions defaults() {
    return defaults;
  }

  public static Builder builder() {
    return defaults.toBuilder();
  }

  public abstract boolean allowClosed();

  public abstract boolean skipFilters();

  public abstract boolean logErrors();

  @Nullable
  public abstract String rule();

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract SubmitRuleOptions.Builder allowClosed(boolean allowClosed);

    public abstract SubmitRuleOptions.Builder skipFilters(boolean skipFilters);

    public abstract SubmitRuleOptions.Builder rule(@Nullable String rule);

    public abstract SubmitRuleOptions.Builder logErrors(boolean logErrors);

    public abstract SubmitRuleOptions build();
  }
}
