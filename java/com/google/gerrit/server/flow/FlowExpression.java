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
import com.google.gerrit.common.ConvertibleToProto;
import java.util.Optional;

/** Expression defining an action that should be triggered when a condition becomes satisfied. */
@ConvertibleToProto
@AutoValue
public abstract class FlowExpression {
  /**
   * The condition which must be satisfied for the action to be triggered.
   *
   * <p>Can contain multiple conditions separated by comma.
   */
  public abstract String condition();

  /** The action that should be triggered when the condition is satisfied. */
  public abstract Optional<FlowAction> action();

  public static FlowExpression.Builder builder() {
    return new AutoValue_FlowExpression.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets the condition which must be fulfilled for the action to be triggered.
     *
     * <p>Can contain multiple conditions separated by comma.
     *
     * <p>Conditions can be Gerrit conditions (e.g. "is the change verified?") as well as conditions
     * in third-party tools (e.g. "is the issue fixed?").
     *
     * <p>Gerrit conditions are expressed as change queries and are satisfied if they match at least
     * one change (example: {@code change:123 label:Verified+1}).
     *
     * <p>The syntax for conditions in third-party tools depends on the flow service implementation.
     */
    public abstract Builder condition(String condition);

    /**
     * Sets the action that should be triggered when the condition is satisfied.
     *
     * <p>If multiple actions are wanted, multiple flows or multiple stages should be added.
     */
    public abstract Builder action(FlowAction action);

    /** Builds the {@link FlowExpression}. */
    public abstract FlowExpression build();
  }
}
