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

/**
 * Representation of a flow action type in the REST API.
 *
 * <p>This class determines the JSON format of flow action types in the REST API.
 *
 * <p>An action type to be triggered when the condition of a flow expression becomes satisfied.
 */
public class FlowActionTypeInfo {
  /**
   * The name of the action type.
   *
   * <p>Which action types are supported depends on the flow service implementation.
   */
  public String name;
}
