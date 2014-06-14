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

package com.google.gerrit.pgm.init;

import com.google.gerrit.lucene.LuceneChangeIndex;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;

/** Initialize the {@code index} configuration section. */
@Singleton
class InitIndex implements InitStep {
  private final ConsoleUI ui;
  private final Section index;
  private final SitePaths site;
  private final InitFlags initFlags;

  @Inject
  InitIndex(ConsoleUI ui,
      Section.Factory sections,
      SitePaths site,
      InitFlags initFlags) {
    this.ui = ui;
    this.index = sections.get("index", null);
    this.site = site;
    this.initFlags = initFlags;
  }

  @Override
  public void run() throws IOException {
    ui.header("Index");

    IndexType type = index.select("Type", "type", IndexType.LUCENE);
    if (site.isNew && type == IndexType.LUCENE) {
      LuceneChangeIndex.setReady(
          site, ChangeSchemas.getLatest().getVersion(), true);
    } else {
      final String message = String.format(
        "\nThe index must be %sbuilt before starting Gerrit:\n"
        + "  java -jar gerrit.war reindex -d site_path\n",
        site.isNew ? "" : "re");
      ui.message(message);
      initFlags.autoStart = false;
    }
  }
}
