// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InitJavaOptions implements InitStep {
  private static final String CONTAINER = "container";
  private static final String JAVA_OPTIONS = "javaOptions";
  private static final String JAVA_MODULES_JAVA_ACTIVATION = "--add-modules java.activation";
  private static final String JAVA_ADD_OPENS_JDK_MANAGEMENT =
      "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED";

  private final Section container;

  @Inject
  public InitJavaOptions(Section.Factory sections) {
    this.container = sections.get(CONTAINER, null);
  }

  @Override
  public void run() throws Exception {
    if (!GerritLauncher.isJdk9OrLater()) {
      return;
    }
    List<String> javaOptions = new ArrayList<>(Arrays.asList(container.getList(JAVA_OPTIONS)));
    if (!isSet(javaOptions, JAVA_MODULES_JAVA_ACTIVATION)) {
      javaOptions.add(JAVA_MODULES_JAVA_ACTIVATION);
    }
    if (!isSet(javaOptions, JAVA_ADD_OPENS_JDK_MANAGEMENT)) {
      javaOptions.add(JAVA_ADD_OPENS_JDK_MANAGEMENT);
    }
    container.setList(JAVA_OPTIONS, javaOptions);
  }

  private static boolean isSet(List<String> javaOptions, String javaOption) {
    return javaOptions.stream().anyMatch(o -> o.equals(javaOption));
  }
}
