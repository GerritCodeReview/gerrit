// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Adds {@code -XX:+UseCompactObjectHeaders} (JEP 519) to {@code container.javaOptions} on JDK 25,
 * unless the administrator already enabled or disabled it.
 */
@Singleton
public class InitCompactHeadersOptions implements InitStep {
  private static final String CONTAINER = "container";
  private static final String JAVA_OPTIONS = "javaOptions";
  private static final String FLAG = "UseCompactObjectHeaders";
  private static final String USE_COMPACT_OBJECT_HEADERS = "-XX:+" + FLAG;

  private final ConsoleUI ui;
  private final Section container;

  @Inject
  InitCompactHeadersOptions(ConsoleUI ui, Section.Factory sections) {
    this.ui = ui;
    this.container = sections.get(CONTAINER, null);
  }

  @Override
  public void run() throws Exception {
    if (Runtime.version().feature() != 25) {
      return;
    }
    List<String> javaOptions = new ArrayList<>(Arrays.asList(container.getList(JAVA_OPTIONS)));
    if (isSet(javaOptions)) {
      return;
    }
    javaOptions.add(USE_COMPACT_OBJECT_HEADERS);
    container.setList(JAVA_OPTIONS, javaOptions);
    ui.message("Enabled compact object headers (%s).\n", USE_COMPACT_OBJECT_HEADERS);
  }

  private static boolean isSet(List<String> javaOptions) {
    return javaOptions.stream().anyMatch(o -> o.contains(FLAG));
  }
}
