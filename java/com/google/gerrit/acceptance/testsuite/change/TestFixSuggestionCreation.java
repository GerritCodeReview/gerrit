// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import java.util.List;
import java.util.Optional;

/** Representation of a FixSuggestion used for testing purposes. */
@AutoValue
public abstract class TestFixSuggestionCreation {

  public abstract Optional<String> description();

  public abstract ImmutableList<TestFixReplacementCreation> fixReplacements();

  static Builder builder() {
    return new AutoValue_TestFixSuggestionCreation.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setDescription(String description);

    abstract Builder setFixReplacements(@Nullable List<TestFixReplacementCreation> fixReplacements);

    abstract ImmutableList.Builder<TestFixReplacementCreation> fixReplacementsBuilder();

    public Builder addFixReplacement(TestFixReplacementCreation fixReplacement) {
      fixReplacementsBuilder().add(fixReplacement);
      return this;
    }

    abstract TestFixSuggestionCreation build();
  }

  @AutoValue
  public abstract static class TestFixReplacementCreation {

    public abstract Optional<String> path();

    public abstract Optional<TestRange> range();

    public abstract Optional<String> replacement();

    static Builder builder() {
      return new AutoValue_TestFixSuggestionCreation_TestFixReplacementCreation.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder path(String path);

      abstract Builder range(TestRange range);

      public abstract Builder replacement(String replacement);

      abstract TestFixReplacementCreation build();
    }
  }
}
