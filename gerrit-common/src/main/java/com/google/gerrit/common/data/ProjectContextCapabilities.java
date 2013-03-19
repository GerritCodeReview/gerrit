// Copyright (C) 2013 The Android Open Source Project
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Project specific capabilities that have impact on other projects or the
 * whole collection of projects.
 */
public class ProjectContextCapabilities {
  /** Can instantiate the project template on the server. */
  public static final String INSTANTIATE_TEMPLATE = "instantiateTemplate";

  private static final List<String> NAMES_ALL;
  private static final List<String> NAMES_LC;

  static {
    NAMES_ALL = new ArrayList<String>();
    NAMES_ALL.add(INSTANTIATE_TEMPLATE);

    NAMES_LC = new ArrayList<String>(NAMES_ALL.size());
    for (String name : NAMES_ALL) {
      NAMES_LC.add(name.toLowerCase());
    }
  }

  /** @return all valid capability names. */
  public static Collection<String> getAllNames() {
    return Collections.unmodifiableList(NAMES_ALL);
  }

  /** @return true if the name is recognized as a capability name. */
  public static boolean isCapability(String varName) {
    return NAMES_LC.contains(varName.toLowerCase());
  }

  private ProjectContextCapabilities() {
    // Utility class, do not create instances.
  }
}
