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

import static com.google.gerrit.entities.LabelFunction.MAX_WITH_BLOCK;

import com.google.gerrit.pgm.init.api.AllProjectsConfig;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import org.eclipse.jgit.lib.Config;

@Singleton
public class InitLabels implements InitStep {
  private static final String KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE = "copyAllScoresIfNoCodeChange";
  private static final String KEY_LABEL = "label";
  private static final String KEY_FUNCTION = "function";
  private static final String KEY_VALUE = "value";
  private static final String LABEL_VERIFIED = "Verified";

  private final ConsoleUI ui;
  private final AllProjectsConfig allProjectsConfig;

  private boolean installVerified;

  @Inject
  InitLabels(ConsoleUI ui, AllProjectsConfig allProjectsConfig) {
    this.ui = ui;
    this.allProjectsConfig = allProjectsConfig;
  }

  @Override
  public void run() throws Exception {
    Config cfg = allProjectsConfig.load().getConfig();
    if (cfg == null || !cfg.getSubsections(KEY_LABEL).contains(LABEL_VERIFIED)) {
      ui.header("Review Labels");
      installVerified = ui.yesno(false, "Install Verified label");
    }
  }

  @Override
  public void postRun() throws Exception {
    Config cfg = allProjectsConfig.load().getConfig();
    if (installVerified) {
      cfg.setString(KEY_LABEL, LABEL_VERIFIED, KEY_FUNCTION, MAX_WITH_BLOCK.getFunctionName());
      cfg.setStringList(
          KEY_LABEL,
          LABEL_VERIFIED,
          KEY_VALUE,
          Arrays.asList("-1 Fails", "0 No score", "+1 Verified"));
      cfg.setBoolean(KEY_LABEL, LABEL_VERIFIED, KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE, true);
      allProjectsConfig.save("Configure 'Verified' label");
    }
  }
}
