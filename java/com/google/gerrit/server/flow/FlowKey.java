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
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;

/** Key that uniquely identifies a {@link Flow}. */
@AutoValue
public abstract class FlowKey {
  /** The name of the project that contains the change for which the flow applies. */
  public abstract Project.NameKey projectName();

  /** The ID of the change for which the flow applies. */
  public abstract Change.Id changeId();

  /** The universally unique identifier that identifies the flow. */
  public abstract String uuid();

  /**
   * Creates a {@link FlowKey}.
   *
   * @param projectName The name of the project that contains the change for which the flow applies.
   * @param changeId The ID of the change for which the flow applies.
   * @param uuid The universally unique identifier that identifies the flow.
   * @return the builder for building the flow key
   */
  public static FlowKey create(Project.NameKey projectName, Change.Id changeId, String uuid) {
    return builder().projectName(projectName).changeId(changeId).uuid(uuid).build();
  }

  /**
   * Creates a builder for building a flow key.
   *
   * @return the builder for building the flow key
   */
  public static FlowKey.Builder builder() {
    return new AutoValue_FlowKey.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Set the name of the project that contains the change for which the flow applies. */
    public abstract Builder projectName(Project.NameKey projectName);

    /** Set the ID of the change for which the flow applies. */
    public abstract Builder changeId(Change.Id changeId);

    /** Set the universally unique identifier that identifies the flow. */
    public abstract Builder uuid(String uuid);

    /** Builds the {@link FlowKey}. */
    public abstract FlowKey build();
  }
}
