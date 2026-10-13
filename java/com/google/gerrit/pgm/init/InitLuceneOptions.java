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

import com.google.gerrit.index.IndexType;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Enables the Vector API for the Lucene index. */
@Singleton
class InitLuceneOptions implements InitStep {
  private static final String VECTOR_MODULE = "jdk.incubator.vector";
  private static final String ADD_VECTOR_MODULE = "--add-modules=" + VECTOR_MODULE;
  private static final Pattern ADD_MODULES =
      Pattern.compile("(?:^|\\s)--add-modules(?:=|\\s+)([^\\s]+)");

  private final ConsoleUI ui;
  private final Section index;
  private final Section container;

  @Inject
  InitLuceneOptions(ConsoleUI ui, Section.Factory sections) {
    this.ui = ui;
    index = sections.get("index", null);
    container = sections.get("container", null);
  }

  @Override
  public void run() {
    if (!new IndexType(index.get("type")).isLucene()) {
      return;
    }
    List<String> options = new ArrayList<>(Arrays.asList(container.getList("javaOptions")));
    for (String option : options) {
      Matcher matcher = ADD_MODULES.matcher(option);
      while (matcher.find()) {
        if (Arrays.asList(matcher.group(1).split(",")).contains(VECTOR_MODULE)) {
          return;
        }
      }
    }
    options.add(ADD_VECTOR_MODULE);
    container.setList("javaOptions", options);
    ui.message("Enabled Vector API for Lucene (%s).\n", ADD_VECTOR_MODULE);
  }
}
