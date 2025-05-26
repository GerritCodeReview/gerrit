// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.flow;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;

/** Definition of all properties necessary for creating a {@link Flow}. */
@AutoValue
public abstract class FlowCreation {
  /** The name of the project that contains the change for which the flow applies. */
  public abstract Project.NameKey projectName();

  /** The ID of the change for which the flow applies. */
  public abstract Change.Id changeId();

  /** The account ID of the user that owns the flow. */
  public abstract Account.Id ownerId();

  /** The expressions for the stages of the flow. */
  public abstract ImmutableList<FlowExpression> stageExpressions();

  /**
   * Creates a builder for building a flow creation.
   *
   * @return the builder for building the flow
   */
  public static FlowCreation.Builder builder() {
    return new AutoValue_FlowCreation.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the name of the project that contains the change for which the flow applies. */
    public abstract Builder projectName(Project.NameKey projecName);

    /** Set the ID of the change for which the flow applies. */
    public abstract Builder changeId(Change.Id changeId);

    /** Set the account ID of the user that owns the flow. */
    public abstract Builder ownerId(Account.Id ownerId);

    /**
     * Sets the expressions for the stages of the flow.
     *
     * <p>Each expressions becomes a stage.
     */
    public abstract Builder stageExpressions(ImmutableList<FlowExpression> stages);

    abstract ImmutableList.Builder<FlowExpression> stageExpressionsBuilder();

    /** Adds an expression for another stage. */
    @CanIgnoreReturnValue
    public Builder addStageExpression(FlowExpression expression) {
      stageExpressionsBuilder().add(expression);
      return this;
    }

    /** Builds the {@link FlowCreation} without validating the properties. */
    abstract FlowCreation autoBuild();

    /** Builds the {@link FlowCreation} with validating the properties.. */
    public FlowCreation build() {
      FlowCreation flowCreation = autoBuild();
      checkState(
          !flowCreation.stageExpressions().isEmpty(),
          "flow creation must contain at least one stage expression");
      return flowCreation;
    }
  }
}
