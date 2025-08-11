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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.ConvertibleToProto;

/**
 * An action to be triggered when the condition of a {@link FlowExpression} becomes satisfied.
 *
 * <p>Actions can have arbitrary parameters. For example an {@code AddReviewer} action can have the
 * user to be added as a reviewer as a parameter.
 */
@ConvertibleToProto
@AutoValue
public abstract class FlowAction {
  /** The name of the action. */
  public abstract String name();

  /** Parameters for the action. */
  public abstract ImmutableMap<String, String> parameters();

  /**
   * Creates a builder for building a flow action.
   *
   * @return the builder for building the flow action
   */
  public static FlowAction.Builder builder() {
    return new AutoValue_FlowAction.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Set the name of the action. */
    public abstract Builder name(String name);

    /** Sets the expressions for the stages of the flow. */
    public abstract Builder parameters(ImmutableMap<String, String> parameters);

    abstract ImmutableMap.Builder<String, String> parametersBuilder();

    /** Adds an expression for another stage. */
    @CanIgnoreReturnValue
    public Builder addParameter(String key, String value) {
      parametersBuilder().put(key, value);
      return this;
    }

    /** Builds the {@link FlowAction}. */
    public abstract FlowAction build();
  }
}
