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

/** State of a stage in a flow in the REST API. */
public enum FlowStageState {
  /** The condition of the stage is not satisfied yet or the action has not been executed yet. */
  PENDING,

  /** The condition of the stage is satisfied and the action has been executed. */
  DONE,

  /** The stage has a non-recoverable error, e.g. performing the action has failed. */
  FAILED,

  /**
   * The stage has been terminated without having been executed, e.g. because a previous stage
   * failed or because it wasn't done within a timeout.
   */
  TERMINATED;
}
