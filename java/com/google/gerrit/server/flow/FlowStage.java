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

@AutoValue
public abstract class FlowStage {
  /** Status of a stage in a {@link Flow}; */
  public enum Status {
    /** The condition of the stage is not satisfied yet or the action has not been executed yet. */
    PENDING,

    /** The condition of the stage is satisfied and the action has been executed. */
    DONE;
  }

  public abstract FlowExpression expression();

  public abstract Status status();

  public static FlowStage.Builder builder() {
    return new AutoValue_FlowStage.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder expression(FlowExpression expression);

    public abstract Builder status(Status status);

    /** Builds the {@link FlowStage}. */
    public abstract FlowStage build();
  }
}
