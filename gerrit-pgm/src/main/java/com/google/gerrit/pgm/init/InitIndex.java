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

package com.google.gerrit.pgm.init;

import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Initialize the {@code index} configuration section. */
@Singleton
class InitIndex implements InitStep {
  private final ConsoleUI ui;
  private final Section index;
  private final SitePaths site;

  @Inject
  InitIndex(ConsoleUI ui,
      Section.Factory sections,
      SitePaths site) {
    this.ui = ui;
    this.index = sections.get("index", null);
    this.site = site;
  }

  public void run() {
    ui.header("Index");

    boolean upgrading = !site.isNew && index.get("type") == null;
    index.select("Type", "type", IndexType.LUCENE);
    if (upgrading) {
      ui.message("The index must be rebuilt before starting Gerrit:\n"
        + "  java -jar gerrit.war reindex -d site_path");
    }
  }
}
