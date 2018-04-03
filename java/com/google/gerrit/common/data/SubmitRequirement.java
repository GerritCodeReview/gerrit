// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/** Describes a requirement to submit a change. */
@GwtIncompatible
@AutoValue
@AutoValue.CopyAnnotations
public abstract class SubmitRequirement {
  private static final CharMatcher TYPE_MATCHER =
      CharMatcher.inRange('a', 'z')
          .or(CharMatcher.inRange('A', 'Z'))
          .or(CharMatcher.inRange('0', '9'))
          .or(CharMatcher.anyOf("-_"));

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setType(String value);

    public abstract Builder setFallbackText(String value);

    public Builder setData(Map<String, String> value) {
      return setData(ImmutableMap.copyOf(value));
    }

    public Builder addCustomValue(String key, String value) {
      dataBuilder().put(key, value);
      return this;
    }

    public SubmitRequirement build() {
      SubmitRequirement requirement = autoBuild();
      Preconditions.checkState(
          validateType(requirement.type()),
          "SubmitRequirement's type contains non alphanumerical symbols.");
      return requirement;
    }

    abstract Builder setData(ImmutableMap<String, String> value);

    abstract ImmutableMap.Builder<String, String> dataBuilder();

    abstract SubmitRequirement autoBuild();
  }

  public abstract String fallbackText();

  public abstract String type();

  public abstract ImmutableMap<String, String> data();

  public static Builder builder() {
    return new AutoValue_SubmitRequirement.Builder();
  }

  private static boolean validateType(String type) {
    return TYPE_MATCHER.matchesAllOf(type);
  }
}
