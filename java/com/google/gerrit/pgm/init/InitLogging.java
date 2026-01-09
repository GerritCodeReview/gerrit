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

import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InitLogging implements InitStep {
  private static final String CONTAINER = "container";
  private static final String JAVA_OPTIONS = "javaOptions";
  private static final String FLOGGER_BACKEND_PROPERTY = "flogger.backend_factory";
  private static final String FLOGGER_LOGGING_CONTEXT = "flogger.logging_context";
  private static final String LOG4J1_BACKEND =
      "com.google.common.flogger.backend.log4j.Log4jBackendFactory#getInstance";
  private static final String LOG4J2_BACKEND =
      "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance";

  private final Section container;

  @Inject
  public InitLogging(Section.Factory sections) {
    this.container = sections.get(CONTAINER, null);
  }

  @Override
  public void run() throws Exception {
    List<String> javaOptions = new ArrayList<>(Arrays.asList(container.getList(JAVA_OPTIONS)));

    upsertBackend(javaOptions);

    if (!isSet(javaOptions, FLOGGER_LOGGING_CONTEXT)) {
      javaOptions.add(
          getJavaOption(
              FLOGGER_LOGGING_CONTEXT,
              "com.google.gerrit.server.logging.LoggingContext#getInstance"));
    }
    container.setList(JAVA_OPTIONS, javaOptions);
  }

  private static void upsertBackend(List<String> opts) {
    String key = "-D" + FLOGGER_BACKEND_PROPERTY + "=";

    for (int i = 0; i < opts.size(); i++) {
      String s = opts.get(i).trim();
      if (!s.startsWith(key)) {
        continue;
      }

      String current = s.substring(key.length());
      if (LOG4J1_BACKEND.equals(current)) {
        opts.set(i, getJavaOption(FLOGGER_BACKEND_PROPERTY, LOG4J2_BACKEND));
      }
      return; // present (migrated or custom)
    }

    opts.add(getJavaOption(FLOGGER_BACKEND_PROPERTY, LOG4J2_BACKEND));
  }

  private static boolean isSet(List<String> javaOptions, String javaOptionName) {
    return javaOptions.stream()
        .anyMatch(
            o ->
                o.startsWith("-D" + javaOptionName + "=")
                    || o.startsWith("\"-D" + javaOptionName + "="));
  }

  private static String getJavaOption(String javaOptionName, String value) {
    return String.format("-D%s=%s", javaOptionName, value);
  }
}
