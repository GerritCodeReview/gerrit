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

import com.google.gerrit.common.Nullable;

/**
 * Representation of a flow expression in the REST API.
 *
 * <p>This class determines the JSON format of flow expressions in the REST API.
 *
 * <p>A stage flow expression defines an action that should be triggered when a condition becomes
 * satisfied.
 */
public class FlowExpressionInfo {
  /**
   * The condition which must be satisfied for the action to be triggered.
   *
   * <p>Can contain multiple conditions separated by comma.
   *
   * <p>The syntax of the condition depends on the flow service implementation.
   */
  public String condition;

  /**
   * The action that should be triggered when the condition is satisfied.
   *
   * <p>If null, no action is performed.
   */
  @Nullable public FlowActionInfo action;
}
