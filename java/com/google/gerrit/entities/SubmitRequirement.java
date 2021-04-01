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

/** Entity describing a requirement that should be met for a change to become submittable. */
@AutoValue
public abstract class SubmitRequirement {
  /** Requirement name. */
  public abstract String name();

  /** Description of what a requirement means. */
  public abstract String description();

  /**
   * Expression of the condition that makes the requirement applicable. The expression should be
   * evaluated for a specific {@link Change} and if it returns false, the requirement becomes
   * irrelevant for the change (i.e. {@link #blockingExpression()} and {@link #overrideExpression()}
   * become irrelevant).
   *
   * <p>An empty String indicates that the requirement is applicable for all changes.
   */
  public abstract String applicabilityExpression();

  /**
   * Expression of the condition that blocks the submission of a change. The expression should be
   * evaluated for a specific {@link Change} and if it returns false, the requirement becomes
   * fulfilled for the change.
   *
   * <p>An empty String indicates that the requirement is not blocking for all changes.
   */
  public abstract String blockingExpression();

  /**
   * Expression of the condition that overrides the submission of a change. The expression should be
   * evaluated for a specific {@link Change} and if it returns true, the submit requirement becomes
   * fulfilled for the change.
   *
   * <p>An empty String indicates that the requirement is not overridden.
   */
  public abstract String overrideExpression();

  /**
   * Boolean value indicating if the {@link SubmitRequirement} can be overridden in child projects.
   */
  public abstract boolean canOverride();

  public static SubmitRequirement.Builder builder() {
    return new AutoValue_SubmitRequirement.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(String name);

    public abstract Builder setDescription(String description);

    public abstract Builder setApplicabilityExpression(String applicabilityExpression);

    public abstract Builder setBlockingExpression(String blockingExpression);

    public abstract Builder setOverrideExpression(String overrideExpression);

    public abstract Builder setCanOverride(boolean canOverride);

    public abstract SubmitRequirement build();
  }
}
