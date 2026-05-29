// Copyright (C) 2020 The Android Open Source Project
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
 * Fallback rule for choosing a display name, if it is not explicitly set. This rule will not be
 * applied by the backend, but should be applied by the user interface.
 */
public enum AccountDefaultDisplayName {

  /**
   * If the display name for an account is not set, then the (full) name will be used as the display
   * name in the user interface.
   */
  FULL_NAME,

  /**
   * If the display name for an account is not set, then the first name (i.e. full name until first
   * whitespace character) will be used as the display name in the user interface.
   */
  FIRST_NAME,

  /**
   * If the display name for an account is not set, then the username will be used as the display
   * name in the user interface. If the username is also not set, then the (full) name will be used.
   */
  USERNAME
}
