// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import java.util.Set;

public interface UpdateUI {

  void message(String message);

  /** Requests the user to answer a yes/no question. */
  boolean yesno(boolean defaultValue, String message);

  /** Prints a message asking the user to let us know when it's safe to continue. */
  void waitForUser();

  /**
   * Prompts the user for a string, suggesting a default.
   *
   * @return the chosen string from the list of allowed values.
   */
  String readString(String defaultValue, Set<String> allowedValues, String message);

  boolean isBatch();
}
