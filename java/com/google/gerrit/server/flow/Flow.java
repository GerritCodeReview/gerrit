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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import java.time.Instant;
import java.util.Optional;

/**
 * An automation rule on a change that triggers actions on the change when the flow conditions
 * become satisfied. For example, a flow can be an automation rule that adds a reviewer to the
 * change when the change has been verified by the CI.
 *
 * <p>Flows have multiple stages that each have an expression that defines the condition to be met
 * and the action to be triggered. Stage are only evaluated if all previous stages have been
 * satisfied.
 */
@AutoValue
public abstract class Flow {
  /** The key that uniquely identifies this flow. */
  public abstract FlowKey key();

  /** The date and time this flow was created. */
  public abstract Instant createdOn();

  /** The account ID of the user that owns this flow. */
  public abstract Account.Id ownerId();

  /**
   * The stages of this flow (sorted by execution order).
   *
   * <p>Stage are only evaluated if all previous stages have been satisfied.
   */
  public abstract ImmutableList<FlowStage> stages();

  /**
   * The date and time this flow was last evaluated.
   *
   * @return The date and time this flow was last evaluated, {@link Optional#empty()} if the flow
   *     hasn't been evaluated yet.
   */
  public abstract Optional<Instant> lastEvaluatedOn();

  /**
   * Creates a builder for building a flow.
   *
   * @param key The key that uniquely identifies this flow.
   * @return the builder for building the flow
   */
  public static Flow.Builder builder(FlowKey key) {
    return new AutoValue_Flow.Builder().key(key);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the key that uniquely identifies this flow. */
    public abstract Builder key(FlowKey key);

    /** Sets the date and time this flow was created. */
    public abstract Builder createdOn(Instant createdOn);

    /** Set the account ID of the user that owns this flow. */
    public abstract Builder ownerId(Account.Id ownerId);

    /** Sets the stages of this flow (sorted by execution order). */
    public abstract Builder stages(ImmutableList<FlowStage> stages);

    /** Sets date and time this flow was last evaluated. */
    public abstract Builder lastEvaluatedOn(Instant lastEvaluatedOn);

    /** Builds the {@link Flow}. */
    public abstract Flow build();
  }
}
