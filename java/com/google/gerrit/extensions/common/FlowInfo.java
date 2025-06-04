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

package com.google.gerrit.extensions.common;

import com.google.common.collect.ImmutableList;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Representation of a flow in the REST API.
 *
 * <p>This class determines the JSON format of flows in the REST API.
 *
 * <p>A flow is an automation rule on a change that triggers actions on the change when the flow
 * conditions become satisfied.
 */
public class FlowInfo {
  /** The universally unique identifier that identifies the flow. */
  public String uuid;

  /** The owner of the flow as an {@link AccountInfo} entity. */
  public AccountInfo owner;

  // TODO(issue-40014498): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant

  /** The timestamp of when the flow was created. */
  public Timestamp created;

  @SuppressWarnings("JdkObsolete")
  public void setCreated(Instant when) {
    created = Timestamp.from(when);
  }

  /** The stages of this flow (sorted by execution order). */
  public ImmutableList<FlowStageInfo> stages;

  /**
   * The timestamp of when the flow was last evaluated.
   *
   * <p>Not set if the flow has not been evaluated yet.
   */
  public Timestamp lastEvaluated;

  @SuppressWarnings("JdkObsolete")
  public void setLastEvaluated(Instant when) {
    lastEvaluated = Timestamp.from(when);
  }
}
