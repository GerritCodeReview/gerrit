// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.entities;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import java.util.List;

/** Portion of a {@link Project} describing a single contributor agreement. */
public record ContributorAgreement(
    String name,
    @Nullable String description,
    ImmutableList<PermissionRule> accepted,
    @Nullable GroupReference autoVerify,
    @Nullable String agreementUrl,
    ImmutableList<String> excludeProjectsRegexes,
    ImmutableList<String> matchProjectsRegexes)
    implements Comparable<ContributorAgreement> {
  public ContributorAgreement {
    requireNonNull(name, "name");
    requireNonNull(accepted, "accepted");
    requireNonNull(excludeProjectsRegexes, "excludeProjectsRegexes");
    requireNonNull(matchProjectsRegexes, "matchProjectsRegexes");
  }

  public static ContributorAgreement.Builder builder(String name) {
    return new AutoBuilder_ContributorAgreement_Builder()
        .setName(name)
        .setAccepted(ImmutableList.of())
        .setExcludeProjectsRegexes(ImmutableList.of())
        .setMatchProjectsRegexes(ImmutableList.of());
  }

  @Override
  public final int compareTo(ContributorAgreement o) {
    return name().compareTo(o.name());
  }

  @Override
  public final String toString() {
    return "ContributorAgreement[" + name() + "]";
  }

  public Builder toBuilder() {
    return new AutoBuilder_ContributorAgreement_Builder(this);
  }

  @AutoBuilder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setDescription(@Nullable String description);

    public abstract Builder setAccepted(ImmutableList<PermissionRule> accepted);

    public abstract Builder setAutoVerify(@Nullable GroupReference autoVerify);

    public abstract Builder setAgreementUrl(@Nullable String agreementUrl);

    public abstract Builder setExcludeProjectsRegexes(List<String> excludeProjectsRegexes);

    public abstract Builder setMatchProjectsRegexes(List<String> matchProjectsRegexes);

    public abstract ContributorAgreement build();
  }
}
