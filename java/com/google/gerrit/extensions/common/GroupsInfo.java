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
 * Representation of group-related server configuration in the REST API.
 *
 * <p>This class determines the JSON format of group-related server configuration in the REST API.
 */
public class GroupsInfo {
  /** The value of the {@code groups.enableDeleteGroup} parameter in {@code gerrit.config}. */
  public Boolean enableDeleteGroup;
}
