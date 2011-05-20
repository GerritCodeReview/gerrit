// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common.data;

import java.util.ArrayList;
import java.util.List;

/** Server wide capabilities. Represented as {@link Permission} objects. */
public class GlobalCapability {
  public static final String QUERY_LIMIT = "queryLimit";

  private static final List<String> NAMES_LC;

  static {
    NAMES_LC = new ArrayList<String>();
    NAMES_LC.add(QUERY_LIMIT.toLowerCase());
  }

  /** @return true if the name is recognized as a capability name. */
  public static boolean isCapability(String varName) {
    return NAMES_LC.contains(varName.toLowerCase());
  }

  /** @return true if the capability should have a range attached. */
  public static boolean hasRange(String varName) {
    return QUERY_LIMIT.equalsIgnoreCase(varName);
  }

  private GlobalCapability() {
    // Utility class, do not create instances.
  }
}
