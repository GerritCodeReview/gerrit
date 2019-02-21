// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.api;

/**
 * Status of a configured checker.
 *
 * <p>This status is a property of the checker's configuration; not to be confused with {@code
 * CheckState}, which is the state of an individual check performed by a checker against a specific
 * change.
 */
public enum CheckerStatus {
  /** The checker is enabled. */
  ENABLED,

  /**
   * The checker is disabled, meaning its checks are not displayed alongside any changes, and the
   * results are not considered when determining submit requirements.
   */
  DISABLED
}
