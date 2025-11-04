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
 * An action type to be triggered when the condition of a flow expression becomes satisfied.
 *
 * <p>Which action types are supported depends on the flow service implementation.
 */
@AutoValue
public abstract class FlowActionType {
  /**
   * The name of the action type.
   *
   * <p>Which action types are supported depends on the flow service implementation.
   */
  public abstract String name();

  /** Creates a {@link Builder} for this flow action type instance. */
  public abstract Builder toBuilder();

  /**
   * Creates a builder for building a flow action type.
   *
   * @param name The name of the action type.
   * @return the builder for building the flow action type
   */
  public static FlowActionType.Builder builder(String name) {
    return new AutoValue_FlowActionType.Builder().name(name);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the name of the action type. */
    public abstract Builder name(String name);

    /** Builds the {@link FlowActionType}. */
    public abstract FlowActionType build();
  }
}
