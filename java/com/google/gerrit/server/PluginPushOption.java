// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.server.notedb.ChangeNotes;

/**
 * Push option that can be specified on push.
 *
 * <p>On push the option has to be specified as {@code -o <pluginName>~<name>=<value>}, or if a
 * value is not required as {@code -o <pluginName>~<name>}.
 */
public interface PluginPushOption {
  /** The name of the push option. */
  public String getName();

  /** The description of the push option. */
  public String getDescription();

  /**
   * Allows implementers to control if the option is enabled at the change level
   *
   * @param changeNotes the change for which it should be checked if the option is enabled
   */
  default boolean isOptionEnabled(ChangeNotes changeNotes) {
    return false;
  }
}
