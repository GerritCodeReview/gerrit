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
  public static Builder builder() {
    return new AutoValue_SubmitRuleOptions.Builder();
  }

  public static Builder defaults() {
    return builder()
        .fastEvalLabels(false)
        .allowDraft(false)
        .allowClosed(false)
        .skipFilters(false)
        .rule(null);
  }

  public abstract boolean fastEvalLabels();

  public abstract boolean allowDraft();

  public abstract boolean allowClosed();

  public abstract boolean skipFilters();

  @Nullable
  public abstract String rule();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract SubmitRuleOptions.Builder fastEvalLabels(boolean fastEvalLabels);

    public abstract SubmitRuleOptions.Builder allowDraft(boolean allowDraft);

    public abstract SubmitRuleOptions.Builder allowClosed(boolean allowClosed);

    public abstract SubmitRuleOptions.Builder skipFilters(boolean skipFilters);

    public abstract SubmitRuleOptions.Builder rule(@Nullable String rule);

    public abstract SubmitRuleOptions build();
  }

  public Builder toBuilder() {
    return builder()
        .fastEvalLabels(fastEvalLabels())
        .allowDraft(allowDraft())
        .allowClosed(allowClosed())
        .skipFilters(skipFilters())
        .rule(rule());
  }
}
