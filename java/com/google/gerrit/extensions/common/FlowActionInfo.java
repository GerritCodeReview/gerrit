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

import com.google.common.collect.ImmutableMap;

/**
 * Representation of a flow action in the REST API.
 *
 * <p>This class determines the JSON format of flow actions in the REST API.
 *
 * <p>An action to be triggered when the condition of a flow expression becomes satisfied.
 */
public class FlowActionInfo {
  /**
   * The name of the action.
   *
   * <p>Which actions are supported depends on the flow service implementation.
   */
  public String name;

  /**
   * Parameters for the action.
   *
   * <p>Which parameters are supported depends on the flow service implementation.
   */
  public ImmutableMap<String, String> parameters;
}
