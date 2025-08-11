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
import java.time.Instant;
import java.util.Optional;

/** Data about the evaluation and execution of a single stage in the flow. */
@ConvertibleToProto
@AutoValue
public abstract class FlowStageEvaluationStatus {
  /** State of a stage in a {@link Flow}; */
  public enum State {
    /** Default, unset value. */
    UNKNOWN(0),
    /** The condition of the stage is not satisfied yet or the action has not been executed yet. */
    PENDING(1),
    /** The condition of the stage is satisfied and the action has been executed. */
    DONE(2),
    /** The stage has a non-recoverable error, e.g. performing the action has failed. */
    FAILED(3),
    /**
     * The stage has been terminated without having been executed, e.g. because a previous stage
     * failed or because it wasn't done within a timeout.
     */
    TERMINATED(4);

    State(int v) {
      this.value = v;
    }

    public int getValue() {
      return value;
    }

    private final int value;
  }

  /** The state of this stage. */
  public abstract State state();

  /**
   * Message contains extra information about the stage execution. Such as error details if the
   * stage failed.
   */
  public abstract Optional<String> message();

  /**
   * The timestamp at which all previous stages completed and evaluation of the current this stage
   * started.
   */
  public abstract Optional<Instant> startTime();

  /* The timestamp at which the stage became DONE/FAILED/TERMINATED. */
  public abstract Optional<Instant> endTime();

  /** Creates a {@link Builder} for this instance. */
  public abstract Builder toBuilder();

  public static FlowStageEvaluationStatus.Builder builder() {
    return new AutoValue_FlowStageEvaluationStatus.Builder();
  }

  /** Fresh stage that has never been evaluated. */
  public static FlowStageEvaluationStatus notEvaledStatus() {
    return new AutoValue_FlowStageEvaluationStatus.Builder().state(State.PENDING).build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the state for this stage. */
    public abstract Builder state(State state);

    /** Sets a message for this flow stage, e.g. to inform about execution errors. */
    public abstract Builder message(String message);

    /**
     * Sets the timestamp at which all previous stages completed and evaluation of the current this
     * stage started..
     */
    public abstract Builder startTime(Instant message);

    /** Sets the timestamp at which the stage became DONE/FAILED/TERMINATED. */
    public abstract Builder endTime(Instant message);

    /** Builds the {@link FlowStageEvaluationStatus}. */
    public abstract FlowStageEvaluationStatus build();
  }
}
