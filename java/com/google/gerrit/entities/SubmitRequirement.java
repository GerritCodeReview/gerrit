//  Copyright (C) 2021 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import java.util.Optional;

/** Entity describing a requirement that should be met for a change to become submittable. */
@AutoValue
public abstract class SubmitRequirement {
  /** Requirement name. */
  public abstract String name();

  /** Description of what this requirement means. */
  public abstract Optional<String> description();

  /**
   * Expression of the condition that makes the requirement applicable. The expression should be
   * evaluated for a specific {@link Change} and if it returns false, the requirement becomes
   * irrelevant for the change (i.e. {@link #submittabilityExpression()} and {@link
   * #overrideExpression()} become irrelevant).
   *
   * <p>An empty {@link Optional} indicates that the requirement is applicable for any change.
   */
  public abstract Optional<SubmitRequirementExpression> applicabilityExpression();

  /**
   * Expression of the condition that allows the submission of a change. The expression should be
   * evaluated for a specific {@link Change} and if it returns true, the requirement becomes
   * fulfilled for the change.
   */
  public abstract Optional<SubmitRequirementExpression> submittabilityExpression();

  /**
   * Expression that, if evaluated to true, causes the submit requirement to be fulfilled,
   * regardless of the submittability expression. This expression should be evaluated for a specific
   * {@link Change}.
   *
   * <p>An empty {@link Optional} indicates that the requirement is not overridable.
   */
  public abstract Optional<SubmitRequirementExpression> overrideExpression();

  /**
   * Boolean value indicating if the {@link SubmitRequirement} definition can be overridden in child
   * projects. Default is false.
   */
  public abstract boolean allowOverrideInChildProjects();

  public static SubmitRequirement.Builder builder() {
    return new AutoValue_SubmitRequirement.Builder();
  }

  public static TypeAdapter<SubmitRequirement> typeAdapter(Gson gson) {
    return new AutoValue_SubmitRequirement.GsonTypeAdapter(gson);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(String name);

    public abstract Builder setDescription(Optional<String> description);

    public abstract Builder setApplicabilityExpression(
        Optional<SubmitRequirementExpression> applicabilityExpression);

    public abstract Builder setSubmittabilityExpression(
        Optional<SubmitRequirementExpression> submittabilityExpression);

    public abstract Builder setOverrideExpression(
        Optional<SubmitRequirementExpression> overrideExpression);

    public abstract Builder setAllowOverrideInChildProjects(boolean allowOverrideInChildProjects);

    public abstract SubmitRequirement build();
  }
}
