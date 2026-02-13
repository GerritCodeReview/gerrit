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
 * A custom condition to be evaluated as part of a flow expression.
 *
 * <p>Which custom conditions are supported depends on the flow service implementation.
 */
@AutoValue
public abstract class FlowCustomCondition {
  /**
   * The name of the custom condition.
   *
   * <p>Which custom conditions are supported depends on the flow service implementation.
   */
  public abstract String name();

  /**
   * Optional prefix that should be appended to all created conditions.
   *
   * <p>Which prefix values are supported depends on the flow service implementation.
   */
  public abstract Optional<String> prefix();

  /** Creates a {@link Builder} for this flow custom condition instance. */
  public abstract Builder toBuilder();

  /**
   * Creates a builder for building a flow custom condition.
   *
   * @param name The name of the custom condition.
   * @return the builder for building the flow custom condition
   */
  public static FlowCustomCondition.Builder builder(String name) {
    return new AutoValue_FlowCustomCondition.Builder().name(name).prefix(Optional.empty());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the name of the custom condition. */
    public abstract Builder name(String name);

    /** Sets an optional prefix for the custom condition. */
    public abstract Builder prefix(Optional<String> prefix);

    /** Sets an optional prefix for the custom condition. */
    public final Builder prefix(String prefix) {
      return prefix(Optional.of(prefix));
    }

    /** Builds the {@link FlowCustomCondition}. */
    public abstract FlowCustomCondition build();
  }
}
