// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.index;

/** Listener for online schema upgrade events. */
public interface OnlineUpgradeListener {
  /**
   * Called before starting upgrading a single index.
   *
   * @param name index definition name.
   * @param oldVersion old schema version.
   * @param newVersion new schema version.
   */
  void onStart(String name, int oldVersion, int newVersion);

  /**
   * Called after successfully upgrading a single index.
   *
   * @param name index definition name.
   * @param oldVersion old schema version.
   * @param newVersion new schema version.
   */
  void onSuccess(String name, int oldVersion, int newVersion);

  /**
   * Called after failing to upgrade a single index.
   *
   * @param name index definition name.
   * @param oldVersion old schema version.
   * @param newVersion new schema version.
   */
  void onFailure(String name, int oldVersion, int newVersion);
}
