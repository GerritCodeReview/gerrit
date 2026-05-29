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

/**
 * A stage in a flow, consisting out of a flow expression that defines an action that should be
 * triggered when a condition becomes satisfied and a status.
 *
 * <p>Stage are only evaluated if all previous stages have been satisfied.
 */
@AutoValue
public abstract class FlowStage {
  /** The expression defining the condition and the action of this stage. */
  public abstract FlowExpression expression();

  /** The evaluation status for this stage. */
  public abstract FlowStageEvaluationStatus status();

  /** Creates a {@link Builder} for this flow stage instance. */
  public abstract Builder toBuilder();

  public static FlowStage.Builder builder() {
    return new AutoValue_FlowStage.Builder().status(FlowStageEvaluationStatus.notEvaledStatus());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the expression defining the condition and the action of this stage. */
    public abstract Builder expression(FlowExpression expression);

    /** Sets the evaluation status for this stage. */
    public abstract Builder status(FlowStageEvaluationStatus status);

    /** Builds the {@link FlowStage}. */
    public abstract FlowStage build();
  }
}
